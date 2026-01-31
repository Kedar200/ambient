import SwiftUI
import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    var shieldWindow: NSWindow?
    var proximityManager = ProximityManager()
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        setupShieldWindow()
    }
    
    func setupShieldWindow() {
        guard let screen = NSScreen.main else { return }
        
        // Create a window that covers the entire screen
        let window = NSWindow(
            contentRect: screen.frame,
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        
        window.level = .screenSaver
        window.isOpaque = false
        window.backgroundColor = NSColor.black.withAlphaComponent(0.0)
        window.ignoresMouseEvents = true // Start by letting clicks through!
        
        // Hardening: Prevent moving and ensure it covers all spaces
        window.isMovable = false
        window.isMovableByWindowBackground = false
        window.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]
        
        // Connect manager to window
        proximityManager.window = window
        
        let contentView = ShieldView(proximity: proximityManager)
        window.contentView = NSHostingView(rootView: contentView)
        
        window.makeKeyAndOrderFront(nil)
        self.shieldWindow = window
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
