package com.ambient.os

import android.app.Application

class AmbientApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Process-wide, lifecycle-aware mDNS auto-pair. Kicks in whenever any
        // UI component is foregrounded; idles otherwise.
        AmbientDiscoveryController(this).attach()
    }
}
