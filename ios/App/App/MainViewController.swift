import UIKit
import Capacitor

// Quiet's main screen: Capacitor's web view, with Quiet's own plugin registered
// (QuietNativePlugin.swift). Main.storyboard uses this class.
class MainViewController: CAPBridgeViewController {
    override open func capacitorDidLoad() {
        bridge?.registerPluginInstance(QuietNativePlugin())
    }
}
