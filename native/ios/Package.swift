// swift-tools-version: 5.7
import PackageDescription

let package = Package(
    name: "BugPostcard",
    platforms: [.iOS(.v16), .macOS(.v12)],
    products: [.library(name: "BugPostcard", targets: ["BugPostcard"])],
    targets: [
        .target(name: "BugPostcard"),
        .testTarget(name: "BugPostcardTests", dependencies: ["BugPostcard"])
    ]
)
