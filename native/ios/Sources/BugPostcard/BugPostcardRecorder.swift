import Foundation

public enum BugPostcardPermissionState: String, Sendable {
    case granted, denied, restricted, limited, undetermined
}

/// A process-local buffer. Record only developer-defined operation labels.
public final class BugPostcardRecorder: @unchecked Sendable {
    public let app: BugPostcardApp

    private let now: () -> Date
    private let lock = NSLock()
    private var captureEnabled = false
    private var events: [BugPostcardEvent] = []

    public convenience init(app: BugPostcardApp) {
        self.init(app: app, now: Date.init)
    }

    init(app: BugPostcardApp, now: @escaping () -> Date) {
        self.app = app
        self.now = now
    }

    public var isCaptureEnabled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return captureEnabled
    }

    public func setCaptureEnabled(_ enabled: Bool) {
        lock.lock()
        defer { lock.unlock() }
        captureEnabled = enabled
        if !enabled { events.removeAll() }
    }

    public func clearEvents() {
        lock.lock()
        defer { lock.unlock() }
        events.removeAll()
    }

    public func recordBreadcrumb(_ label: StaticString) {
        guard let label = safeLabel(label) else { return }
        record(kind: "breadcrumb", label: label)
    }

    public func recordHTTPStatus(_ status: Int) {
        guard (100...599).contains(status) else { return }
        record(kind: "http", label: "HTTP \(status)")
    }

    public func recordPermission(_ permission: StaticString, state: BugPostcardPermissionState) {
        guard let permission = safeLabel(permission) else { return }
        record(kind: "permission", label: "\(permission): \(state.rawValue)")
    }

    public func recordAppError(_ code: StaticString) {
        guard let code = safeLabel(code) else { return }
        record(kind: "error", label: code)
    }

    public func makeReport(title: String, description: String) -> BugPostcardReport {
        lock.lock()
        defer { lock.unlock() }
        let time = now()
        removeExpiredEvents(at: time)
        return BugPostcardReport(app: app, createdAt: time, title: title, description: description, events: events)
    }

    private func record(kind: String, label: String) {
        lock.lock()
        defer { lock.unlock() }
        guard captureEnabled else { return }
        let time = now()
        removeExpiredEvents(at: time)
        if events.count == 30 { events.removeFirst() }
        events.append(BugPostcardEvent(time: time, kind: kind, label: label))
    }

    private func removeExpiredEvents(at time: Date) {
        let cutoff = time.addingTimeInterval(-300)
        events.removeAll { $0.time < cutoff }
    }

    private func safeLabel(_ value: StaticString) -> String? {
        let label = value.description
        let bytes = label.utf8
        guard (1...64).contains(bytes.count), bytes.allSatisfy({
            (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 45 || $0 == 95
        }) else { return nil }
        return label
    }
}
