import Foundation

public struct BugPostcardApp: Encodable, Sendable {
    public let name: String
    public let version: String
    public let platform = "ios"

    public init(name: String, version: String) {
        self.name = String(decoding: name.utf16.prefix(120), as: UTF16.self)
        self.version = String(decoding: version.utf16.prefix(80), as: UTF16.self)
    }
}

public struct BugPostcardEvent: Encodable, Identifiable, Sendable {
    public let id = UUID()
    public let time: Date
    public let kind: String
    public let label: String

    private enum CodingKeys: String, CodingKey {
        case time, kind, label
    }
}

public struct BugPostcardReport: Encodable, Sendable {
    static let titleLimit = 160
    static let descriptionLimit = 8_000
    // The base64 data URL must fit the shared four-million-character limit.
    static let screenshotByteLimit = 2_999_982

    public let schemaVersion = 1
    public let app: BugPostcardApp
    public let createdAt: Date
    public let title: String
    public let description: String
    public let context: [String: String] = [:]
    public private(set) var events: [BugPostcardEvent]
    public private(set) var screenshot: String?

    init(app: BugPostcardApp, createdAt: Date, title: String, description: String, events: [BugPostcardEvent]) {
        self.app = app
        self.createdAt = createdAt
        self.title = String(decoding: title.utf16.prefix(Self.titleLimit), as: UTF16.self)
        self.description = String(decoding: description.utf16.prefix(Self.descriptionLimit), as: UTF16.self)
        self.events = events
    }

    public mutating func removeEvent(id: BugPostcardEvent.ID) {
        events.removeAll { $0.id == id }
    }

    public mutating func removeScreenshot() {
        screenshot = nil
    }

    @discardableResult
    mutating func attachScreenshot(pngData: Data) -> Bool {
        let signature: [UInt8] = [137, 80, 78, 71, 13, 10, 26, 10]
        guard pngData.count <= Self.screenshotByteLimit, pngData.starts(with: signature) else { return false }
        screenshot = "data:image/png;base64," + pngData.base64EncodedString()
        return true
    }

    public func jsonData() throws -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(self)
    }
}
