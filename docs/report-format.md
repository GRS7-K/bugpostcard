# BugPostcard report format v1

UTF-8 JSON. The same envelope is used by the web and native collectors.

```json
{
  "schemaVersion": 1,
  "app": { "name": "Example", "version": "1.2.0", "platform": "android" },
  "createdAt": "2026-01-01T00:00:00.000Z",
  "title": "Save settings failed",
  "description": "Open settings and tap Save.",
  "context": { "os": "Android" },
  "events": [
    { "time": "2026-01-01T00:00:00.000Z", "kind": "http", "label": "HTTP 503" }
  ],
  "screenshot": null
}
```

Use ISO 8601 timestamps with a timezone. Platform is `web`, `android`, `ios` or `other`. The editor accepts up to the last 100 events and 30 string context values. Maximum lengths are 160 characters for title, 8,000 for description, and 240 for event labels and context values. A screenshot is optional, either null or a PNG data URL with dimensions at most 1600 × 1600 and up to 4,000,000 characters. Import files must be 5 MB or smaller. Unknown fields are discarded. Import rejects unsupported schema versions and invalid top-level metadata. Invalid individual events and unsupported images are skipped.

Treat every report as untrusted data. Render its strings as text. Do not upload it or embed external resources in its viewer. BugPostcard's HTML export escapes text and restricts resource loading.
