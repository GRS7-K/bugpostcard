# BugPostcard for iOS

BugPostcard is a Swift package for iOS 16 or later. It provides an in-memory event recorder and a SwiftUI report screen. It uses Apple frameworks and has no third-party dependencies.

## Add the package

In Xcode, choose **File → Add Package Dependencies → Add Local**, select `native/ios`, and add the `BugPostcard` library to your app target. Keep one recorder for the part of the app you want to instrument.

```swift
import BugPostcard
import Foundation
import SwiftUI

struct ExampleView: View {
    @State private var showReport = false
    @State private var recorder = BugPostcardRecorder(app: .init(
        name: "Example App",
        version: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "Unknown"
    ))

    var body: some View {
        VStack {
            Button("Try example action") {
                recorder.recordBreadcrumb("example_button_tapped")
            }
            Button("Report an issue") { showReport = true }
        }
        .sheet(isPresented: $showReport) {
            BugPostcardView(recorder: recorder)
        }
    }
}
```

From your existing callbacks, add events at the points you want to diagnose.

```swift
if let http = response as? HTTPURLResponse {
    recorder.recordHTTPStatus(http.statusCode)
}
recorder.recordPermission("camera", state: .denied)
recorder.recordAppError("export_failed")
```

Use fixed identifiers that describe app actions, such as `checkout_opened`. Labels accept 1–64 ASCII letters, digits, underscores, and hyphens. Keep personal information and credentials out of identifiers, app metadata, descriptions, and screenshots. HTTP events carry only the status code. Permission and error events carry the explicit identifier or state supplied by your app.

## Review and export

1. Open the report screen and enable **Record recent app events**. Close the sheet, then reproduce the issue.
2. Reopen the screen, add a title and steps, and choose **Review report**. Remove individual events if needed.
3. Optionally choose a screenshot through the system photo picker. The report shows the selected image and lets you remove it. The package re-encodes a resized PNG for export without copying the original photo metadata.
4. Choose **Export JSON to Files** and select a location. Import the saved `.json` file in the BugPostcard web editor. Sharing the file afterward is a separate action.

Recording starts off and resets when the app process ends. With consent, the recorder keeps up to 30 recent events, and previews exclude entries older than five minutes. **Clear recorded events** clears the in-memory buffer. Turning recording off also clears it. A report preview is a snapshot. Close or discard the preview to drop it, and use Files to delete copies you already exported.

The recorder works independently of SwiftUI if you need a custom review screen. Only call `setCaptureEnabled(true)` after obtaining explicit consent. Create the snapshot with `makeReport(title:description:)`, show its contents, let the user remove any events via `removeEvent(id:)`, and call `jsonData()` when the user chooses to export. Your app owns the lifetime of any snapshots it creates. The package performs no telemetry, automatic uploads, global crash capture, or network interception.

## Verify on macOS

- In `native/ios`, run `swift test` to check the recorder and JSON output on macOS with the Swift toolchain. This suite covers capture consent, eviction, expiry, safe fields, clearing, and export shape.
- Add the package to an iOS app and build in Xcode to compile the SwiftUI screen. On an iOS 16+ simulator or device, use the sample flow above. Try capture both off and on, remove an event, select and remove a screenshot, save to Files, and open the exported file in the web editor.
- The Windows development environment did not have Swift or Xcode. Native build, simulator, and device checks are pending.
