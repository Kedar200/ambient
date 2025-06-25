package com.ambient.os

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickShareTileService : TileService() {

    override fun onClick() {
        super.onClick()
        
        // Check if IP is set
        val sharedPreferences = getSharedPreferences("ambient_os_prefs", Context.MODE_PRIVATE)
        val ipAddress = sharedPreferences.getString("ip_address", "")
        
        if (ipAddress.isNullOrEmpty()) {
            // If IP address is not set, open the main activity
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            Toast.makeText(this, "Please set your server IP address first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get the most recent image
        val latestImageUri = getLatestImageUri()
        
        if (latestImageUri != null) {
            // Share the image using our silent activity
            val shareIntent = Intent(this, SilentShareActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, latestImageUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            
            // Use the modern API for newer Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startActivityAndCollapse(shareIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(shareIntent)
            }
            
            // Show a toast directly from the service
            Toast.makeText(this, "Sharing latest image...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No recent images found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        
        // Check if IP is set to determine if the tile should be active
        val sharedPreferences = getSharedPreferences("ambient_os_prefs", Context.MODE_PRIVATE)
        val ipAddress = sharedPreferences.getString("ip_address", "")
        
        qsTile?.state = if (ipAddress.isNullOrEmpty()) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }
        qsTile?.updateTile()
    }
    
    private fun getLatestImageUri(): Uri? {
        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DATE_TAKEN
        )
        
        val sortOrder = "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC"
        
        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                contentUri,
                projection,
                null,
                null,
                sortOrder
            )
            
            if (cursor != null && cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID)
                val id = cursor.getLong(idColumn)
                return ContentUris.withAppendedId(contentUri, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        
        return null
    }

}