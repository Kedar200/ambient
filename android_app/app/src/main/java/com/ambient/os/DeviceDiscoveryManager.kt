package com.ambient.os

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Manages automatic discovery of Ambient OS desktop agents on the local network
 * using Android's Network Service Discovery (NSD) / mDNS.
 */
class DeviceDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    companion object {
        private const val TAG = "DeviceDiscoveryManager"
        private const val SERVICE_TYPE = "_ambient._tcp."
    }

    interface DiscoveryCallback {
        fun onDeviceFound(hostAddress: String, port: Int, deviceName: String)
        fun onDiscoveryStarted()
        fun onDiscoveryStopped()
        fun onError(message: String)

        /**
         * Called after the raw NsdServiceInfo has been resolved. Default no-op so
         * existing callers keep working. The controller overrides this to read
         * TXT record attributes (name, battery, version).
         */
        fun onServiceResolved(info: NsdServiceInfo) {}
    }

    fun startDiscovery(callback: DiscoveryCallback) {
        if (isDiscovering) {
            Log.w(TAG, "Discovery already in progress")
            return
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started for $serviceType")
                isDiscovering = true
                callback.onDiscoveryStarted()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                // Resolve the service to get IP and port
                resolveService(serviceInfo, callback)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped for $serviceType")
                isDiscovering = false
                callback.onDiscoveryStopped()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                isDiscovering = false
                callback.onError("Failed to start discovery (error $errorCode)")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            callback.onError("Failed to start discovery: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, callback: DiscoveryCallback) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host = resolvedInfo.host?.hostAddress
                val port = resolvedInfo.port
                val name = resolvedInfo.serviceName

                Log.d(TAG, "Service resolved: $name at $host:$port")

                // Notify controller with the full NsdServiceInfo first (so it
                // can read TXT attributes like battery/version), then the
                // simpler onDeviceFound for legacy callers.
                callback.onServiceResolved(resolvedInfo)
                if (host != null) {
                    callback.onDeviceFound(host, port, name)
                }
            }
        })
    }

    fun stopDiscovery() {
        if (!isDiscovering || discoveryListener == null) {
            return
        }

        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        }
        isDiscovering = false
        discoveryListener = null
    }

    fun isDiscovering(): Boolean = isDiscovering
}
