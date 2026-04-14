import SwiftUI
import AppKit
import UniformTypeIdentifiers

import Combine

// Content view wrapper that observes ProximityManager and animates transition
struct OrbContentView: View {
    @ObservedObject var proximity: ProximityManager
    @Namespace var animation
    
    var body: some View {
        ZStack {
            if !proximity.isUnlocked {
                // Shield Mode (Background Layer)
                ShieldView(proximity: proximity)
                    .transition(.opacity)
            } else {
                // Orb Mode
                OrbTestView(batteryPercentage: $proximity.phoneBatteryLevel, isConnected: proximity.isConnected)
                    .matchedGeometryEffect(id: "Container", in: animation)
                    .frame(width: 300, height: 300)
                    .transition(.scale(scale: 0.1, anchor: .center))
            }
        }
        .animation(.spring(response: 0.6, dampingFraction: 0.8), value: proximity.isUnlocked)
    }
}

// Demo Delegate for Transparent Window
class OrbTestDelegate: NSObject, NSApplicationDelegate {
    var window: NSWindow?
    var proximityManager = ProximityManager()
    var statusItem: NSStatusItem? // Keep strong reference
    var cancellables = Set<AnyCancellable>()
    
    // Store original frame to return to
    var orbFrame: NSRect = NSRect(x: 0, y: 0, width: 300, height: 300)

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Create initial window (Orb size)
        orbFrame = NSRect(x: 0, y: 0, width: 300, height: 300)
        
        let window = NSWindow(
            contentRect: orbFrame,
            styleMask: [.borderless], // No title bar
            backing: .buffered,
            defer: false
        )
        
        // Critical for transparency and overlay behavior
        window.isOpaque = false
        window.backgroundColor = .clear
        window.level = .floating // Use floating instead of screenSaver for drag support
        // Allows appearing on all spaces
        window.collectionBehavior = [.canJoinAllSpaces, .stationary] 
        window.hasShadow = false // Let the view handle shadows
        
        window.center()
        // Save the center position
        orbFrame = window.frame
        
        // Use OrbContentView which observes ProximityManager
        let hostingView = NSHostingView(rootView: OrbContentView(proximity: proximityManager))
        window.contentView = hostingView
        
        // Register window for file drops
        window.registerForDraggedTypes([.fileURL, .URL])
        
        // Make sure window can receive events
        window.acceptsMouseMovedEvents = true
        window.ignoresMouseEvents = false
        
        window.makeKeyAndOrderFront(nil)
        
        // Make window movable by background
        window.isMovableByWindowBackground = true
        
        self.window = window
        
        setupMenuBar()
        setupStateObservation()
    }
    
    func setupStateObservation() {
        // Observe unlocked state to animate window
        proximityManager.$isUnlocked
            .dropFirst() // Skip initial value
            .receive(on: DispatchQueue.main)
            .sink { [weak self] unlocked in
                self?.animateWindow(unlocked: unlocked)
            }
            .store(in: &cancellables)
    }
    
    func animateWindow(unlocked: Bool) {
        guard let window = self.window, let screen = NSScreen.main else { return }
        
        if unlocked {
            // Shrink to Orb
            window.level = .floating
            window.setFrame(orbFrame, display: true, animate: true)
            window.ignoresMouseEvents = false // Allow dragging
        } else {
            // Save current position before expanding (in case user dragged it)
            orbFrame = window.frame
            
            // Expand to Shield (Full Screen)
            window.level = .screenSaver // High priority to cover everything
            window.setFrame(screen.frame, display: true, animate: true)
            // Note: We keep ignoresMouseEvents = false so we can shake mouse or click unlock
        }
    }
    
    // Strong reference to status item to keep it alive

    func setupMenuBar() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        
        if let button = statusItem?.button {
            button.image = NSImage(systemSymbolName: "circle.hexagongrid.fill", accessibilityDescription: "Ambient Orb")
        }
        
        let menu = NSMenu()
        menu.addItem(withTitle: "Toggle Orb", action: #selector(toggleOrbVisibility), keyEquivalent: "o")
        menu.addItem(NSMenuItem.separator())
        menu.addItem(withTitle: "Quit AmbientOrb", action: #selector(terminateApp), keyEquivalent: "q")
        
        statusItem?.menu = menu
    }
    
    @objc func toggleOrbVisibility() {
        guard let window = self.window else { return }
        
        if window.alphaValue > 0 {
            // Hide
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.3
                window.animator().alphaValue = 0
            }
        } else {
            // Show
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.3
                window.animator().alphaValue = 1
            }
        }
    }
    
    @objc func terminateApp() {
        NSApplication.shared.terminate(nil)
    }
}

@main
struct OrbTestApp {
    static func main() {
        let app = NSApplication.shared
        let delegate = OrbTestDelegate()
        app.delegate = delegate
        app.setActivationPolicy(.accessory) // Hide from dock, behaves like a system overlay
        app.run()
    }
}
