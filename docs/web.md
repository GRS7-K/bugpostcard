# Web integration

Copy the versioned `lib/` directory into your app's public assets. The modules are framework-independent and have no third-party runtime dependencies. Serve them over HTTPS or localhost with the normal JavaScript MIME type.

```html
<link rel="stylesheet" href="/bugpostcard/editor.css">
<label><input id="diagnostics" type="checkbox"> Record recent diagnostics</label>
<button id="save" data-bugpostcard-action="Save settings">Save</button>
<button id="report">Report a problem</button>
<script type="module" src="/bug-report.js"></script>
```

In `bug-report.js`:

```js
import { createRecorder } from '/bugpostcard/browser.js';
import { createReportDialog } from '/bugpostcard/editor.js';

const recorder = createRecorder({ app: { name: 'Settings', version: '1.2.0' } });
const dialog = createReportDialog();
const diagnostics = document.querySelector('#diagnostics');

diagnostics.checked = false;
diagnostics.addEventListener('change', () => {
  if (diagnostics.checked) recorder.start();
  else recorder.stop();
});
document.querySelector('#report').addEventListener('click', () => {
  dialog.open(recorder.snapshot());
});
```

Keep one recorder per app window. For single-page applications, keep it at the app shell. Use `record('route', 'Notification settings')` for route changes, `record('permission', 'Camera permission denied')` from existing app permission callbacks, and `recordHttp(response.status)` for selected network calls. These methods only record while enabled. Pass static labels, never full routes, request data, customer identifiers, or error payloads. If you pass `captureFetch: true`, the recorder wraps the current window's `fetch` while active and records only status codes for failures and a generic network failure message. It does not observe XHR, service workers, other frames, or native network calls.

`snapshot()` returns detached data. Call `clear()` to clear recent events or `stop()` to disable collection and clear the buffer. On app teardown, call `stop()` and `dialog.destroy()`. Your app is responsible for disposing of any snapshots it keeps.

The supplied dialog needs modern ES modules, `HTMLDialogElement`, Blob downloads, and canvas for screenshot attachments. Clipboard falls back to downloading Markdown when the browser blocks clipboard access. A restrictive CSP needs local modules and CSS plus `img-src blob: data:` for the optional screenshot. Sites enforcing Trusted Types for all HTML sinks can build their own review UI using the recorder and report helpers.

## Validate the integration

1. Open the report before enabling diagnostics and confirm the event list is empty.
2. Enable diagnostics, perform a labeled action, and reproduce a failure. Verify only the expected event and safe metadata appear.
3. Remove an event, select a screenshot, review, and export. Open HTML offline and import JSON again to confirm the removed event stays out.
4. Stop diagnostics and confirm a new report no longer contains the prior session's events.
