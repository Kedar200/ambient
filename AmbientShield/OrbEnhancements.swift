import SwiftUI

// MARK: - Aurora Halo
// Rotating outer ring with aurora gradient. Cinematic idle accent for the orb.
struct AuroraHaloView: View {
    var diameter: CGFloat = 190
    var intensity: CGFloat = 1.0

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            let rotation = Angle(degrees: (t * 12).truncatingRemainder(dividingBy: 360))
            let breathe = 0.85 + 0.15 * sin(t * 0.8)

            ZStack {
                // Outer aurora ring
                Circle()
                    .strokeBorder(
                        AngularGradient(
                            gradient: Gradient(colors: [
                                Color.cyan.opacity(0.0),
                                Color.cyan.opacity(0.55),
                                Color.purple.opacity(0.55),
                                Color.pink.opacity(0.45),
                                Color.cyan.opacity(0.0),
                            ]),
                            center: .center
                        ),
                        lineWidth: 6
                    )
                    .blur(radius: 6)
                    .frame(width: diameter, height: diameter)
                    .rotationEffect(rotation)
                    .opacity(0.75 * intensity)

                // Inner crisp ring
                Circle()
                    .strokeBorder(
                        AngularGradient(
                            gradient: Gradient(colors: [
                                Color.white.opacity(0.0),
                                Color.cyan.opacity(0.25),
                                Color.purple.opacity(0.2),
                                Color.white.opacity(0.0),
                            ]),
                            center: .center
                        ),
                        lineWidth: 1.2
                    )
                    .frame(width: diameter - 10, height: diameter - 10)
                    .rotationEffect(-rotation)
                    .opacity(0.9 * intensity)
            }
            .scaleEffect(breathe)
            .blendMode(.plusLighter)
            .allowsHitTesting(false)
        }
    }
}

// MARK: - Particle Field
// Ambient motes drifting around the orb. Adaptive count for low-power conditions.
struct ParticleFieldView: View {
    var bounds: CGSize = CGSize(width: 280, height: 280)

    private struct Mote {
        var x: CGFloat
        var y: CGFloat
        var vx: CGFloat
        var vy: CGFloat
        var depth: CGFloat // 0..1, affects size + alpha
        var phase: Double
    }

    @State private var motes: [Mote] = []

    private var motesCount: Int {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: return 24
        case .fair: return 16
        default: return 10
        }
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            Canvas { ctx, size in
                for m in motes {
                    // Parallax drift: larger depth = stronger motion component
                    let drift = CGFloat(sin(t * 0.5 + m.phase)) * 6 * m.depth
                    let px = m.x + drift
                    let py = m.y + CGFloat(cos(t * 0.4 + m.phase)) * 4 * m.depth

                    let radius = 0.6 + 2.2 * m.depth
                    let alpha = 0.15 + 0.55 * m.depth
                    let rect = CGRect(
                        x: px - radius,
                        y: py - radius,
                        width: radius * 2,
                        height: radius * 2
                    )
                    ctx.fill(
                        Path(ellipseIn: rect),
                        with: .color(Color.cyan.opacity(alpha))
                    )
                    // soft bloom
                    ctx.fill(
                        Path(ellipseIn: rect.insetBy(dx: -radius, dy: -radius)),
                        with: .color(Color.white.opacity(alpha * 0.15))
                    )
                }
            }
            .onAppear { seedIfNeeded(for: bounds) }
            .onChange(of: bounds) { _, newBounds in seedIfNeeded(for: newBounds, force: true) }
            .blendMode(.plusLighter)
            .allowsHitTesting(false)
        }
    }

    private func seedIfNeeded(for size: CGSize, force: Bool = false) {
        guard force || motes.isEmpty else { return }
        let count = motesCount
        motes = (0..<count).map { _ in
            let angle = Double.random(in: 0..<(2 * .pi))
            let r = CGFloat.random(in: 70...(min(size.width, size.height) / 2 - 10))
            return Mote(
                x: size.width / 2 + r * CGFloat(cos(angle)),
                y: size.height / 2 + r * CGFloat(sin(angle)),
                vx: CGFloat.random(in: -0.2...0.2),
                vy: CGFloat.random(in: -0.2...0.2),
                depth: CGFloat.random(in: 0.2...1.0),
                phase: Double.random(in: 0..<(2 * .pi))
            )
        }
    }
}

// MARK: - Shockwave Ring
// Expanding ring stroke on drop release. Fires once per trigger id.
struct ShockwaveRingView: View {
    var trigger: Int
    var diameter: CGFloat = 130

    @State private var scale: CGFloat = 0.7
    @State private var opacity: Double = 0

    var body: some View {
        Circle()
            .stroke(
                LinearGradient(
                    colors: [Color.cyan, Color.purple.opacity(0.6), .clear],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ),
                lineWidth: 4
            )
            .frame(width: diameter, height: diameter)
            .scaleEffect(scale)
            .opacity(opacity)
            .blendMode(.plusLighter)
            .allowsHitTesting(false)
            .onChange(of: trigger) { _, _ in fire() }
    }

    private func fire() {
        scale = 0.7
        opacity = 1.0
        withAnimation(.easeOut(duration: 0.7)) {
            scale = 3.5
            opacity = 0
        }
    }
}
