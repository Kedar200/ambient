package com.ambient.os

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClipboardSyncService : Service() {
    private lateinit var clipboardManager: ClipboardManager
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private var syncJob: Job? = null
    private var lastSyncedText: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val syncInterval = 2000L // 2 seconds
    
    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startClipboardSync()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stopClipboardSync()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your clipboard in sync with your computer"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ambient OS")
            .setContentText("Clipboard sync is active")
            .setSmallIcon(R.drawable.ic_clipboard)
            .setColor(resources.getColor(R.color.primary, theme))
            .setColorized(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startClipboardSync() {
        if (syncJob != null) return
        
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                checkClipboard()
                checkServerClipboard()
                delay(syncInterval)
            }
        }
    }
    
    private fun stopClipboardSync() {
        syncJob?.cancel()
        syncJob = null
    }
    
    private fun checkClipboard() {
        if (!clipboardManager.hasPrimaryClip()) return
        
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount <= 0) return
        
        val text = clipData.getItemAt(0).text?.toString() ?: return
        if (text == lastSyncedText) return
        
        val ipAddress = getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)
            .getFormattedIpAddress()
        if (ipAddress.isEmpty()) return
        
        try {
            val json = JSONObject().put("content", text).toString()
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("http://$ipAddress/clipboard")
                .post(body)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    lastSyncedText = text
                    Log.d(TAG, "Clipboard synced to server")
                } else {
                    Log.e(TAG, "Failed to sync clipboard: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing clipboard", e)
        }
    }
    
    private fun checkServerClipboard() {
        val ipAddress = getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)
            .getFormattedIpAddress()
        if (ipAddress.isEmpty()) return
        
        try {
            val request = Request.Builder()
                .url("http://$ipAddress/clipboard")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return
                    val jsonObject = JSONObject(responseBody)
                    val content = jsonObject.optString("content", "")
                    
                    if (content.isNotEmpty() && content != lastSyncedText) {
                        handler.post {
                            updateClipboard(content)
                        }
                        lastSyncedText = content
                        Log.d(TAG, "Clipboard updated from server")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server clipboard", e)
        }
    }
    
    private fun updateClipboard(text: String) {
        val clipData = android.content.ClipData.newPlainText("Ambient OS Clipboard", text)
        clipboardManager.setPrimaryClip(clipData)
    }
    
    companion object {
        private const val CHANNEL_ID = "clipboard_sync_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ClipboardSyncService"
    }
}
