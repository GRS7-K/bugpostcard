#if os(iOS)
import Foundation
import ImageIO
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

@MainActor
public struct BugPostcardView: View {
    private let recorder: BugPostcardRecorder
    @Environment(\.dismiss) private var dismiss

    @State private var captureEnabled: Bool
    @State private var title = ""
    @State private var description = ""
    @State private var report: BugPostcardReport?
    @State private var photo: PhotosPickerItem?
    @State private var screenshotPreview: Data?
    @State private var loadingPhoto = false
    @State private var document: ReportDocument?
    @State private var exporting = false
    @State private var notice: String?

    public init(recorder: BugPostcardRecorder) {
        self.recorder = recorder
        _captureEnabled = State(initialValue: recorder.isCaptureEnabled)
    }

    public var body: some View {
        NavigationStack {
            Form {
                if let report {
                    review(report)
                } else {
                    compose
                }

                if let notice {
                    Section { Text(notice) }
                }
            }
            .navigationTitle("BugPostcard")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .fileExporter(isPresented: $exporting, document: document, contentType: .json, defaultFilename: "bugpostcard") { result in
                document = nil
                switch result {
                case .success: notice = "Report exported."
                case .failure: notice = "The report could not be saved."
                }
            }
        }
    }

    private var compose: some View {
        Group {
            Section {
                Toggle("Record recent app events", isOn: Binding(
                    get: { captureEnabled },
                    set: { enabled in
                        recorder.setCaptureEnabled(enabled)
                        captureEnabled = enabled
                    }
                ))
                .onAppear { captureEnabled = recorder.isCaptureEnabled }
                Button("Clear recorded events", role: .destructive) {
                    recorder.clearEvents()
                    notice = "Recorded events cleared."
                }
                .disabled(!captureEnabled)
            } footer: {
                Text("Only events this app explicitly records are kept, up to 30 from the past five minutes. Turning recording off clears the buffer.")
            }

            Section("Describe the issue") {
                TextField("Title", text: $title)
                TextField("Steps and what happened", text: $description, axis: .vertical)
                    .lineLimit(4...10)
            }

            Section {
                Button("Review report") {
                    report = recorder.makeReport(title: title.trimmingCharacters(in: .whitespacesAndNewlines), description: description)
                    notice = nil
                }
                .disabled(textTooLong || title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            } footer: {
                if textTooLong {
                    Text("Shorten the title to 160 or fewer characters and the description to 8,000 or fewer.")
                } else {
                    Text("You can remove events and check the report before saving it to Files.")
                }
            }
        }
    }

    private var textTooLong: Bool {
        title.utf16.count > BugPostcardReport.titleLimit || description.utf16.count > BugPostcardReport.descriptionLimit
    }

    private func review(_ draft: BugPostcardReport) -> some View {
        Group {
            Section("Report") {
                Text(draft.title).font(.headline)
                if !draft.description.isEmpty { Text(draft.description) }
                LabeledContent("App", value: draft.app.name)
                LabeledContent("Version", value: draft.app.version)
                LabeledContent("Platform", value: "iOS")
                LabeledContent("Created", value: draft.createdAt.formatted())
            }

            Section("Recorded events") {
                if draft.events.isEmpty { Text("No events included.") }
                ForEach(draft.events) { event in
                    VStack(alignment: .leading, spacing: 5) {
                        Text(event.label)
                        Text("\(event.kind) · \(event.time.formatted())")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Button("Remove event", role: .destructive) {
                            report?.removeEvent(id: event.id)
                            document = nil
                        }
                            .buttonStyle(.borderless)
                            .accessibilityLabel("Remove event \(event.label)")
                    }
                }
            }

            Section {
                if let screenshotPreview, let image = UIImage(data: screenshotPreview) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 280)
                        .accessibilityLabel("Selected screenshot")
                    Button("Remove screenshot", role: .destructive) { clearScreenshot() }
                }
                PhotosPicker(selection: $photo, matching: .images) {
                    Text(screenshotPreview == nil ? "Choose screenshot" : "Replace screenshot")
                }
                if loadingPhoto { ProgressView("Preparing screenshot") }
            } header: {
                Text("Optional screenshot")
            } footer: {
                Text("Check the image for personal information before including it.")
            }

            Section {
                Button("Export JSON to Files") { export(draft) }
                    .disabled(loadingPhoto)
                Button("Discard preview") {
                    clearScreenshot()
                    report = nil
                    notice = nil
                }
            }
        }
        .task(id: photo) { await loadSelectedPhoto(photo) }
    }

    private func clearScreenshot() {
        photo = nil
        screenshotPreview = nil
        loadingPhoto = false
        report?.removeScreenshot()
        document = nil
    }

    private func loadSelectedPhoto(_ selection: PhotosPickerItem?) async {
        guard let selection else { return }
        loadingPhoto = true
        notice = nil
        defer {
            if photo == selection { loadingPhoto = false }
        }

        let original = try? await selection.loadTransferable(type: Data.self)
        guard !Task.isCancelled else { return }
        let png = await Task.detached(priority: .userInitiated) {
            original.flatMap(Self.normalizedPNG)
        }.value

        guard !Task.isCancelled, photo == selection, var draft = report else { return }
        guard let png, draft.attachScreenshot(pngData: png) else {
            notice = "This image could not be included. The previous selection is unchanged."
            return
        }
        report = draft
        screenshotPreview = png
        document = nil
    }

    private nonisolated static func normalizedPNG(_ input: Data) -> Data? {
        guard input.count <= 20_000_000,
              let source = CGImageSourceCreateWithData(input as CFData, nil) else { return nil }

        for size in [1600, 1000] {
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: size
            ]
            let output = NSMutableData()
            guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary),
                  image.width <= size, image.height <= size,
                  let destination = CGImageDestinationCreateWithData(output as CFMutableData, UTType.png.identifier as CFString, 1, nil) else { return nil }
            CGImageDestinationAddImage(destination, image, nil)
            if CGImageDestinationFinalize(destination), output.length <= BugPostcardReport.screenshotByteLimit {
                return output as Data
            }
        }
        return nil
    }

    private func export(_ draft: BugPostcardReport) {
        do {
            document = ReportDocument(data: try draft.jsonData())
            notice = nil
            exporting = true
        } catch {
            notice = "The report could not be prepared."
        }
    }
}

private struct ReportDocument: FileDocument {
    static let readableContentTypes: [UTType] = [.json]
    let data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw CocoaError(.fileReadCorruptFile) }
        self.data = data
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
#endif
