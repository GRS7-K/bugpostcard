import { createRecorder } from '../lib/browser.js';
import { createReportDialog, readReportFile } from '../lib/editor.js';

const recorder = createRecorder({ app: { name: 'Camera settings demo', version: '1.0.0' } });
const editor = createReportDialog();
const capture = document.querySelector('#capture');
const indicator = document.querySelector('#recorder-state');
const permission = document.querySelector('#permission');
const save = document.querySelector('#save');
const scenario = document.querySelector('#scenario-status');
const input = document.querySelector('#import-report');
const importStatus = document.querySelector('#import-status');
const reviewReport = document.querySelector('#report');

capture.checked = false;
capture.addEventListener('change', () => {
  if (capture.checked) {
    recorder.start();
    indicator.textContent = 'Capture on';
  } else {
    recorder.stop();
    indicator.textContent = 'Capture off';
  }
  indicator.classList.toggle('active', capture.checked);
});
permission.addEventListener('click', () => {
  save.disabled = false;
  recorder.record('permission', 'Camera permission: allowed (simulated)');
  scenario.textContent = 'Camera access is enabled.';
});
save.addEventListener('click', () => {
  recorder.recordHttp(503);
  scenario.textContent = 'Settings could not be saved. The service returned HTTP 503 (simulated).';
});
reviewReport.addEventListener('click', () => editor.open(recorder.snapshot()));
input.addEventListener('change', async () => {
  const file = input.files[0];
  if (!file) return;
  input.disabled = true;
  reviewReport.disabled = true;
  importStatus.textContent = 'Reading local report…';
  try {
    editor.open(await readReportFile(file));
    importStatus.textContent = '';
  } catch (error) {
    importStatus.textContent = error.message;
  } finally {
    input.disabled = false;
    reviewReport.disabled = false;
    input.value = '';
  }
});
