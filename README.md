# Ambient Network OS (v1)

The project's goal is to create a cross-device, local-first OS layer for seamless device interaction without cloud dependency. The initial MVP focuses on building a Rust-based desktop agent and an Android app for instant image sharing.

Ambient Network OS (v1) - Technical Report

Project Title: Ambient Network OS (v1)
Author: Kedar Deshmukh
 Contact: kedardeshmukh2003@gmail.com | LinkedIn | GitHub

1. Objective
To design and build a cross-device, local-first personal OS layer that seamlessly connects a user's phone, laptop, and other smart devices within a secure, offline network. The Ambient OS allows for clipboard sync, file and image transfer, remote command execution, and future AI-powered interactions — all without depending on any cloud provider.

2. Motivation
Current ecosystems (e.g., Apple Continuity, Google Home, Microsoft Recall) offer device sync, but are:
Tied to cloud services


Proprietary and closed


Platform-specific


Ambient Network OS is open, encrypted, and offline-first. It empowers individuals to build their own ambient mesh network, giving them total control over their personal computing environment.

3. Why This Is Needed (Gap in Existing Ecosystems)
While there are many tools that provide partial functionality, none offer the complete, extensible, user-controlled ambient experience that Ambient OS aims to deliver:
Tool/Platform
File Sync
Clipboard Sync
Cross-Platform
Multi-User Mesh
Offline
Custom Automation
Apple Continuity
✅
✅
❌ (Apple only)
❌
❌
❌
KDE Connect
✅
✅
✅ (Linux/Android only)
❌
✅
❌
Syncthing
✅
❌
✅
❌
✅
❌
Tailscale + Wormhole
✅
❌
✅
❌
❌
❌
Home Assistant
❌
❌
❌ (IoT-focused)
❌
✅
✅ (IoT-specific)

Ambient OS is the first system designed to:
Work across Android, Linux, macOS, and Windows


Let each user control their own cluster of devices (e.g., phone, laptop, TV)


Securely share across users with explicit permissions


Support offline-first operation with WAN fallback (via Tailscale)


Enable ambient computing workflows (e.g., tap to send, photo triggers, automation)


Stay open-source, extensible, and cloud-optional


Ambient OS isn't just a replacement for AirDrop or clipboard tools — it's a new foundation for local-first, personal computing in a world where cloud dominance is the default.

4. Features Overview
Phase 1 (MVP)
Instant image sharing from phone to laptop


WebSocket/HTTP-based LAN communication


Cross-platform Rust-based agent


Android app to trigger actions


Manual IP pairing with optional QR scan


Phase 2 (Modular Extensions)
Two-way clipboard synchronization


Cross-device shell/command execution


File sync (screenshots, media, etc.)


Device discovery using mDNS


Phase 3 (Multi-User Shared Mesh)
Isolated user device groups (e.g., Kedar's devices vs. Friend's devices)


Each user has their own synced cluster: phone, laptop, tablet, TV


Devices of different users share the same LAN mesh but remain private


Peer-to-peer permission system: e.g., Kedar can request to send an image to his friend's TV; access is granted explicitly


Secure request/response protocol to facilitate cross-user interactions under full user control


Phase 4 (Cross-Network Sync via Tailscale)
Seamless connectivity even when devices are on different networks


Integrate Tailscale or self-hosted Headscale to create a private WireGuard-based mesh


Each device receives a stable private IP address (e.g., 100.x.x.x)


Ambient OS modules (image share, clipboard sync, etc.) work identically over LAN or Tailscale mesh


Ensures minimal latency (~10–50ms) and full encryption without user reconfiguration


Future Phases
AI Assistants (local voice/gesture-based triggers)


Contextual automation (e.g., pause media on laptop when call is answered on phone)


TV/camera integrations



5. System Architecture
               📱 Android Phone
           ┌──────────────────────────────┐
           │ Ambient Agent (Kotlin + Rust)│
           │ - Image Sender               │
           │ - Clipboard Monitor          │
           │ - Command Sender             │
           └──────────────────────┬───────┘
                                  │
        ┌─────────────────────────┼────────────────────────┐
        │           LAN / P2P Layer (Wi-Fi)                │
        │ - WebSocket or HTTP over LAN                     │
        │ - mDNS (Zeroconf) device discovery               │
        │ - Encrypted communication (AES + Ed25519)        │
        └─────────────────────────┼────────────────────────┘
                                  │
           ┌──────────────────────▼─────────────────────┐
           │         💻 Laptop / Desktop Agent          │
           │ - Rust server for receiving data            │
           │ - Opens files (images, etc.)                │
           │ - Clipboard syncing / Shell execution       │
           └─────────────────────────────────────────────┘

           ┌─────────────────────── Shared Mesh Layer ───────────────────────┐
           │ Users are grouped into isolated sync clusters. Each device      │
           │ cluster communicates privately within itself. With permission, │
           │ users can interact across clusters (e.g., file share, command) │
           └─────────────────────────────────────────────────────────────────┘

           ┌─────────────────────── Tailscale Layer ─────────────────────────┐
           │ Enables secure communication across WAN (mobile + home + office)│
           │ Ambient Agents use stable Tailscale IPs for communication       │
           │ Peer-to-peer if possible, fallback to DERP relay               │
           └─────────────────────────────────────────────────────────────────┘




5. Components Breakdown
Android App
Kotlin-based


Double-tap to trigger file send


Optionally overlays gallery with floating button


Stores target device IP (LAN or Tailscale IP)


Can send requests to other users' devices with permission


Rust Agent (Laptop/Desktop)
WebSocket/HTTP listener


Opens received files via OS-native viewer


Future: executes commands and syncs clipboard


Handles access control for cross-user file requests


Supports both LAN and Tailscale interfaces


Device Pairing
First-time: manual IP entry or QR code


Future: mDNS-based automatic discovery for LAN


Shared mesh mode: requires handshake & approval per device group


Tailscale pairing uses stable identity and access control



6. MVP Implementation Plan
ID
Component
Task
Status
A1
Android Sender
Select and send image
🔜
A2
Rust Agent
Receive and open image
🔜
A3
Config Storage
IP persistence
🔜
A4
Clipboard Sync
Two-way clipboard
⏳
A5
Command Bus
Execute shell commands
⏳
A6
mDNS Discovery
Zeroconf peer discovery
⏳
A7
Encryption Layer
AES + Ed25519 key exchange
⏳
A8
Shared Mesh Mode
Isolated clusters + access UI
⏳
A9
Tailscale Mesh
WAN sync using Tailscale
⏳


7. Key Advantages
Private by Design: Zero cloud dependency


Fast: Uses local Wi-Fi or Tailscale mesh for low-latency transfers


Composable: Build only the modules you need


Cross-Platform: Works across Android, Linux, macOS, Windows


Multi-user Capable: Each user has a private device cluster with optional cross-sharing


Future-Proof: Extendable to AI, UWB, AR, DLNA, etc.


Network-Agnostic: Devices sync on LAN or over WAN via Tailscale



8. Stretch Goals & Vision
Whisper + TTS-based voice agent


Mesh-networked Raspberry Pi hub


Ambient scripting DSL (e.g., "on photo taken → sync to laptop")


Remote control UI


Fine-grained sharing across users with permissions (send to friend's TV)


Full Tailscale integration with fallback and routing



9. Repository and Licensing
GitHub: https://github.com/Kedar200/ambient-os (to be initialized)


License: MIT or Apache 2.0 (TBD)



10. Conclusion
Ambient OS v1 is the first step toward a self-owned, secure, and futuristic computing mesh that blends your devices into a unified ambient experience. Starting with image sharing, the foundation paves the way for a future where your devices communicate without friction or dependence on the cloud — even across networks using technologies like Tailscale.
You're not just syncing devices — you're reclaiming control over your digital space.

Prepared by Kedar Deshmukh | June 2025 