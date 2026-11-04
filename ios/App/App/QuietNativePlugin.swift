import Foundation
import Capacitor
import UIKit

// Quiet's own iPhone features, used from src/app/native.ts (the Android side is
// android/app/src/main/java/app/quietreader/QuietNativePlugin.java):
// vibration, keeping the screen on, the battery, the safety copy of the library, sharing and
// saving quote pictures, and saving a backup where the reader chooses.
// Book folders, volume keys and rotation lock are Android only; here they answer "unavailable"
// (the web side hides them on iPhone).
@objc(QuietNativePlugin)
public class QuietNativePlugin: CAPPlugin, CAPBridgedPlugin, UIDocumentPickerDelegate {
    public let identifier = "QuietNativePlugin"
    public let jsName = "QuietNative"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "haptic", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "keepAwake", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "battery", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setOrientation", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setVolumeKeys", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "release", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "safetyWrite", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "safetyCommit", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "safetyInfo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "safetyList", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "safetyDelete", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "shareImage", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "saveImage", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "createDocument", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "writeDocument", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "finishDocument", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pickFolder", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "scanFolder", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "copyFile", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "forgetFolder", returnType: CAPPluginReturnPromise),
    ]

    // ---------- Small things ----------

    @objc func haptic(_ call: CAPPluginCall) {
        let strong = call.getString("kind") == "confirm"
        DispatchQueue.main.async {
            let generator = UIImpactFeedbackGenerator(style: strong ? .medium : .light)
            generator.impactOccurred()
        }
        call.resolve()
    }

    @objc func keepAwake(_ call: CAPPluginCall) {
        let enabled = call.getBool("enabled") ?? false
        DispatchQueue.main.async { UIApplication.shared.isIdleTimerDisabled = enabled }
        call.resolve()
    }

    @objc func battery(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            UIDevice.current.isBatteryMonitoringEnabled = true
            let level = UIDevice.current.batteryLevel
            let state = UIDevice.current.batteryState
            call.resolve(["level": level < 0 ? -1 : Int((level * 100).rounded()), "charging": state == .charging || state == .full])
        }
    }

    @objc func setOrientation(_ call: CAPPluginCall) { call.resolve() }
    @objc func setVolumeKeys(_ call: CAPPluginCall) { call.resolve() }

    @objc func release(_ call: CAPPluginCall) {
        if let path = call.getString("path") { try? FileManager.default.removeItem(atPath: path) }
        call.resolve()
    }

    // ---------- Safety copy (src/library/safety.ts) ----------
    // Files in Application Support/safety, written in chunks to "<name>.tmp" and then swapped in.

    private func safetyDir() throws -> URL {
        let base = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let dir = base.appendingPathComponent("safety", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func safetyFile(_ name: String?) throws -> URL {
        guard let name = name, !name.isEmpty, !name.contains(".."), !name.hasPrefix("/") else {
            throw NSError(domain: "Quiet", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bad name"])
        }
        return try safetyDir().appendingPathComponent(name)
    }

    private func append(_ data: Data, to url: URL, fresh: Bool) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        if fresh || !FileManager.default.fileExists(atPath: url.path) {
            try data.write(to: url)
        } else {
            let handle = try FileHandle(forWritingTo: url)
            defer { try? handle.close() }
            try handle.seekToEnd()
            try handle.write(contentsOf: data)
        }
    }

    @objc func safetyWrite(_ call: CAPPluginCall) {
        let name = call.getString("name")
        let data = Data(base64Encoded: call.getString("data") ?? "", options: .ignoreUnknownCharacters) ?? Data()
        let appendChunk = call.getBool("append") ?? false
        DispatchQueue.global(qos: .utility).async {
            do {
                try self.append(data, to: try self.safetyFile((name ?? "") + ".tmp"), fresh: !appendChunk)
                call.resolve()
            } catch {
                call.reject("Couldn't write \(name ?? ""): \(error.localizedDescription)")
            }
        }
    }

    @objc func safetyCommit(_ call: CAPPluginCall) {
        do {
            let file = try safetyFile(call.getString("name"))
            let tmp = try safetyFile((call.getString("name") ?? "") + ".tmp")
            guard FileManager.default.fileExists(atPath: tmp.path) else { return call.reject("Nothing written") }
            if FileManager.default.fileExists(atPath: file.path) { try FileManager.default.removeItem(at: file) }
            try FileManager.default.moveItem(at: tmp, to: file)
            call.resolve()
        } catch {
            call.reject("Couldn't save: \(error.localizedDescription)")
        }
    }

    @objc func safetyInfo(_ call: CAPPluginCall) {
        do {
            let file = try safetyFile(call.getString("name"))
            let attributes = try? FileManager.default.attributesOfItem(atPath: file.path)
            let exists = attributes != nil
            call.resolve(["exists": exists, "path": file.path, "size": (attributes?[.size] as? NSNumber)?.intValue ?? 0])
        } catch {
            call.reject(error.localizedDescription)
        }
    }

    @objc func safetyList(_ call: CAPPluginCall) {
        var names: [String] = []
        if let dir = try? safetyFile(call.getString("dir") ?? "books"),
           let items = try? FileManager.default.contentsOfDirectory(atPath: dir.path) {
            names = items.filter { !$0.hasSuffix(".tmp") }
        }
        call.resolve(["names": names])
    }

    @objc func safetyDelete(_ call: CAPPluginCall) {
        if let file = try? safetyFile(call.getString("name")) { try? FileManager.default.removeItem(at: file) }
        call.resolve()
    }

    // ---------- Quote pictures ----------

    private func present(_ controller: UIViewController) {
        guard let host = bridge?.viewController else { return }
        if let pop = controller.popoverPresentationController {
            pop.sourceView = host.view
            pop.sourceRect = CGRect(x: host.view.bounds.midX, y: host.view.bounds.maxY - 80, width: 1, height: 1)
        }
        host.present(controller, animated: true)
    }

    @objc func shareImage(_ call: CAPPluginCall) {
        guard let data = Data(base64Encoded: call.getString("data") ?? "") else { return call.reject("No picture") }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(call.getString("name") ?? "quote.png")
        do { try data.write(to: url) } catch { return call.reject("Couldn't share the picture") }
        var items: [Any] = [url]
        if let text = call.getString("text") { items.append(text) }
        DispatchQueue.main.async {
            self.present(UIActivityViewController(activityItems: items, applicationActivities: nil))
            call.resolve()
        }
    }

    @objc func saveImage(_ call: CAPPluginCall) {
        guard let data = Data(base64Encoded: call.getString("data") ?? ""), let image = UIImage(data: data) else { return call.reject("No picture") }
        DispatchQueue.main.async {
            UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
            call.resolve()
        }
    }

    // ---------- Saving a backup where the reader chooses ----------
    // createDocument makes a file in the app's temporary folder, writeDocument fills it in chunks,
    // and finishDocument shows iOS's "save to Files" sheet. Resolves { saved }.

    private var exportCall: CAPPluginCall?

    @objc func createDocument(_ call: CAPPluginCall) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("exports", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let url = dir.appendingPathComponent(call.getString("name") ?? "Quiet backup")
        try? FileManager.default.removeItem(at: url)
        call.resolve(["uri": url.absoluteString])
    }

    @objc func writeDocument(_ call: CAPPluginCall) {
        guard let value = call.getString("uri"), let url = URL(string: value), url.isFileURL else { return call.reject("No file") }
        let data = Data(base64Encoded: call.getString("data") ?? "", options: .ignoreUnknownCharacters) ?? Data()
        let appendChunk = call.getBool("append") ?? false
        DispatchQueue.global(qos: .utility).async {
            do {
                try self.append(data, to: url, fresh: !appendChunk)
                call.resolve()
            } catch {
                call.reject("Couldn't write the file: \(error.localizedDescription)")
            }
        }
    }

    @objc func finishDocument(_ call: CAPPluginCall) {
        guard let value = call.getString("uri"), let url = URL(string: value), url.isFileURL else { return call.reject("No file") }
        DispatchQueue.main.async {
            self.exportCall = call
            let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
            picker.delegate = self
            self.present(picker)
        }
    }

    public func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        exportCall?.resolve(["saved": true])
        exportCall = nil
    }

    public func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        exportCall?.resolve(["saved": false])
        exportCall = nil
    }

    // ---------- Android only ----------

    @objc func pickFolder(_ call: CAPPluginCall) { call.unavailable("Book folders are Android only") }
    @objc func scanFolder(_ call: CAPPluginCall) { call.unavailable("Book folders are Android only") }
    @objc func copyFile(_ call: CAPPluginCall) { call.unavailable("Book folders are Android only") }
    @objc func forgetFolder(_ call: CAPPluginCall) { call.resolve() }
}
