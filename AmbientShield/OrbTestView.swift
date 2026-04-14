import SwiftUI
import UniformTypeIdentifiers

struct OrbTestView: View {
    // Battery level from ProximityManager
    @Binding var batteryPercentage: Int
    var isConnected: Bool = true

    
    @State private var morphPhase: CGFloat = 0
    @State private var shimmerPhase: CGFloat = 0
    @State private var messageFloat: CGFloat = 0
    
    // Wormhole drag & drop states
    @State private var isDragHovering: Bool = false

    @State private var suckingAnimation: Bool = false
    @State private var droppedFiles: [URL] = []
    
    // Ripple Animation States
    @State private var rippleTrigger: Bool = false

    // Shockwave ring trigger (increments on each drop)
    @State private var shockwaveTrigger: Int = 0


    
    // Computed property for battery ring progress (0.0 - 1.0)
    private var batteryProgress: CGFloat {
        CGFloat(batteryPercentage) / 100.0
    }
    
    // Battery icon based on level
    private var batteryIcon: String {
        switch batteryPercentage {
        case 0...10: return "battery.0"
        case 11...25: return "battery.25"
        case 26...50: return "battery.50"
        case 51...75: return "battery.75"
        default: return "battery.100"
        }
    }
    
    // Battery ring color based on level
    private var batteryColor: Color {
        if !isConnected { return .gray }
        switch batteryPercentage {

        case 0...20: return .red
        case 21...40: return .orange
        default: return .green
        }
    }
    
    // Mood configuration based on battery
    private var blobMood: (speed: CGFloat, complexity: Double, sharpness: Double) {
        switch batteryPercentage {
        case 0..<20: 
            return (speed: 3.5, complexity: 2.0, sharpness: 1.5) // Nervous/Jagged
        case 20..<80: 
            return (speed: 1.0, complexity: 1.0, sharpness: 1.0) // Normal
        default: 
            return (speed: 0.4, complexity: 0.6, sharpness: 0.8) // Zen/Calm
        }
    }

    // Cinematic background stack (extracted to help Swift's type checker).
    @ViewBuilder
    private var cinematicBackdrop: some View {
        ParticleFieldView(bounds: CGSize(width: 280, height: 280))
            .frame(width: 280, height: 280)
        AuroraHaloView(diameter: 190, intensity: isConnected ? 1.0 : 0.35)
        ShockwaveRingView(trigger: shockwaveTrigger, diameter: 140)
    }

    var body: some View {
        ZStack {
            cinematicBackdrop

            // Ripple Effect (Water Drop Wave)
            if rippleTrigger {
                ForEach(0..<3) { i in
                    Circle()
                        .stroke(Color.white.opacity(0.4), lineWidth: 2)
                        .scaleEffect(rippleTrigger ? 2.5 : 1.0)
                        .opacity(rippleTrigger ? 0 : 1)
                        .animation(
                            .easeOut(duration: 1.5)
                            .delay(Double(i) * 0.2),
                            value: rippleTrigger
                        )
                }
                .frame(width: 130, height: 130)
            }
            
            // === Core Glass Sphere (Water Droplet) ===
            orbCore
        }
        .frame(width: 280, height: 280)
        .onDrop(of: [.fileURL, .item], isTargeted: $isDragHovering) { providers in
            handleDrop(providers: providers)
            return true
        }
        .onChange(of: isDragHovering) { _, hovering in
            if hovering {
                withAnimation(.easeInOut(duration: 0.3)) { suckingAnimation = true }
            } else {
                withAnimation(.easeOut(duration: 0.3)) { suckingAnimation = false }
            }
        }
        .onAppear {
            withAnimation(.linear(duration: 20).repeatForever(autoreverses: false)) { morphPhase = .pi * 2 }
            withAnimation(.linear(duration: 8).repeatForever(autoreverses: false)) { shimmerPhase = 360 }
            withAnimation(.easeInOut(duration: 3).repeatForever(autoreverses: true)) { messageFloat = -8 }
        }
    }

    // Core glass orb — extracted from `body` so the Swift type checker can
    // handle the enlarged cinematic backdrop without timing out.
    @ViewBuilder
    private var orbCore: some View {
        ZStack {
            // Clear glass base with subtle gradient
            // Animated Mood Blob
            MorphingBlobShape(phase: morphPhase, complexity: blobMood.complexity, sharpness: blobMood.sharpness, speed: blobMood.speed)
                    .fill(
                         RadialGradient(
                             colors: [
                                 Color.white.opacity(0.12),
                                 Color.white.opacity(0.06),
                                 Color.white.opacity(0.03)
                             ],
                             center: .center,
                             startRadius: 0,
                             endRadius: 65
                         )
                     )
                    .animation(.easeInOut(duration: 2.0), value: batteryPercentage)

                // Amber warm glow (more transparent)
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                (isConnected ? Color.orange : Color.gray).opacity(0.25),
                                (isConnected ? Color.orange : Color.gray).opacity(0.12),
                                .clear
                            ],

                            center: .center,
                            startRadius: 15,
                            endRadius: 55
                        )
                    )
                
                // Bottom reflection (water droplet refraction)
                Circle()
                    .trim(from: 0.15, to: 0.3)
                    .stroke(
                        Color.white.opacity(0.35),
                        style: StrokeStyle(lineWidth: 2, lineCap: .round)
                    )
                    .frame(width: 108, height: 108)
                    .rotationEffect(.degrees(160))
                
                // Shimmer light effect
                Circle()
                    .trim(from: 0, to: 0.1)
                    .stroke(
                        LinearGradient(
                            colors: [.clear, .white.opacity(0.7), .clear],
                            startPoint: .leading,
                            endPoint: .trailing
                        ),
                        style: StrokeStyle(lineWidth: 3, lineCap: .round)
                    )
                    .frame(width: 105, height: 105)
                    .rotationEffect(.degrees(shimmerPhase))
                
                // Progress ring with glow (battery indicator)
                ZStack {
                    // Outer glow
                    Circle()
                        .trim(from: 0, to: batteryProgress)
                        .stroke(batteryColor.opacity(0.35), lineWidth: 10)
                        .blur(radius: 6)
                    
                    // Inner glow
                    Circle()
                        .trim(from: 0, to: batteryProgress)
                        .stroke(batteryColor.opacity(0.5), lineWidth: 6)
                        .blur(radius: 2)
                    
                    // Main ring
                    Circle()
                        .trim(from: 0, to: batteryProgress)
                        .stroke(
                            LinearGradient(
                                colors: [
                                    batteryColor,
                                    batteryColor.opacity(0.8),
                                    batteryColor.opacity(0.5)
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            ),
                            style: StrokeStyle(lineWidth: 4, lineCap: .round)
                        )
                }
                .rotationEffect(.degrees(-90))
                .frame(width: 95, height: 95)
                .animation(.easeInOut(duration: 0.5), value: batteryPercentage)
                .scaleEffect(batteryPercentage > 80 ? 1.02 : 1.0) // Breathing effect for high battery
                .animation(.easeInOut(duration: 2.0).repeatForever(autoreverses: true), value: batteryPercentage > 80)
                
                // Content - Battery percentage display
                VStack(spacing: 4) {
                    Text("\(batteryPercentage)%")
                        .font(.system(size: 32, weight: .thin, design: .rounded))
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.3), radius: 2)
                        .contentTransition(.numericText())
                        .animation(.easeInOut(duration: 0.3), value: batteryPercentage)
                    
                    HStack(spacing: 3) {
                        Image(systemName: batteryIcon)
                            .font(.system(size: 10))
                            .foregroundStyle(batteryColor)
                        Text("Phone Battery")
                            .font(.system(size: 10, weight: .medium))
                    }
                    .foregroundStyle(.white.opacity(0.9))
                    .shadow(color: .black.opacity(0.2), radius: 1)
                }
                
                // Outer rim with water droplet edge
                Circle()
                    .stroke(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(0.7),
                                Color.white.opacity(0.3),
                                Color.white.opacity(0.15)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.5
                    )
                
                // Inner edge shadow
                Circle()
                    .stroke(
                        Color.black.opacity(0.1),
                        lineWidth: 1
                    )
                    .blur(radius: 2)
                    .offset(x: 1, y: 1)
            }
            .frame(width: 130, height: 130)
            .shadow(color: .white.opacity(0.15), radius: 8, x: -3, y: -3)
            .shadow(color: .black.opacity(0.25), radius: 15, x: 3, y: 3)
            // Wormhole activation effects
            .overlay {
                if isDragHovering {
                    ZStack {
                        // Outer vortex rings
                        ForEach(0..<4, id: \.self) { i in
                            Circle()
                                .stroke(
                                    LinearGradient(
                                        colors: [
                                            Color.purple.opacity(0.6 - Double(i) * 0.1),
                                            Color.blue.opacity(0.5 - Double(i) * 0.1),
                                            Color.cyan.opacity(0.4 - Double(i) * 0.1),
                                            .clear
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ),
                                    lineWidth: 2 - CGFloat(i) * 0.3
                                )
                                .frame(width: 130 + CGFloat(i) * 25, height: 130 + CGFloat(i) * 25)
                                .opacity(suckingAnimation ? 0.8 : 0.3)

                        }
                        
                        // Inner pulsing core
                        Circle()
                            .fill(
                                RadialGradient(
                                    colors: [
                                        Color.purple.opacity(0.5),
                                        Color.blue.opacity(0.3),
                                        .clear

                                    ],
                                    center: .center,
                                    startRadius: 0,
                                    endRadius: 70
                                )
                            )
                            .frame(width: 140, height: 140)
                            .scaleEffect(suckingAnimation ? 1.1 : 0.9)
                        
                        // Spiral particles
                        ForEach(0..<8, id: \.self) { i in
                            Circle()
                                .fill(Color.white.opacity(0.7))
                                .frame(width: 4, height: 4)
                                .offset(x: suckingAnimation ? 20 : 80)

                        }
                    }
                }
            }

            .scaleEffect(isDragHovering ? (suckingAnimation ? 1.15 : 1.05) : 1.0)

            
            // Text("Meeting with\nSarah @ 10am")
            //     .font(.system(size: 12, weight: .medium))
            //     .padding(.horizontal, 12)
            //     .padding(.vertical, 8)
            //     .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
            //     .overlay(
            //         RoundedRectangle(cornerRadius: 12)
            //             .stroke(.white.opacity(0.2), lineWidth: 0.5)
            //     )
            //     .offset(x: 65, y: -72)
            //     .opacity(isDragHovering ? 0.3 : 1.0)
            
            // Dropped files indicator
            if !droppedFiles.isEmpty {
                VStack(spacing: 2) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    Text("\(droppedFiles.count) absorbed")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                }
                .offset(y: 90)
            }
    }

    // Handle file drop - the wormhole "absorbs" the files
    private func handleDrop(providers: [NSItemProvider]) {
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier) { item, error in
                    if let data = item as? Data,
                        let url = URL(dataRepresentation: data, relativeTo: nil) {
                        DispatchQueue.main.async {
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) {
                                droppedFiles.append(url)
                            }
                            print("🌀 Wormhole absorbed: \(url.lastPathComponent)")

                            // Trigger shockwave ring on release
                            shockwaveTrigger &+= 1

                            // Trigger Ripple Animation
                            rippleTrigger = false // Reset
                            withAnimation(.none) { // Forces reset immediately
                                rippleTrigger = false
                            }
                            // Start Animation
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                rippleTrigger = true
                                // Reset after animation completes
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                    rippleTrigger = false 
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}

struct FloatingMessageBubble: View {
    let text: String
    
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            // Message bubble background
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(.ultraThinMaterial)
                .environment(\.colorScheme, .light)
                .overlay {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.6),
                                    Color.white.opacity(0.2)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                }
                .shadow(color: .black.opacity(0.15), radius: 12, x: 0, y: 4)
                .frame(width: 140, height: 50)
            
            // Text content
            Text(text)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundStyle(.white.opacity(0.95))
                .multilineTextAlignment(.leading)
                .lineSpacing(2)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(width: 140, height: 50, alignment: .leading)
            
            // Tail/pointer (pointing to the orb)
            Triangle()
                .fill(.ultraThinMaterial)
                .environment(\.colorScheme, .light)
                .overlay {
                    Triangle()
                        .stroke(Color.white.opacity(0.3), lineWidth: 0.5)
                }
                .frame(width: 12, height: 8)
                .rotationEffect(.degrees(180))
                .offset(x: -52, y: 4)
                .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 2)
        }
    }
}

// Triangle shape for message tail
struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

struct MorphingBlobShape: Shape {
    var phase: CGFloat
    var complexity: Double
    var sharpness: Double
    let speed: CGFloat
    
    var animatableData: AnimatablePair<CGFloat, AnimatablePair<Double, Double>> {
        get { AnimatablePair(phase, AnimatablePair(complexity, sharpness)) }
        set { 
            phase = newValue.first
            complexity = newValue.second.first
            sharpness = newValue.second.second
        }
    }
    
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let blobComplexity = CGFloat(complexity)
        let blobSharpness = CGFloat(sharpness)
        
        let points = Int(16.0 * blobComplexity)
        let step = (2 * .pi) / CGFloat(points)
        var coords: [CGPoint] = []
        
        for i in 0..<points {
            let angle = CGFloat(i) * step
            let n1 = sin(phase * speed + angle * 2.2 * blobSharpness) * 0.07 * blobSharpness
            let n2 = cos(phase * speed * 0.7 + angle * 3 * blobSharpness) * 0.04 * blobSharpness
            let radius = (min(rect.width, rect.height) / 2) * (1.0 + n1 + n2)
            coords.append(CGPoint(
                x: center.x + cos(angle) * radius,
                y: center.y + sin(angle) * radius
            ))
        }
        
        if !coords.isEmpty {
            path.move(to: coords[0])
            for i in 0..<coords.count {
                let current = coords[i]
                let next = coords[(i + 1) % coords.count]
                let nextNext = coords[(i + 2) % coords.count]
                let cp1 = CGPoint(
                    x: current.x + (next.x - current.x) * 0.5,
                    y: current.y + (next.y - current.y) * 0.5
                )
                let cp2 = CGPoint(
                    x: next.x - (nextNext.x - current.x) * 0.15,
                    y: next.y - (nextNext.y - current.y) * 0.15
                )
                path.addCurve(to: next, control1: cp1, control2: cp2)
            }
            path.closeSubpath()
        }
        return path
    }
}

#Preview {
    ZStack {
        LinearGradient(
            colors: [
                Color(red: 0.4, green: 0.2, blue: 0.6),
                Color(red: 0.2, green: 0.4, blue: 0.7),
                Color(red: 0.3, green: 0.5, blue: 0.8)
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .blur(radius: 80)
        .ignoresSafeArea()
        
        OrbTestView(batteryPercentage: .constant(75), isConnected: true)

    }
}