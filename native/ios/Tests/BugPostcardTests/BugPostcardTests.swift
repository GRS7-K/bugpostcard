import Foundation
import XCTest
@testable import BugPostcard

final class BugPostcardTests: XCTestCase {
    private func recorder(now: @escaping () -> Date = Date.init) -> BugPostcardRecorder {
        BugPostcardRecorder(app: .init(name: "Example", version: "1.2.3"), now: now)
    }

    func testCaptureStartsOffAndOptOutPurgesEvents() {
        let recorder = recorder()
        recorder.recordBreadcrumb("before_opt_in")
        XCTAssertFalse(recorder.isCaptureEnabled)
        XCTAssertTrue(recorder.makeReport(title: "Bug", description: "Steps").events.isEmpty)

        recorder.setCaptureEnabled(true)
        recorder.recordBreadcrumb("after_opt_in")
        XCTAssertEqual(recorder.makeReport(title: "Bug", description: "Steps").events.map(\.label), ["after_opt_in"])

        recorder.setCaptureEnabled(false)
        recorder.recordBreadcrumb("after_opt_out")
        recorder.setCaptureEnabled(true)
        XCTAssertTrue(recorder.makeReport(title: "Bug", description: "Steps").events.isEmpty)
    }

    func testCapacityKeepsNewestThirtyEvents() {
        let recorder = recorder()
        recorder.setCaptureEnabled(true)
        recorder.recordBreadcrumb("evict_me")
        for _ in 0..<30 { recorder.recordBreadcrumb("keep_me") }

        let events = recorder.makeReport(title: "Bug", description: "Steps").events
        XCTAssertEqual(events.count, 30)
        XCTAssertTrue(events.allSatisfy { $0.label == "keep_me" })
    }

    func testReportOmitsEventsOlderThanFiveMinutes() {
        var current = Date(timeIntervalSince1970: 0)
        let recorder = recorder(now: { current })
        recorder.setCaptureEnabled(true)
        recorder.recordBreadcrumb("stale")
        current = current.addingTimeInterval(1)
        recorder.recordBreadcrumb("fresh")
        current = current.addingTimeInterval(300)

        XCTAssertEqual(recorder.makeReport(title: "Bug", description: "Steps").events.map(\.label), ["fresh"])
    }

    func testOnlySafeIdentifiersAndValidHTTPStatusesAreCaptured() {
        let recorder = recorder()
        recorder.setCaptureEnabled(true)
        recorder.recordBreadcrumb("https://example.invalid/private")
        recorder.recordBreadcrumb("alice@example.invalid")
        recorder.recordHTTPStatus(0)
        recorder.recordHTTPStatus(600)
        recorder.recordHTTPStatus(503)
        recorder.recordPermission("camera", state: .denied)
        recorder.recordAppError("save_failed")

        let events = recorder.makeReport(title: "Bug", description: "Steps").events
        XCTAssertEqual(events.map(\.kind), ["http", "permission", "error"])
        XCTAssertEqual(events.map(\.label), ["HTTP 503", "camera: denied", "save_failed"])
    }

    func testPreviewCanExcludeOneEventAndExportSharedSchema() throws {
        let recorder = recorder(now: { Date(timeIntervalSince1970: 0) })
        recorder.setCaptureEnabled(true)
        recorder.recordBreadcrumb("remove_this")
        recorder.recordBreadcrumb("keep_this")
        var report = recorder.makeReport(title: "畫面空白", description: "1. 開啟範例")
        report.removeEvent(id: try XCTUnwrap(report.events.first).id)

        let decoded = try JSONSerialization.jsonObject(with: report.jsonData())
        let json = try XCTUnwrap(decoded as? [String: Any])
        XCTAssertEqual(json["schemaVersion"] as? Int, 1)
        XCTAssertEqual(json["app"] as? [String: String], ["name": "Example", "version": "1.2.3", "platform": "ios"])
        XCTAssertEqual(json["createdAt"] as? String, "1970-01-01T00:00:00Z")
        XCTAssertEqual(json["title"] as? String, "畫面空白")
        XCTAssertEqual(json["description"] as? String, "1. 開啟範例")
        XCTAssertEqual(json["context"] as? [String: String], [:])
        XCTAssertEqual(json["events"] as? [[String: String]], [[
            "time": "1970-01-01T00:00:00Z", "kind": "breadcrumb", "label": "keep_this"
        ]])
        XCTAssertNil(json["screenshot"])
    }

    func testClearingEventsKeepsCaptureEnabled() {
        let recorder = recorder()
        recorder.setCaptureEnabled(true)
        recorder.recordAppError("old_error")
        recorder.clearEvents()
        recorder.recordAppError("new_error")

        XCTAssertTrue(recorder.isCaptureEnabled)
        XCTAssertEqual(recorder.makeReport(title: "Bug", description: "Steps").events.map(\.label), ["new_error"])
    }

    func testUserSelectedScreenshotCanBeRemovedBeforeExport() throws {
        let png = try XCTUnwrap(Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGPgUbIAAACkAGeY0OCYAAAAAElFTkSuQmCC"))
        var report = recorder().makeReport(title: "Bug", description: "Steps")
        XCTAssertNil(report.screenshot)
        XCTAssertTrue(report.attachScreenshot(pngData: png))
        XCTAssertEqual(report.screenshot, "data:image/png;base64," + png.base64EncodedString())

        report.removeScreenshot()
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: report.jsonData()) as? [String: Any])
        XCTAssertNil(json["screenshot"])
    }

    func testInvalidOrOversizedScreenshotCannotBeAttached() {
        var report = recorder().makeReport(title: "Bug", description: "Steps")
        XCTAssertFalse(report.attachScreenshot(pngData: Data("not_png".utf8)))
        var oversized = Data([137, 80, 78, 71, 13, 10, 26, 10])
        oversized.append(Data(repeating: 0, count: 3_000_000))
        XCTAssertFalse(report.attachScreenshot(pngData: oversized))
        XCTAssertNil(report.screenshot)
    }

    func testExportBoundsReportTextToSharedSchemaLimits() {
        let recorder = BugPostcardRecorder(app: .init(
            name: String(repeating: "A", count: 121),
            version: String(repeating: "9", count: 81)
        ))
        let report = recorder.makeReport(
            title: String(repeating: "🙂", count: 81),
            description: String(repeating: "步", count: 8_001)
        )

        XCTAssertEqual(report.app.name.utf16.count, 120)
        XCTAssertEqual(report.app.version.utf16.count, 80)
        XCTAssertEqual(report.title.utf16.count, 160)
        XCTAssertEqual(report.description.utf16.count, 8_000)
    }
}
