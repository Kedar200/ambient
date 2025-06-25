# Ambient Network OS

A seamless device interaction system with a Rust desktop agent and Android app for image sharing and clipboard synchronization.

## Overview

Ambient Network OS creates a seamless connection between your Android device and computer, allowing for:

- **Instant Image Sharing**: Share images directly from your Android gallery to your computer with zero friction
- **Clipboard Synchronization**: Keep your clipboard contents in sync between devices
- **Quick Access**: Use the Quick Settings tile for one-tap sharing of recent photos

## Components

### Rust Desktop Agent

The desktop agent runs on your computer and provides:

- HTTP server for receiving images and clipboard content
- Local storage for received files
- Clipboard synchronization with connected devices

### Android App

The Android app provides multiple ways to interact with your computer:

- **Modern UI**: Beautiful, intuitive interface with Material Design components
- **Silent Sharing**: Share images without UI interruption
- **Quick Settings Tile**: One-tap sharing of recent photos
- **Background Sync**: Clipboard synchronization runs as a foreground service

## Getting Started

1. **Run the Rust Agent**:
   ```
   cd rust_agent
   cargo run
   ```

2. **Install the Android App**:
   - Build from source with `./gradlew assembleDebug`
   - Install the APK on your Android device

3. **Configure the App**:
   - Enter your computer's IP address and port (default: 23921)
   - Enable clipboard sync if desired

## Usage

### Image Sharing

- **From Gallery**: Use the Android share menu and select "Ambient OS"
- **Quick Access**: Use the "Share to PC" Quick Settings tile
- **Manual**: Select and upload images directly from the app

### Clipboard Sync

- Enable clipboard sync in the app
- Copy text on either device and it will automatically sync to the other

## Technical Details

- The Android app uses modern Kotlin and Material Design components
- The Rust agent uses Axum for the HTTP server
- Communication happens over HTTP with multipart form data for images
- Clipboard sync uses a foreground service with proper notifications 