import SwiftUI

struct ShieldView: View {
    @ObservedObject var proximity: ProximityManager
    
    // Smooth animation for blur and calibration
    var blurRadius: CGFloat {
        proximity.isUnlocked ? 0 : CGFloat(proximity.signalStrengthLoss * 2) 
    }
    
    var opacity: Double {
        proximity.isUnlocked ? 0 : min(Double(proximity.signalStrengthLoss) / 50.0, 0.95)
    }
    
    var body: some View {
        ZStack {
            // Stack multiple blur layers to increase density
            // Layer 1: Base Frost
            VisualEffectView(material: .headerView, blendingMode: .behindWindow)
                .edgesIgnoringSafeArea(.all)
                .opacity(proximity.isUnlocked ? 0 : 1)
            
            // Layer 2: Deep Blur
            VisualEffectView(material: .sidebar, blendingMode: .behindWindow)
                .edgesIgnoringSafeArea(.all)
                .opacity(opacity)
                
            // Layer 3: Surface Frost (distortion)
            VisualEffectView(material: .hudWindow, blendingMode: .behindWindow)
                .edgesIgnoringSafeArea(.all)
                .opacity(opacity)
            
            // 4. SwiftUI Blur (Additional distortion on top)
            if !proximity.isUnlocked {
                Color.white.opacity(0.1)
                    .background(.ultraThinMaterial)
                    .blur(radius: 50) // High radius
            }
            
            // Status Indicator (Only visible when "locked")
            if !proximity.isUnlocked && opacity > 0.2 {
                VStack(spacing: 20) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.white.opacity(0.8))
                        .symbolEffect(.pulse.byLayer, options: .repeating, isActive: true)
                    
                    Text("Proximity Shield Active")
                        .font(.system(size: 24, weight: .light, design: .rounded))
                        .foregroundColor(.white.opacity(0.9))
                    
                    Text("Shake mouse to override")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.5))
                        .padding(.top, 8)
                }
            }
        }
    }
}

// Helper for Visual Effect (Glass)
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
    
    func updateNSView(_ nsView: NSVisualEffectView, context: Context) {
        nsView.material = material
        nsView.blendingMode = blendingMode
    }
}
