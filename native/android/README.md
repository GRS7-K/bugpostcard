# BugPostcard for Android

Add a report screen to an Android app so users can describe a problem, review recent events, and save a JSON report through Android's document picker. BugPostcard runs in the app process and requires only the Android framework.

## Install

1. Copy the `com/bugpostcard/android` directory from [src/main/java](src/main/java/) into your app's Java source directory.
2. Copy the `<activity>` declaration from the [sample manifest](src/main/AndroidManifest.xml) into your app manifest's existing `<application>` element. Keep `android:exported="false"`.
3. Use Android API 21 or newer at runtime and compile against API 28 or newer. Compile the Java source with Java 8 language support or newer.

The copied files require no Android permissions. Your app can localize the English review screen by moving its visible labels into its string resources.

## Add consent and a report button

Start with diagnostics disabled and add a visible opt-in in your app. A setting might say “Keep recent diagnostic events on this device for bug reports.” The SDK starts each process with an empty, disabled event buffer. To remember a user's preference across launches, store the setting in your app and restore it on startup.

```java
import com.bugpostcard.android.BugPostcard;
import com.bugpostcard.android.BugPostcardReportActivity;

// In your Activity. diagnosticsOptIn and reportIssue are your existing views.
diagnosticsOptIn.setChecked(BugPostcard.isCollectionEnabled());
diagnosticsOptIn.setOnCheckedChangeListener((button, checked) ->
    BugPostcard.setCollectionEnabled(checked));
reportIssue.setOnClickListener(view -> BugPostcardReportActivity.open(this));
```

The report screen works with diagnostics disabled. Turning diagnostics off clears recent events and invalidates event selections in open reports. You can also call `BugPostcard.clearEvents()` while keeping the current opt-in setting.

## Record useful events

Define a small set of static, non-sensitive enum constants in your app. BugPostcard stores their names and the explicit values shown below. Record the events where they happen, such as when a request finishes or the permission result arrives.

```java
enum DiagnosticCode {
    OPEN_SETTINGS, REFRESH_CATALOG, CAMERA, SAVE_FAILED
}

BugPostcard.breadcrumb(DiagnosticCode.OPEN_SETTINGS);
BugPostcard.httpStatus(DiagnosticCode.REFRESH_CATALOG, 503);
BugPostcard.permissionResult(DiagnosticCode.CAMERA, false);
BugPostcard.appError(DiagnosticCode.SAVE_FAILED);
```

HTTP status codes use the range 100–599. Event codes use up to 64 ASCII letters, digits, and underscores, starting with a letter. The SDK records only these explicit calls. It keeps at most 100 events from the preceding 15 minutes in memory and freezes that list when the report screen opens. The buffer is per process.

## Review and save

The screen shows the app name, app version, report time, and the included event timestamps and labels. The user can uncheck the Android API level and individual events, and can optionally choose a PNG with Android's document picker. The screenshot preview includes a removal button. Input PNGs may be up to 16 MiB and 48 megapixels, with each side at most 16,384 pixels. The SDK decodes and re-encodes the pixels as a PNG at most 1600 pixels on either side and leaves out source metadata such as textual PNG chunks. If the resulting attachment exceeds the report limit, the user can choose a smaller PNG. Visible text in the image remains part of the image.

**Save JSON** opens Android's picker for the user to choose the destination. The SDK writes one JSON file to the selected destination. A cloud-backed folder is available if the device's document picker offers it and the user chooses it. To inspect, edit, or export other formats, open the BugPostcard web editor and import the resulting JSON.

The report draft stays in memory across screen rotation. Closing the screen or restarting the app process discards it. If the destination provider fails during writing, it may leave an incomplete file at the selected destination.

## Test

From this directory, run the pure Java test with JDK 11 or newer.

```sh
mkdir -p build
javac --release 8 -encoding UTF-8 -d build \
  src/main/java/com/bugpostcard/android/BugPostcard.java \
  src/main/java/com/bugpostcard/android/ReportJson.java \
  tests/com/bugpostcard/android/CoreTest.java
java -cp build com.bugpostcard.android.CoreTest
```

In an Android host app, check these interactions on an emulator or device.

1. Record an event with consent off, open a report, and check that the event is absent.
2. Opt in, trigger a few diagnostic events, and reopen the report. Uncheck an event and the API level.
3. Select a PNG, rotate the device, and verify the preview and edits survive. Remove the image, then select it again.
4. Save JSON to a test folder and import it into the web editor. Verify the checked events and PNG are present. Repeat while cancelling the save picker.
5. Turn diagnostics off and reopen the report. Verify there are no recent events.
