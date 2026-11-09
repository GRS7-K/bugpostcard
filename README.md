# BugPostcard

Turn a failed app session into a local report you can review and share. BugPostcard collects recent, deliberately selected events after the user enables diagnostics. It works without a reporting server or an account.

**[Try the demo](https://grs7-k.github.io/bugpostcard/)** · **[繁體中文](README.zh-TW.md)** · **[Download](https://github.com/GRS7-K/bugpostcard/releases)**

1. Turn on diagnostics and reproduce the issue.
2. Review the steps, app environment and optional screenshot. Remove anything you want to omit.
3. Save the report or copy its text for a GitHub issue. You choose how and where to share it.

| Integration | What you get |
| --- | --- |
| [Web](docs/web.md) | Plain JavaScript recorder and embeddable review dialog. Save offline HTML or JSON, or copy issue text. |
| [Android](native/android/README.md) | Copy-in Java sources with a native review screen and JSON export. |
| [iOS](native/ios/README.md) | Local Swift package for iOS 16+, with a SwiftUI review sheet and JSON export. |

Open native JSON reports in the demo using **Import report JSON**. Files are read on the device. The demo has no upload endpoint.

## Try locally

Download a tagged source archive, unzip it, and run the following in the project folder with Node.js 22 or newer:

```sh
npm run demo
```

Open the localhost address printed in the terminal. The demo simulates a camera permission and an HTTP 503 without using your camera.

## Privacy and scope

- Capture starts off. Web diagnostics keep up to 100 events for five minutes by default, in memory. Native retention limits are documented in each guide.
- Automatic web diagnostics include explicitly labeled clicks, generic runtime errors, connection changes, and optionally HTTP failure status. Native events come from host-app callbacks. Use fixed labels.
- The kit does not automatically collect typed input, form values, full URLs, response bodies, headers, raw error messages, or screenshots. A selected screenshot or typed report can contain personal information. Always review the export before sharing.
- This is an integration for apps you maintain. It does not capture activity from unrelated apps or collect a device-wide crash log.
- Offline HTML is self-contained. JSON follows the [report format](docs/report-format.md). Copied issue text omits the screenshot.

Run `npm test` for the web core tests. See the platform guides for native build and device checks.

[MIT License](LICENSE)
