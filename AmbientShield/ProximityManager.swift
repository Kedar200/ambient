import SwiftUI
import CoreBluetooth

class ProximityManager: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published var isUnlocked = true {
        didSet {
            updateWindowInteractivity()
        }
    }
    @Published var signalStrengthLoss: Int = 0 // 0 = perfect signal, 100 = lost
    @Published var phoneBatteryLevel: Int = 0 // Phone battery percentage (0-100)
    @Published var isConnected: Bool = false // True if signal received recently

    
    weak var window: NSWindow?
    
    private var centralManager: CBCentralManager!
    // Using the same UUID as defined in Android ProximityBeacon.kt
    private let targetServiceUUID = CBUUID(string: "C0FF3300-1234-5678-9ABC-DEF000000000")
    
    // Mouse Shake Detection
    private var lastMousePosition: NSPoint = .zero
    private var shakeCount = 0
    private var lastShakeTime = Date()
    private var mouseCheckTimer: Timer?
    
    // Connection tracking
    private var lastSignalTime: Date?
    private let connectionTimeout: TimeInterval = 3.0 // Seconds before considering signal lost

    
    // Cooldown: prevents shield from reappearing until phone comes close again
    private var userDismissedShield = false  // True after mouse shake override
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
        startMousePolling()
    }
    
    func updateWindowInteractivity() {
        DispatchQueue.main.async {
            // If unlocked, we ignore mouse events (click-through). 
            // If locked, we catch mouse events (blocking).
            self.window?.ignoresMouseEvents = self.isUnlocked
        }
    }
    
    // ... BLE logic handles signal updates ...
    
    func startMousePolling() {
        // Poll mouse location every 0.1s. Works globally without Accessibility permissions for "Location only"
        mouseCheckTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.handleMouseMovement()
            self?.checkConnectionStatus()
        }
    }
    
    func checkConnectionStatus() {
        if let lastSignal = lastSignalTime {
            let timeSinceLastSignal = Date().timeIntervalSince(lastSignal)
            if timeSinceLastSignal > connectionTimeout && isConnected {
                DispatchQueue.main.async {
                    self.isConnected = false
                    print("⚠️ Connection lost - no signal for \(self.connectionTimeout)s")
                }
            }
        }
    }
    
    func handleMouseMovement() {
        let currentPosition = NSEvent.mouseLocation
        let distance = hypot(currentPosition.x - lastMousePosition.x, currentPosition.y - lastMousePosition.y)
        
        // If moved significantly in short time
        if distance > 100 { // Increased threshold for "Vigorous"
            let now = Date()
            if now.timeIntervalSince(lastShakeTime) < 0.5 {
                 shakeCount += 1
            } else {
                shakeCount = 1
            }
            lastShakeTime = now
            
            if shakeCount > 10 { // Approx 1-2 seconds of vigorous shaking
                overrideUnlock()
            }
        }
        
        lastMousePosition = currentPosition
    }
    
    func overrideUnlock() {
        if isUnlocked { return }
        
        print("Mouse shake detected! Emergency override - shield won't reappear until phone comes close again.")
        userDismissedShield = true  // Set cooldown - shield won't come back until phone is close again
        DispatchQueue.main.async {
            withAnimation(.spring()) {
                self.signalStrengthLoss = 0
                self.isUnlocked = true
            }
        }
    }
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            // Start scanning for our specific Android Service
            centralManager.scanForPeripherals(withServices: [targetServiceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
            print("Scanning for proximity beacons with UUID: \(targetServiceUUID)")
        } else {
            print("Bluetooth not available: \(central.state)")
        }
    }
    
    // Signal Smoothing
    private var rssiHistory: [Int] = []
    private let smoothWindow = 10
    
    // ...
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        let strength = RSSI.intValue
        // Filter out absurd values
        if strength == 127 { return } 
        
        // Update connection status
        lastSignalTime = Date()
        if !isConnected {
            DispatchQueue.main.async {
                self.isConnected = true
                print("✅ specific Connection restored")
            }
        }
 
        
        // Parse battery level from manufacturer data
        // Format: 2 bytes manufacturer ID (0xFFFF) + 1 byte battery level
        if let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data,
           manufacturerData.count >= 3 {
            let batteryLevel = Int(manufacturerData[2])
            if batteryLevel >= 0 && batteryLevel <= 100 {
                DispatchQueue.main.async {
                    self.phoneBatteryLevel = batteryLevel
                }
                print("🔋 Phone battery: \(batteryLevel)%")
            }
        }
        
        handleSignalUpdate(rssi: strength)
    }
    
    func handleSignalUpdate(rssi: Int) {
        DispatchQueue.main.async {
            // 1. Add to history
            self.rssiHistory.append(rssi)
            if self.rssiHistory.count > self.smoothWindow {
                self.rssiHistory.removeFirst()
            }
            
            // 2. Calculate average
            let smoothedRSSI = Int(self.rssiHistory.reduce(0, +) / self.rssiHistory.count)
            
            // Log signal strength for testing
            print("📶 RSSI: \(rssi) dB | Smoothed: \(smoothedRSSI) dB | State: \(self.isUnlocked ? "UNLOCKED" : "LOCKED") | Cooldown: \(self.userDismissedShield)")
            
            // 3. Logic with Hysteresis
            let LOCK_THRESHOLD = -75 // Must drop below this to lock
            let UNLOCK_THRESHOLD = -65 // Must verify above this to unlock
            
            if self.isUnlocked {
                // Currently Unlocked: Look for reason to LOCK
                if smoothedRSSI < LOCK_THRESHOLD {
                    // Only lock if user hasn't dismissed the shield (cooldown not active)
                    if !self.userDismissedShield {
                        let loss = min(max((abs(smoothedRSSI) - abs(LOCK_THRESHOLD)) * 4, 0), 100)
                        self.signalStrengthLoss = loss
                        self.isUnlocked = false
                        print("Phone went far - showing shield")
                    }
                } else {
                    // Phone is close - reset the cooldown so shield can appear next time
                    if self.userDismissedShield {
                        print("Phone came close - resetting cooldown, shield can appear next time")
                        self.userDismissedShield = false
                    }
                    self.signalStrengthLoss = 0
                }
            } else {
                // Currently Locked: Look for reason to UNLOCK
                if smoothedRSSI > UNLOCK_THRESHOLD {
                    self.isUnlocked = true
                    self.signalStrengthLoss = 0
                    // Phone came close - reset cooldown
                    self.userDismissedShield = false
                    print("Phone came close - unlocking and resetting cooldown")
                } else {
                    // Update blur intensity based on how far we are
                    let loss = min(max((abs(smoothedRSSI) - abs(UNLOCK_THRESHOLD)) * 4, 0), 100)
                    self.signalStrengthLoss = loss
                }
            }
        }
    }
}
