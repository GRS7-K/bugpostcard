import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeReport, toHtml, toMarkdown, ReportBuffer } from '../lib/report.js';

const date = '2026-01-01T00:00:00.000Z';
const report = {
  schemaVersion: 1, app: { name: 'Example', version: '1', platform: 'web' },
  createdAt: date, title: 'Broken', description: 'Step 1', context: { locale: 'en' },
  events: [{ time: date, kind: 'action', label: 'Open' }],
};

test('shared envelope round-trips native events and drops unknown fields', () => {
  const native = { ...report, privateData: 'dropped', app: { ...report.app, platform: 'ios' } };
  assert.deepEqual(normalizeReport(native), { ...report, app: native.app, screenshot: null });
  assert.throws(() => normalizeReport({ ...report, schemaVersion: 2 }), /version/i);
  assert.throws(() => normalizeReport({ ...report, events: {} }), /events/i);
});

test('context keys stay data and events have a strict bound', () => {
  const context = JSON.parse('{"__proto__":"unsafe","os":"Android","nested":{"url":"secret"}}');
  const result = normalizeReport({ ...report, context, events: Array(300).fill(report.events[0]) });
  assert.equal(result.events.length, 100);
  assert.deepEqual(result.context, { os: 'Android' });
});

test('offline HTML escapes user content and forbids remote resources', () => {
  const value = { ...report, title: '<img src=x onerror=alert(1)>', description: '</pre><script>alert(1)</script>',
    screenshot: 'https://invalid.example/file.png' };
  const html = toHtml(value);
  assert.match(html, /Content-Security-Policy/);
  assert.match(html, /default-src 'none'/);
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/);
  assert.doesNotMatch(html, /<script|https:\/\/invalid\.example/);
});

test('GitHub Markdown contains no active links from a report', () => {
  const result = toMarkdown({ ...report, description: '~~~\n![tracking](https://invalid.example)' });
  assert.match(result, /    !\[tracking\]/);
  assert.match(result, /    ~~~/);
});

test('PNG data can be attached while other formats and oversized dimensions are rejected', () => {
  const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3m8AAAAASUVORK5CYII=';
  const screenshot = 'data:image/png;base64,' + png;
  assert.equal(normalizeReport({ ...report, screenshot }).screenshot, screenshot);
  assert.match(toHtml({ ...report, screenshot }), /<img alt="Attached screenshot" src="data:image\/png;base64,/);
  assert.equal(normalizeReport({ ...report, screenshot: 'data:image/svg+xml;base64,' + png }).screenshot, null);
  const large = Buffer.from(png, 'base64');
  large.writeUInt32BE(1601, 16);
  assert.equal(normalizeReport({ ...report, screenshot: 'data:image/png;base64,' + large.toString('base64') }).screenshot, null);
});

test('buffer expires, bounds, and returns an independent snapshot', () => {
  let now = Date.parse(date);
  const buffer = new ReportBuffer({ limit: 2, retentionMs: 1000, now: () => now });
  buffer.add('action', 'a');
  now += 100;
  buffer.add('action', 'b');
  buffer.add('error', 'c');
  const entries = buffer.snapshot();
  assert.deepEqual(entries.map(entry => entry.label), ['b', 'c']);
  entries[0].label = 'mutated';
  assert.equal(buffer.snapshot()[0].label, 'b');
  now += 1001;
  assert.deepEqual(buffer.snapshot(), []);
  buffer.add('action', 'fresh');
  buffer.clear();
  assert.deepEqual(buffer.snapshot(), []);
});
