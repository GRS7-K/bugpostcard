import test from 'node:test';
import assert from 'node:assert/strict';
import { createRecorder } from '../lib/browser.js';

function environment() {
  const window = new EventTarget();
  window.document = new EventTarget();
  window.fetch = async () => ({ status: 503 });
  window.navigator = { language: 'en', onLine: true };
  window.innerWidth = 800;
  window.innerHeight = 600;
  return window;
}

test('explicit opt-in, bounded diagnostics, and teardown', async () => {
  const window = environment();
  const fetch = window.fetch;
  const recorder = createRecorder({ window, app: { name: 'Demo', version: '1' }, captureFetch: true });
  assert.equal(recorder.record('action', 'Before consent'), false);
  assert.equal(window.fetch, fetch);
  recorder.start();
  recorder.start();
  const event = new Event('error');
  Object.defineProperty(event, 'message', { value: 'secret text' });
  window.dispatchEvent(event);
  const response = await window.fetch('https://site.example/path?private=1', { body: 'secret' });
  assert.equal(response.status, 503);
  recorder.record('action', 'Allowed label');
  const report = recorder.snapshot();
  assert.deepEqual(report.events.map(entry => entry.label), ['Unhandled browser error', 'Fetch returned HTTP 503', 'Allowed label']);
  assert.doesNotMatch(JSON.stringify(report), /secret|site\.example|private=1/);
  recorder.stop();
  assert.equal(window.fetch, fetch);
  assert.deepEqual(recorder.snapshot().events, []);
  window.dispatchEvent(event);
  assert.deepEqual(recorder.snapshot().events, []);
});

test('manual HTTPS events only accept valid HTTP codes', () => {
  const recorder = createRecorder({ window: environment(), app: { name: 'Demo' } });
  recorder.start();
  assert.equal(recorder.recordHttp(999), false);
  assert.equal(recorder.recordHttp('401'), false);
  assert.equal(recorder.recordHttp(401), true);
  assert.equal(recorder.snapshot().events[0].label, 'HTTP 401');
});
