package com.ambient.os

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Service that watches for new photos/screenshots and automatically uploads them to the Mac.
 * This creates a "Live Photo Wall" experience where photos appear on your Mac instantly.
 */
class PhotoWatcherService : Service() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var photoObserver: ContentObserver? = null
    private var lastProcessedId: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "PhotoWatcherService"
        private const val CHANNEL_ID = "photo_watcher_channel"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startWatchingPhotos()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWatchingPhotos()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Photo Wall",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Watches for new photos to send to your Mac"
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
            .setContentTitle("Live Photo Wall")
            .setContentText("Photos will appear on your Mac")
            .setSmallIcon(R.drawable.ic_image)
            .setColor(resources.getColor(R.color.primary, theme))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startWatchingPhotos() {
        // Get the last photo ID to avoid uploading old photos
        lastProcessedId = getLatestPhotoId()
        
        photoObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "Photo change detected: $uri")
                
                // Add a small delay to ensure the media scanner has finished
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    checkForNewPhotos()
                }, 1000) // 1 second delay
            }
        }

        // Watch for new photos in the gallery
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            photoObserver!!
        )

        Log.d(TAG, "Started watching for photos. Last ID: $lastProcessedId")
        Log.d(TAG, "Server IP: ${getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE).getFormattedIpAddress()}")
    }

    private fun stopWatchingPhotos() {
        photoObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        photoObserver = null
    }

    private fun getLatestPhotoId(): Long {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            }
        }
        return 0
    }

    private fun checkForNewPhotos() {
        Log.d(TAG, "Checking for new photos after ID: $lastProcessedId")
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media._ID} > ?"
        val selectionArgs = arrayOf(lastProcessedId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            Log.d(TAG, "Query returned ${cursor.count} photos")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                
                Log.d(TAG, "New photo found: $name (ID: $id)")
                
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                
                uploadPhoto(uri, name)
                lastProcessedId = id
            }
        } ?: Log.e(TAG, "Query returned null!")
    }

    private fun uploadPhoto(uri: Uri, fileName: String) {
        val ipAddress = getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)
            .getFormattedIpAddress()

        if (ipAddress.isEmpty()) {
            Log.w(TAG, "Server not configured, skipping upload")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = createTempFileFromUri(uri, fileName)
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody("image/*".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url("http://$ipAddress/upload")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Photo uploaded successfully: $fileName")
                    } else {
                        Log.e(TAG, "Upload failed: ${response.message}")
                    }
                }

                file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading photo", e)
            }
        }
    }

    private fun createTempFileFromUri(uri: Uri, fileName: String): File {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { outputStream ->
            inputStream?.copyTo(outputStream)
        }

        return file
    }
}
