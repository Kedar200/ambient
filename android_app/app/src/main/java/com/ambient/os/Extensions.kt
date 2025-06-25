package com.ambient.os

import android.content.SharedPreferences

fun SharedPreferences.getFormattedIpAddress(): String {
    val rawIpAddress = getString("ip_address", "") ?: ""
    if (rawIpAddress.isEmpty()) return ""
    return if (rawIpAddress.contains(":")) rawIpAddress else "$rawIpAddress:23921"
} 