package com.ambient.os

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ClipboardSyncService : Service() {

    private val client = OkHttpClient()
    private lateinit var job: Job
    private var lastClipboardContent: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ipAddress = intent?.getStringExtra("IP_ADDRESS") ?: return START_NOT_STICKY
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        lastClipboardContent = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()

        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                // Sync from server to local
                syncFromServer(ipAddress, clipboardManager)

                // Sync from local to server
                syncToServer(ipAddress, clipboardManager)

                delay(2000) // Poll every 2 seconds
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun syncFromServer(ipAddress: String, clipboardManager: ClipboardManager) {
        val request = Request.Builder().url("http://$ipAddress:3000/clipboard").build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Simple JSON parsing
                    val serverContent = responseBody?.substringAfter("\"content\":\"")?.substringBeforeLast("\"")
                    
                    if (serverContent != null && serverContent != lastClipboardContent) {
                        val clip = android.content.ClipData.newPlainText("synced-clipboard", serverContent)
                        clipboardManager.setPrimaryClip(clip)
                        lastClipboardContent = serverContent
                        Log.d("ClipboardSync", "Synced from server: $serverContent")
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("ClipboardSync", "Failed to sync from server", e)
        }
    }

    private fun syncToServer(ipAddress: String, clipboardManager: ClipboardManager) {
        val currentClip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
        if (currentClip != null && currentClip != lastClipboardContent) {
            lastClipboardContent = currentClip
            val json = "{\"content\":\"$currentClip\"}"
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder().url("http://$ipAddress:3000/clipboard").post(body).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ClipboardSync", "Failed to sync to server", e)
                }
                override fun onResponse(call: Call, response: Response) {
                    if(response.isSuccessful) {
                        Log.d("ClipboardSync", "Synced to server: $currentClip")
                    }
                }
            })
        }
    }
}
