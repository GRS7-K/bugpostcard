import { MAX_IMPORT_BYTES, MAX_SCREENSHOT_LENGTH, normalizeReport, toHtml, toMarkdown } from './report.js';

const MAX_IMAGE_BYTES = 12_000_000;

const template = `
  <form method="dialog" class="bp-shell">
    <header class="bp-head"><div><span class="bp-eyebrow">BUGPOSTCARD / LOCAL REPORT</span>
      <h2>Review your report</h2><p>Remove anything you don't want to share. This tool sends nothing.</p></div>
      <button type="submit" value="close" formnovalidate class="bp-quiet" aria-label="Close report">Close</button></header>
    <div class="bp-content">
      <div class="bp-fields">
        <label>Title<input name="title" maxlength="160" required placeholder="What went wrong?"></label>
        <label>What happened?<textarea name="description" maxlength="8000" rows="4"
          placeholder="What did you expect? What happened instead?"></textarea></label>
      </div>
      <section aria-label="Evidence">
        <div class="bp-row bp-section-head"><h3>Included evidence</h3>
          <button type="button" data-action="clear" class="bp-quiet">Remove all evidence</button></div>
        <p class="bp-summary"></p>
        <ul class="bp-context"></ul><ol class="bp-events"></ol>
      </section>
      <section aria-label="Screenshot">
        <div class="bp-row bp-section-head"><h3>Screenshot <small>optional</small></h3>
          <button type="button" data-action="remove-image" class="bp-quiet" hidden>Remove image</button></div>
        <label class="bp-file">Choose PNG, JPEG or WebP<input name="image" type="file" accept="image/png,image/jpeg,image/webp"></label>
        <img class="bp-image" alt="Screenshot included in the report" hidden>
      </section>
      <label class="bp-consent"><input name="reviewed" type="checkbox"> I reviewed what this report contains.</label>
      <p class="bp-status" role="status" aria-live="polite"></p>
    </div>
    <footer class="bp-actions">
      <button type="button" data-action="html" class="bp-primary">Save offline HTML</button>
      <button type="button" data-action="json">Save JSON</button>
      <button type="button" data-action="copy">Copy issue text</button>
      <button type="button" data-action="markdown">Save Markdown</button>
    </footer>
  </form>
`;

async function screenshotData(blob) {
  if (blob.size > MAX_IMAGE_BYTES || !['image/png', 'image/jpeg', 'image/webp'].includes(blob.type)) {
    throw new Error('Choose a PNG, JPEG or WebP image under 12 MB.');
  }
  const url = URL.createObjectURL(blob);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    if (!image.naturalWidth || !image.naturalHeight) throw new Error('Could not read that image.');
    const canvas = document.createElement('canvas');
    for (const size of [1600, 1200, 900, 600]) {
      const ratio = Math.min(1, size / Math.max(image.naturalWidth, image.naturalHeight));
      canvas.width = Math.max(1, Math.round(image.naturalWidth * ratio));
      canvas.height = Math.max(1, Math.round(image.naturalHeight * ratio));
      const context = canvas.getContext('2d');
      if (!context) throw new Error('Image conversion is unavailable.');
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      const data = canvas.toDataURL('image/png');
      if (data.length <= MAX_SCREENSHOT_LENGTH && data.startsWith('data:image/png;base64,')) return data;
    }
    throw new Error('That screenshot is too large.');
  } finally {
    URL.revokeObjectURL(url);
  }
}

export async function readReportFile(file) {
  if (file.size > MAX_IMPORT_BYTES) throw new Error('Report is larger than 5 MB.');
  let raw;
  try { raw = JSON.parse(await file.text()); } catch { throw new Error('This file is not valid report JSON.'); }
  const report = normalizeReport(raw);
  if (report.screenshot) {
    try {
      const encoded = report.screenshot.split(',')[1];
      const bytes = Uint8Array.from(atob(encoded), char => char.charCodeAt(0));
      report.screenshot = await screenshotData(new Blob([bytes], { type: 'image/png' }));
    } catch {
      report.screenshot = null;
    }
  }
  return report;
}

function download(text, type, extension) {
  const link = document.createElement('a');
  const url = URL.createObjectURL(new Blob([text], { type }));
  link.href = url;
  link.download = 'bugpostcard-' + new Date().toISOString().slice(0, 10) + '.' + extension;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

export function createReportDialog() {
  const dialog = document.createElement('dialog');
  dialog.className = 'bp-dialog';
  const id = 'bp-title-' + Math.random().toString(36).slice(2);
  dialog.setAttribute('aria-labelledby', id);
  dialog.innerHTML = template;
  dialog.querySelector('h2').id = id;
  document.body.append(dialog);

  const form = dialog.querySelector('form');
  const title = form.elements.namedItem('title');
  const description = form.elements.namedItem('description');
  const reviewed = form.elements.namedItem('reviewed');
  const imageInput = form.elements.namedItem('image');
  const image = dialog.querySelector('.bp-image');
  const status = dialog.querySelector('.bp-status');
  const action = name => dialog.querySelector('[data-action="' + name + '"]');
  const exports = ['html', 'json', 'copy', 'markdown'].map(action);
  let report;
  let imageGeneration = 0;
  let revision = 0;
  let imageBusy = false;
  let destroyed = false;

  function enableExports() {
    reviewed.disabled = imageBusy;
    for (const button of exports) button.disabled = !reviewed.checked || !title.value.trim() || imageBusy;
  }

  function changed() {
    revision++;
    reviewed.checked = false;
    status.textContent = '';
    enableExports();
  }

  function listRow(parent, label, detail, remove) {
    const row = document.createElement('li');
    const body = document.createElement('span');
    body.textContent = label;
    if (detail) {
      const info = document.createElement('small');
      info.textContent = detail;
      body.append(info);
    }
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'bp-quiet';
    button.textContent = 'Remove';
    button.setAttribute('aria-label', 'Remove ' + label);
    button.addEventListener('click', () => {
      remove();
      render();
      changed();
      const next = parent.querySelector('button') ?? action('clear');
      (next.disabled ? title : next).focus();
    });
    row.append(body, button);
    parent.append(row);
  }

  function render() {
    const context = dialog.querySelector('.bp-context');
    const events = dialog.querySelector('.bp-events');
    context.replaceChildren();
    events.replaceChildren();
    Object.entries(report.context).forEach(([key, value]) => listRow(context, key, value, () => delete report.context[key]));
    report.events.forEach(({ time, kind, label }, index) => listRow(events, label, kind + ' · ' + time, () => report.events.splice(index, 1)));
    const count = report.events.length + Object.keys(report.context).length;
    const app = [report.app.name, report.app.version, report.app.platform].filter(Boolean).join(' · ');
    dialog.querySelector('.bp-summary').textContent = app + ' — ' + count + ' diagnostic items';
    action('clear').disabled = !count && !report.screenshot;
    action('remove-image').hidden = !report.screenshot;
    image.hidden = !report.screenshot;
    if (report.screenshot) image.src = report.screenshot;
    else image.removeAttribute('src');
  }

  function clearImage() {
    imageGeneration++;
    imageBusy = false;
    imageInput.value = '';
    report.screenshot = null;
  }

  action('clear').addEventListener('click', () => {
    report.context = {};
    report.events = [];
    clearImage();
    render();
    changed();
    title.focus();
  });
  action('remove-image').addEventListener('click', () => {
    clearImage();
    render();
    changed();
    imageInput.focus();
  });
  title.addEventListener('input', changed);
  description.addEventListener('input', changed);
  reviewed.addEventListener('change', enableExports);
  imageInput.addEventListener('change', async () => {
    const file = imageInput.files[0];
    if (!file || !report) return;
    const current = ++imageGeneration;
    imageBusy = true;
    changed();
    status.textContent = 'Preparing screenshot…';
    try {
      const data = await screenshotData(file);
      if (current !== imageGeneration || !dialog.open) return;
      report.screenshot = data;
      render();
      changed();
      status.textContent = 'Screenshot attached. Check it before sharing.';
    } catch (error) {
      if (current === imageGeneration && dialog.open) status.textContent = error.message;
    } finally {
      if (current === imageGeneration) {
        imageBusy = false;
        imageInput.value = '';
        enableExports();
      }
    }
  });

  const approvedReport = () => normalizeReport({ ...report, title: title.value, description: description.value });
  action('html').addEventListener('click', () => {
    download(toHtml(approvedReport()), 'text/html;charset=utf-8', 'html');
    status.textContent = 'HTML export prepared.';
  });
  action('json').addEventListener('click', () => {
    download(JSON.stringify(approvedReport(), null, 2), 'application/json', 'json');
    status.textContent = 'JSON export prepared.';
  });
  action('markdown').addEventListener('click', () => {
    download(toMarkdown(approvedReport()), 'text/markdown;charset=utf-8', 'md');
    status.textContent = 'Markdown export prepared.';
  });
  action('copy').addEventListener('click', async () => {
    const text = toMarkdown(approvedReport());
    const current = revision;
    try {
      await navigator.clipboard.writeText(text);
      if (dialog.open && current === revision) status.textContent = 'GitHub Issue text copied.';
    } catch {
      if (!dialog.open || current !== revision || !reviewed.checked) return;
      download(text, 'text/markdown;charset=utf-8', 'md');
      status.textContent = 'Clipboard unavailable. Markdown export prepared.';
    }
  });

  function reset() {
    imageGeneration++;
    revision++;
    imageBusy = false;
    report = undefined;
    form.reset();
    dialog.querySelector('.bp-context').replaceChildren();
    dialog.querySelector('.bp-events').replaceChildren();
    dialog.querySelector('.bp-summary').textContent = '';
    image.removeAttribute('src');
    status.textContent = '';
  }

  // A fast reopen can happen before a queued close event is delivered.
  dialog.addEventListener('close', () => { if (!dialog.open) reset(); });

  return {
    open(value) {
      if (destroyed) throw new Error('This report dialog has been destroyed.');
      reset();
      report = normalizeReport(value);
      title.value = report.title;
      description.value = report.description;
      render();
      changed();
      if (!dialog.open) dialog.showModal();
      title.focus();
    },
    destroy() {
      if (dialog.open) dialog.close();
      reset();
      dialog.remove();
      destroyed = true;
    },
  };
}
