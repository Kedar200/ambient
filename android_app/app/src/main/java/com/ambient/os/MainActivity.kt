package com.ambient.os

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ambient.os.ui.AmbientApp
import com.ambient.os.ui.AmbientTheme

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private var proximityBeacon: ProximityBeacon? = null
    private var pendingPhotoWallEnable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)

        // BLE proximity beacon — independent of UI layer, kept as-is.
        proximityBeacon = ProximityBeacon(this)
        requestBleIfNeeded()

        // Notifications permission (required for foreground services on 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_CODE,
            )
        }

        setContent {
            AmbientTheme {
                var clipboardEnabled by remember {
                    mutableStateOf(sharedPreferences.getBoolean("clipboard_sync_enabled", false))
                }
                var photoWallEnabled by remember {
                    mutableStateOf(sharedPreferences.getBoolean("photo_wall_enabled", false))
                }
                AmbientApp(
                    clipboardEnabled = clipboardEnabled,
                    onClipboardToggle = { desired ->
                        clipboardEnabled = desired
                        sharedPreferences.edit().putBoolean("clipboard_sync_enabled", desired).apply()
                        if (desired) startClipboardService() else stopClipboardService()
                    },
                    photoWallEnabled = photoWallEnabled,
                    onPhotoWallToggle = { desired ->
                        if (desired && !ensureMediaPermission()) {
                            pendingPhotoWallEnable = true
                            return@AmbientApp
                        }
                        photoWallEnabled = desired
                        sharedPreferences.edit().putBoolean("photo_wall_enabled", desired).apply()
                        if (desired) startPhotoWatcherService() else stopPhotoWatcherService()
                    },
                )
            }
        }
    }

    private fun requestBleIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
            val needed = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isEmpty()) {
                proximityBeacon?.startAdvertising()
            } else {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), BLE_PERMISSION_CODE)
            }
        } else {
            proximityBeacon?.startAdvertising()
        }
    }

    private fun ensureMediaPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                MEDIA_PERMISSION_CODE,
            )
        }
        return granted
    }

    private fun startClipboardService() {
        val intent = Intent(this, ClipboardSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopClipboardService() {
        stopService(Intent(this, ClipboardSyncService::class.java))
    }

    private fun startPhotoWatcherService() {
        val intent = Intent(this, PhotoWatcherService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopPhotoWatcherService() {
        stopService(Intent(this, PhotoWatcherService::class.java))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            BLE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    proximityBeacon?.startAdvertising()
                } else {
                    Toast.makeText(this, "Bluetooth access required for Proximity Shield", Toast.LENGTH_LONG).show()
                }
            }
            MEDIA_PERMISSION_CODE -> {
                if (pendingPhotoWallEnable && grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    pendingPhotoWallEnable = false
                    sharedPreferences.edit().putBoolean("photo_wall_enabled", true).apply()
                    startPhotoWatcherService()
                    // Note: Compose state in setContent won't update from here; user can toggle again
                    // to re-render if needed. This is a graceful degradation for first-time perms.
                }
            }
            NOTIFICATION_PERMISSION_CODE -> { /* best-effort; services still start, notifications may be silent */ }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        proximityBeacon?.stopAdvertising()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 101
        private const val MEDIA_PERMISSION_CODE = 102
        private const val BLE_PERMISSION_CODE = 103
    }
}
