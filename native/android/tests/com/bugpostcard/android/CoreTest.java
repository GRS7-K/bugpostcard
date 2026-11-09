package com.bugpostcard.android;

import java.util.Collections;

public final class CoreTest {
    private static final String PNG = "data:image/png;base64,"
        + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a03sAAAAASUVORK5CYII=";

    private enum Code {
        OPEN_SETTINGS, REFRESH, CAMERA, SAVE_FAILED;

        @Override
        public String toString() {
            return "https://example.test/private";
        }
    }

    private static final class Clock implements BugPostcard.TimeSource {
        long elapsed;
        long wall;

        public long elapsedMillis() { return elapsed; }
        public long wallMillis() { return wall; }
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--emit-fixture")) {
            System.out.println(attachmentFixture());
            return;
        }
        consentStartsOffAndWithdrawalInvalidatesSnapshots();
        preservesOnlyTheNewestHundredEvents();
        expiresEventsByElapsedTimeDespiteWallClockChanges();
        expiresEventsAcrossDeviceSleep();
        recordsOnlyExplicitCodeNamesAndSafeDetails();
        encodesTheSelectedDataInTheSharedEnvelope();
        System.out.println("Android core: 6 tests passed");
    }

    private static void consentStartsOffAndWithdrawalInvalidatesSnapshots() {
        BugPostcard.Recorder recorder = new BugPostcard.Recorder(new Clock());
        recorder.breadcrumb(Code.OPEN_SETTINGS);
        check(recorder.capture().events.isEmpty(), "Collection is off by default");
        recorder.setEnabled(true);
        recorder.breadcrumb(Code.OPEN_SETTINGS);
        BugPostcard.Capture first = recorder.capture();
        check(first.events.size() == 1, "Only collect after explicit opt-in");
        check(recorder.isCurrent(first), "Fresh report can export");
        recorder.clear();
        check(!recorder.isCurrent(first), "Clear invalidates earlier report data");
        recorder.breadcrumb(Code.OPEN_SETTINGS);
        BugPostcard.Capture second = recorder.capture();
        recorder.setEnabled(false);
        recorder.setEnabled(true);
        check(!recorder.isCurrent(second), "Withdrawal invalidates earlier report data");
        check(recorder.capture().events.isEmpty(), "Opt-in starts again with an empty buffer");
    }

    private static void preservesOnlyTheNewestHundredEvents() {
        BugPostcard.Recorder recorder = new BugPostcard.Recorder(new Clock());
        recorder.setEnabled(true);
        for (int status = 400; status <= 500; status++) {
            recorder.httpStatus(Code.REFRESH, status);
        }
        BugPostcard.Capture report = recorder.capture();
        check(report.events.size() == 100, "Keep at most 100 events");
        check(report.events.get(0).label.equals("REFRESH HTTP 401"), "Evict oldest first");
        check(report.events.get(99).label.equals("REFRESH HTTP 500"), "Keep the latest event");
    }

    private static void expiresEventsByElapsedTimeDespiteWallClockChanges() {
        Clock clock = new Clock();
        BugPostcard.Recorder recorder = new BugPostcard.Recorder(clock);
        recorder.setEnabled(true);
        recorder.breadcrumb(Code.OPEN_SETTINGS);
        clock.elapsed = 900_001;
        clock.wall = -3_600_000;
        check(recorder.capture().events.isEmpty(), "Evict events older than 15 minutes");
    }

    private static void expiresEventsAcrossDeviceSleep() {
        Clock clock = new Clock();
        BugPostcard.Recorder recorder = new BugPostcard.Recorder(clock);
        recorder.setEnabled(true);
        recorder.breadcrumb(Code.OPEN_SETTINGS);
        clock.wall = 900_001;
        check(recorder.capture().events.isEmpty(), "Evict events after 15 minutes of device sleep");
    }

    private static void recordsOnlyExplicitCodeNamesAndSafeDetails() {
        BugPostcard.Recorder recorder = new BugPostcard.Recorder(new Clock());
        recorder.setEnabled(true);
        recorder.breadcrumb(null);
        recorder.httpStatus(Code.REFRESH, 0);
        recorder.httpStatus(Code.REFRESH, 600);
        recorder.breadcrumb(Code.OPEN_SETTINGS);
        recorder.httpStatus(Code.REFRESH, 503);
        recorder.permissionResult(Code.CAMERA, false);
        recorder.appError(Code.SAVE_FAILED);
        BugPostcard.Capture report = recorder.capture();
        check(report.events.size() == 4, "Invalid or incomplete inputs never enter the report");
        check(report.events.get(0).label.equals("OPEN_SETTINGS"), "Use the enum's stable name");
        check(report.events.get(1).label.equals("REFRESH HTTP 503"), "Keep operation and status");
        check(report.events.get(2).label.equals("CAMERA denied"), "Keep permission and outcome");
        check(report.events.get(3).label.equals("SAVE_FAILED"), "Keep explicit error code only");
    }

    private static void encodesTheSelectedDataInTheSharedEnvelope() {
        BugPostcard.Recorder recorder = new BugPostcard.Recorder(new Clock());
        recorder.setEnabled(true);
        recorder.breadcrumb(Code.OPEN_SETTINGS);
        recorder.httpStatus(Code.REFRESH, 503);
        String exported = ReportJson.encode("Test \"App\"", "1.2", 0, "開啟",
            "first\nsecond\\last", null, Collections.singletonList(recorder.capture().events.get(1)),
            null);
        String expected = "{\"schemaVersion\":1,\"app\":{\"name\":\"Test \\\"App\\\"\","
            + "\"version\":\"1.2\",\"platform\":\"android\"},\"createdAt\":\"1970-01-01T00:00:00.000Z\","
            + "\"title\":\"開啟\",\"description\":\"first\\nsecond\\\\last\",\"context\":{},"
            + "\"events\":[{\"time\":\"1970-01-01T00:00:00.000Z\",\"kind\":\"http\","
            + "\"label\":\"REFRESH HTTP 503\"}],\"screenshot\":null}";
        check(expected.equals(exported), "Only the checked event is serialized: " + exported);

        String attached = attachmentFixture();
        check(attached.contains("\"context\":{\"androidApiLevel\":\"35\"}"), "API level is optional");
        check(attached.endsWith("\"screenshot\":\"" + PNG + "\"}"),
            "Selected screenshot is represented as a PNG data URL");
    }

    private static String attachmentFixture() {
        BugPostcard.Recorder recorder = new BugPostcard.Recorder(new Clock());
        recorder.setEnabled(true);
        recorder.httpStatus(Code.REFRESH, 503);
        return ReportJson.encode("Android Test App", "1.2", 0, "Unable to refresh", "", "35",
            recorder.capture().events, PNG);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
