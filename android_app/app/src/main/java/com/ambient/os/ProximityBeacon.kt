package com.ambient.os

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class ProximityBeacon(private val context: Context) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // TODO: This UUID must match the one in the Mac App
    val SERVICE_UUID: UUID = UUID.fromString("C0FF3300-1234-5678-9ABC-DEF000000000")

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

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // High frequency for responsiveness
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // Max range
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Save bytes
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i("ProximityBeacon", "Beacon broadcasting successfully!")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("ProximityBeacon", "Beacon failed to start: $errorCode")
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, callback)
            Log.i("ProximityBeacon", "Started advertising service: $SERVICE_UUID")
        } catch (e: SecurityException) {
            Log.e("ProximityBeacon", "Permission denied: ${e.message}")
        }
    }

    fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(callback)
            Log.i("ProximityBeacon", "Stopped advertising")
        } catch (e: SecurityException) {
            Log.e("ProximityBeacon", "Permission denied: ${e.message}")
        }
    }
}
