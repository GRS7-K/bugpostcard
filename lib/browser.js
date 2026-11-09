import { normalizeReport, ReportBuffer } from './report.js';

export function createRecorder({
  window: host = globalThis.window, app = {}, captureFetch = false, limit, retentionMs,
} = {}) {
  if (!host?.document) throw new TypeError('BugPostcard needs a browser window.');
  const buffer = new ReportBuffer({ limit, retentionMs });
  let generation = 0;
  let enabled = false;
  let restoreFetch = () => {};

  function record(kind, label) {
    return enabled && buffer.add(kind, label);
  }

  function click(event) {
    const action = event.target?.closest?.('[data-bugpostcard-action]');
    if (action) record('action', action.getAttribute('data-bugpostcard-action'));
  }

  const onError = () => record('error', 'Unhandled browser error');
  const onRejection = () => record('error', 'Unhandled promise rejection');
  const onOnline = () => record('network', 'Browser reports online');
  const onOffline = () => record('network', 'Browser reports offline');
  const listeners = [
    [host.document, 'click', click, true],
    [host, 'error', onError, false],
    [host, 'unhandledrejection', onRejection, false],
    [host, 'online', onOnline, false],
    [host, 'offline', onOffline, false],
  ];

  function start() {
    if (enabled) return;
    enabled = true;
    const current = ++generation;
    for (const [target, type, listener, capture] of listeners) target.addEventListener(type, listener, capture);
    if (!captureFetch || typeof host.fetch !== 'function') return;

    const previous = host.fetch;
    function watchedFetch(...args) {
      return Reflect.apply(previous, host, args).then(response => {
        if (generation === current && response.status >= 400) record('http', 'Fetch returned HTTP ' + response.status);
        return response;
      }, error => {
        if (generation === current && error?.name !== 'AbortError') record('http', 'Fetch failed before receiving a response');
        throw error;
      });
    }
    try {
      host.fetch = watchedFetch;
      restoreFetch = () => {
        if (host.fetch === watchedFetch) {
          try { host.fetch = previous; } catch { /* A host may lock the property after installation. */ }
        }
      };
    } catch { /* Manual recordHttp is available when fetch cannot be wrapped. */ }
  }

  function stop() {
    enabled = false;
    generation++;
    for (const [target, type, listener, capture] of listeners) target.removeEventListener(type, listener, capture);
    restoreFetch();
    restoreFetch = () => {};
    buffer.clear();
  }

  return {
    get enabled() { return enabled; },
    start,
    stop,
    record,
    clear: () => buffer.clear(),
    recordHttp(status) {
      return Number.isInteger(status) && status >= 100 && status <= 599 && record('http', 'HTTP ' + status);
    },
    snapshot() {
      return normalizeReport({
        schemaVersion: 1,
        app: { ...app, platform: 'web' },
        createdAt: new Date().toISOString(),
        title: '',
        description: '',
        context: enabled ? {
          locale: host.navigator?.language,
          viewport: host.innerWidth + ' × ' + host.innerHeight,
          connectivity: host.navigator?.onLine ? 'online' : 'offline',
        } : {},
        events: buffer.snapshot(),
      });
    },
  };
}
