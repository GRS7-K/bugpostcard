const MAX_EVENTS = 100;
export const MAX_SCREENSHOT_LENGTH = 4_000_000;
export const MAX_IMPORT_BYTES = 5_000_000;

export function boundedText(value, limit) {
  return typeof value === 'string'
    ? value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u202A-\u202E\u2066-\u2069]/g, '')
      .replace(/\r\n?/g, '\n').slice(0, limit).trim()
    : '';
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function iso(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T/.test(value)) return '';
  const time = new Date(value);
  return Number.isFinite(time.getTime()) ? time.toISOString() : '';
}

function png(value) {
  const prefix = 'data:image/png;base64,';
  if (typeof value !== 'string' || value.length > MAX_SCREENSHOT_LENGTH || !value.startsWith(prefix)) return null;
  const encoded = value.slice(prefix.length);
  if (encoded.length < 32 || encoded.length % 4 || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)) return null;
  const head = atob(encoded.slice(0, 32));
  if (head.slice(0, 16) !== '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR') return null;
  const dimension = offset => [...head.slice(offset, offset + 4)].reduce((size, char) => size * 256 + char.charCodeAt(0), 0);
  const width = dimension(16);
  const height = dimension(20);
  return width > 0 && height > 0 && width <= 1600 && height <= 1600 ? value : null;
}

export function normalizeReport(value) {
  if (!isRecord(value) || value.schemaVersion !== 1) throw new Error('Unsupported report version.');
  if (!isRecord(value.app)) throw new Error('Invalid report app.');
  if (!Array.isArray(value.events)) throw new Error('Invalid report events.');
  const createdAt = iso(value.createdAt);
  if (!createdAt) throw new Error('Invalid report date.');
  const context = Object.fromEntries(
    Object.entries(isRecord(value.context) ? value.context : {}).slice(0, 30)
      .filter(([key, item]) => /^[\w .-]{1,64}$/.test(key) &&
        !['__proto__', 'constructor', 'prototype'].includes(key) && typeof item === 'string')
      .map(([key, item]) => [key, boundedText(item, 240)]),
  );
  return {
    schemaVersion: 1,
    app: {
      name: boundedText(value.app.name, 120),
      version: boundedText(value.app.version, 80),
      platform: ['web', 'android', 'ios', 'other'].includes(value.app.platform) ? value.app.platform : 'other',
    },
    createdAt,
    title: boundedText(value.title, 160),
    description: boundedText(value.description, 8000),
    context,
    events: value.events.slice(-MAX_EVENTS).flatMap(item => {
      if (!isRecord(item)) return [];
      const time = iso(item.time);
      const kind = boundedText(item.kind, 32);
      const label = boundedText(item.label, 240);
      return time && kind && label ? [{ time, kind, label }] : [];
    }),
    screenshot: png(value.screenshot),
  };
}

export class ReportBuffer {
  #events = [];

  constructor({ limit = MAX_EVENTS, retentionMs = 300_000, now = Date.now } = {}) {
    this.limit = Number.isFinite(limit) ? Math.max(1, Math.min(MAX_EVENTS, Math.floor(limit))) : MAX_EVENTS;
    this.retentionMs = Number.isFinite(retentionMs) ? Math.max(1000, Math.min(900_000, retentionMs)) : 300_000;
    this.now = now;
  }

  add(kind, label) {
    const safeKind = boundedText(kind, 32);
    const safeLabel = boundedText(label, 240);
    if (!safeKind || !safeLabel) return false;
    const now = this.now();
    this.#prune(now);
    this.#events.push({ at: now, kind: safeKind, label: safeLabel });
    if (this.#events.length > this.limit) this.#events.shift();
    return true;
  }

  snapshot() {
    this.#prune(this.now());
    return this.#events.map(({ at, kind, label }) => ({ time: new Date(at).toISOString(), kind, label }));
  }

  clear() {
    this.#events = [];
  }

  #prune(now) {
    this.#events = this.#events.filter(entry => entry.at >= now - this.retentionMs && entry.at <= now);
  }
}

const escapeHtml = value => String(value).replace(/[&<>"']/g, char => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
})[char]);

function sections(report) {
  const context = Object.entries(report.context).map(([key, value]) => key + ': ' + value);
  return [
    ['Description', report.description || 'No description provided.'],
    ['Environment', [
      ['App', report.app.name, report.app.version].filter(Boolean).join(' · '),
      'Platform: ' + report.app.platform,
      'Created (UTC): ' + report.createdAt,
      ...context,
    ].join('\n')],
    ['Timeline (UTC)', report.events.map(({ time, kind, label }) => time + ' · ' + kind + ' · ' + label).join('\n') || 'No events included.'],
  ];
}

export function toMarkdown(value) {
  const report = normalizeReport(value);
  const blocks = [['Title', report.title || 'Bug report'], ...sections(report)];
  const body = blocks.map(([heading, content]) =>
    '### ' + heading + '\n\n' + content.split('\n').map(line => '    ' + line).join('\n'));
  if (report.screenshot) body.push('Screenshot available in the local HTML or JSON export.');
  return body.join('\n\n') + '\n';
}

export function toHtml(value) {
  const report = normalizeReport(value);
  const heading = escapeHtml(report.title || 'Bug report');
  const body = sections(report).map(([name, content]) =>
    '<section><h2>' + name + '</h2><pre>' + escapeHtml(content) + '</pre></section>').join('');
  const image = report.screenshot ? '<section><h2>Screenshot</h2><img alt="Attached screenshot" src="' + report.screenshot + '"></section>' : '';
  return '<!doctype html>\n<html lang="en"><head><meta charset="utf-8">' +
    '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; img-src data:; style-src \'unsafe-inline\'; base-uri \'none\'; form-action \'none\'">' +
    '<meta name="viewport" content="width=device-width,initial-scale=1"><title>' + heading + ' · BugPostcard</title>' +
    '<style>body{margin:0;background:#f4f6f3;color:#18342d;font:1rem/1.55 system-ui}main{max-width:52rem;margin:auto;padding:2rem 1rem 4rem}header{border-bottom:3px solid #27735f}h1{line-height:1.15;overflow-wrap:anywhere}section{background:white;padding:1rem 1.25rem;border-radius:1rem;margin-top:1rem}h2{font-size:1rem;margin:0 0 .5rem}pre{white-space:pre-wrap;overflow-wrap:anywhere;margin:0;font:inherit}img{display:block;max-width:100%;height:auto}small{color:#466359}</style>' +
    '</head><body><main><header><small>BUGPOSTCARD · OFFLINE REPORT</small><h1>' + heading + '</h1></header>' +
    body + image + '</main></body></html>';
}
