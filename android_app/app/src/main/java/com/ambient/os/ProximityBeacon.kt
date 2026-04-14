package com.ambient.os

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class ProximityBeacon(private val context: Context) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private var isAdvertising = false

    // TODO: This UUID must match the one in the Mac App
    val SERVICE_UUID: UUID = UUID.fromString("C0FF3300-1234-5678-9ABC-DEF000000000")
    
    // Custom manufacturer ID (use 0xFFFF for testing, register with Bluetooth SIG for production)
    private val MANUFACTURER_ID = 0xFFFF

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            0
        }
    }

    fun startAdvertising() {
        if (adapter == null || !adapter.isEnabled) {
            Log.e("ProximityBeacon", "Bluetooth disabled or not available")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e("ProximityBeacon", "BLE Advertising not supported on this device")
            return
        }

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i("ProximityBeacon", "Beacon broadcasting successfully!")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("ProximityBeacon", "Beacon failed to start: $errorCode")
                isAdvertising = false
            }
        }

        // Start advertising with current battery level
        startAdvertisingWithBattery()
        
        // Schedule periodic updates every 30 seconds to refresh battery level
        schedulePeriodicUpdate()
    }

    private fun startAdvertisingWithBattery() {
        val batteryLevel = getBatteryPercentage()
        Log.i("ProximityBeacon", "Broadcasting with battery level: $batteryLevel%")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // High frequency for responsiveness
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Max range
            .setConnectable(false)
            .build()

        // Include battery level in manufacturer-specific data
        val manufacturerData = byteArrayOf(batteryLevel.toByte())

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Save bytes
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addManufacturerData(MANUFACTURER_ID, manufacturerData)
            .build()

        try {
            // Stop current advertising before restarting with new data
            if (isAdvertising) {
                advertiser?.stopAdvertising(callback)
            }
            advertiser?.startAdvertising(settings, data, callback)
            isAdvertising = true
            Log.i("ProximityBeacon", "Started advertising service: $SERVICE_UUID with battery: $batteryLevel%")
        } catch (e: SecurityException) {
            Log.e("ProximityBeacon", "Permission denied: ${e.message}")
        }
    }

    private fun schedulePeriodicUpdate() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isAdvertising) {
                    startAdvertisingWithBattery()
                    handler.postDelayed(this, 30_000) // Update every 30 seconds
                }
            }
        }, 30_000)
    }

    fun stopAdvertising() {
        try {
            isAdvertising = false
            handler.removeCallbacksAndMessages(null)
            advertiser?.stopAdvertising(callback)
            Log.i("ProximityBeacon", "Stopped advertising")
        } catch (e: SecurityException) {
            Log.e("ProximityBeacon", "Permission denied: ${e.message}")
        }
    }
}
