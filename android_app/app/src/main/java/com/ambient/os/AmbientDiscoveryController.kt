package com.ambient.os

import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lifecycle-aware coordinator that automatically discovers the Ambient OS
 * desktop agent via mDNS and keeps [ConnectionStateHolder] in sync. No UI
 * interaction required — pairing happens invisibly the instant the app is
 * foregrounded on a Wi-Fi network where the agent is advertising.
 *
 * Writes the resolved `host:port` into the legacy `ambient_prefs` /
 * `ip_address` SharedPreferences key so existing services
 * ([ClipboardSyncService], [PhotoWatcherService], [SilentShareActivity])
 * continue to work unchanged — they already re-read this pref on every tick.
 */
class AmbientDiscoveryController(
    private val appContext: Context,
) : DefaultLifecycleObserver {

    private val discoveryManager = DeviceDiscoveryManager(appContext)
    private val prefs = appContext.getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)
    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null
    private var healthJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private var attempt = 0
    @Volatile private var foundThisCycle = false
    @Volatile private var lastConnectedHost: String? = null
    @Volatile private var lastConnectedPort: Int = 0

    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        acquireMulticastLock()
        // Seed UI immediately from persisted state if we have one.
        val savedIp = prefs.getString("ip_address", "")
        val savedName = prefs.getString("last_device_name", null)
        if (!savedIp.isNullOrEmpty() && savedName != null) {
            val (h, p) = parseHostPort(savedIp)
            if (h != null) {
                lastConnectedHost = h
                lastConnectedPort = p
                ConnectionStateHolder.update(
                    ConnectionState.Connecting(savedName)
                )
            }
        } else {
            ConnectionStateHolder.update(ConnectionState.Searching(attempt = 1))
        }
        startDiscoveryLoop()
        startHealthLoop()
    }

    override fun onStop(owner: LifecycleOwner) {
        loopJob?.cancel()
        loopJob = null
        healthJob?.cancel()
        healthJob = null
        runCatching { discoveryManager.stopDiscovery() }
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager?.createMulticastLock(TAG)?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun startDiscoveryLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                foundThisCycle = false
                attempt += 1
                val currentState = ConnectionStateHolder.current()
                if (currentState !is ConnectionState.Connected) {
                    ConnectionStateHolder.update(ConnectionState.Searching(attempt))
                }

                runCatching { discoveryManager.stopDiscovery() }
                discoveryManager.startDiscovery(callback)

                // Let the scan run up to 6s before backing off.
                delay(6_000)
                runCatching { discoveryManager.stopDiscovery() }

                if (foundThisCycle) {
                    // Connected — idle until health loop tells us otherwise.
                    while (isActive && ConnectionStateHolder.current() is ConnectionState.Connected) {
                        delay(2_000)
                    }
                    attempt = 0
                } else {
                    // Exponential backoff 1s → 30s
                    val backoff = (1L shl (attempt.coerceAtMost(5))) * 1_000L
                    delay(backoff.coerceAtMost(30_000))
                }
            }
        }
    }

    private fun startHealthLoop() {
        if (healthJob?.isActive == true) return
        healthJob = scope.launch {
            while (isActive) {
                delay(10_000)
                val state = ConnectionStateHolder.current()
                if (state is ConnectionState.Connected) {
                    val ok = ping(state.host, state.port)
                    if (!ok) {
                        Log.w(TAG, "Health check failed for ${state.host}:${state.port}")
                        ConnectionStateHolder.update(ConnectionState.Searching(attempt = 1))
                    }
                }
            }
        }
    }

    private fun ping(host: String, port: Int): Boolean {
        return try {
            val req = Request.Builder().url("http://$host:$port/").get().build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (t: Throwable) {
            false
        }
    }

    private val callback = object : DeviceDiscoveryManager.DiscoveryCallback {
        override fun onDeviceFound(hostAddress: String, port: Int, deviceName: String) {
            foundThisCycle = true
            val address = "$hostAddress:$port"
            prefs.edit()
                .putString("ip_address", address)
                .putString("last_device_name", deviceName)
                .putString("last_device_host", hostAddress)
                .putInt("last_device_port", port)
                .apply()

            // Preserve battery/version if we read them from TXT in
            // onServiceResolved just before this callback.
            val existing = ConnectionStateHolder.current() as? ConnectionState.Connected
            lastConnectedHost = hostAddress
            lastConnectedPort = port
            ConnectionStateHolder.update(
                ConnectionState.Connected(
                    name = deviceName,
                    host = hostAddress,
                    port = port,
                    battery = pendingBattery ?: existing?.battery,
                    version = pendingVersion ?: existing?.version,
                )
            )
            pendingBattery = null
            pendingVersion = null
        }

        override fun onDiscoveryStarted() {
            Log.d(TAG, "mDNS discovery started")
        }

        override fun onDiscoveryStopped() {
            Log.d(TAG, "mDNS discovery stopped")
        }

        override fun onError(message: String) {
            Log.w(TAG, "Discovery error: $message")
        }

        override fun onServiceResolved(info: NsdServiceInfo) {
            val attrs = runCatching { info.attributes }.getOrNull() ?: return
            pendingBattery = attrs["battery"]?.toString(Charsets.UTF_8)?.toIntOrNull()
            pendingVersion = attrs["version"]?.toString(Charsets.UTF_8)
        }
    }

    private var pendingBattery: Int? = null
    private var pendingVersion: String? = null

    private fun parseHostPort(raw: String): Pair<String?, Int> {
        val parts = raw.split(":")
        return if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 23921)
        else raw to 23921
    }

    companion object {
        private const val TAG = "AmbientDiscovery"
    }
}
