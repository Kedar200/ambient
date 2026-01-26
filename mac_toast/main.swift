import SwiftUI
import AppKit

class AppDelegate: NSObject, NSApplicationDelegate {
    var window: NSWindow?
    var imagePath: String = ""
    var fileName: String = ""
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        // Get command line arguments
        let args = CommandLine.arguments
        NSLog("PhotoToast started with args: %@", args)
        
        guard args.count >= 2 else {
            NSLog("Error: Not enough arguments")
            NSApplication.shared.terminate(nil)
            return
        }
        
        imagePath = args[1]
        fileName = args.count >= 3 ? args[2] : URL(fileURLWithPath: imagePath).lastPathComponent
        
        NSLog("Image path: %@", imagePath)
        
        showToast()
    }
    
    func showToast() {
        // Get screen size
        guard let screen = NSScreen.main else {
            NSLog("Error: No main screen found")
            NSApplication.shared.terminate(nil)
            return
        }
        let screenRect = screen.visibleFrame
        NSLog("Main screen found: %@", screenRect.debugDescription)
        
        // Window size
        let windowWidth: CGFloat = 340
        let windowHeight: CGFloat = 320
        
        // Position in bottom-right corner with padding
        let windowX = screenRect.maxX - windowWidth - 20
        let windowY = screenRect.minY + 20
        
        let contentView = ToastView(imagePath: imagePath, fileName: fileName) {
            self.openImage()
        } onDismiss: {
            NSApplication.shared.terminate(nil)
        }
        
        window = NSWindow(
            contentRect: NSRect(x: windowX, y: windowY, width: windowWidth, height: windowHeight),
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        
        window?.level = .screenSaver // Very high level
        window?.isOpaque = false
        window?.backgroundColor = .clear
        window?.hasShadow = true
        window?.canHide = false
        window?.contentView = NSHostingView(rootView: contentView)
        window?.makeKeyAndOrderFront(nil)
        
        NSApp.activate(ignoringOtherApps: true)
        
        // Animate in
        window?.alphaValue = 0
        NSAnimationContext.runAnimationGroup { context in
            context.duration = 0.3
            window?.animator().alphaValue = 1
        }
        
        // Auto-dismiss after 5 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
            self.dismissToast()
        }
    }
    
    func openImage() {
        NSWorkspace.shared.open(URL(fileURLWithPath: imagePath))
        dismissToast()
    }
    
    func dismissToast() {
        NSAnimationContext.runAnimationGroup({ context in
            context.duration = 0.3
            window?.animator().alphaValue = 0
        }, completionHandler: {
            NSApplication.shared.terminate(nil)
        })
    }
}

struct ToastView: View {
    let imagePath: String
    let fileName: String
    let onTap: () -> Void
    let onDismiss: () -> Void
    
    @State private var isHovered = false
    @State private var isDragging = false
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                // Image preview - draggable!
                if let nsImage = NSImage(contentsOfFile: imagePath) {
                    Image(nsImage: nsImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(height: 220)
                        .clipped()
                        .overlay(
                            // Drag hint
                            VStack {
                                Spacer()
                                HStack {
                                    Spacer()
                                    Text("↖ Drag to share")
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.black.opacity(0.6))
                                        .clipShape(Capsule())
                                        .padding(8)
                                        .opacity(isHovered && !isDragging ? 1 : 0)
                                }
                            }
                        )
                        .onDrag {
                            isDragging = true
                            let fileURL = URL(fileURLWithPath: imagePath)
                            return NSItemProvider(object: fileURL as NSURL)
                        }
                } else {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(height: 220)
                        .overlay(
                            Image(systemName: "photo")
                                .font(.system(size: 40))
                                .foregroundColor(.gray)
                        )
                }
                
                // Info section
                HStack(spacing: 12) {
                    Text("📸")
                        .font(.system(size: 28))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Live Photo Wall")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                        
                        Text(fileName)
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.7))
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(Color.black.opacity(0.5))
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.white.opacity(0.2), lineWidth: 1)
            )
            
            // Close button
            Button(action: onDismiss) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 22))
                    .foregroundColor(.white.opacity(0.8))
                    .shadow(radius: 2)
            }
            .buttonStyle(.plain)
            .padding(8)
            .opacity(isHovered ? 1 : 0.6)
        }
        .background(
            VisualEffectView(material: .hudWindow, blendingMode: .behindWindow)
                .clipShape(RoundedRectangle(cornerRadius: 16))
        )
        .shadow(color: .black.opacity(0.3), radius: 20)
        .scaleEffect(isDragging ? 0.95 : 1.0)
        .animation(.easeInOut(duration: 0.2), value: isDragging)
        .onTapGesture {
            onTap()
        }
        .onHover { hovering in
            isHovered = hovering
        }
    }
}

struct VisualEffectView: NSViewRepresentable {
    let material: NSVisualEffectView.Material
    let blendingMode: NSVisualEffectView.BlendingMode
    
    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = blendingMode
        view.state = .active
        return view
    }
    
    func updateNSView(_ nsView: NSVisualEffectView, context: Context) {}
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
