package com.ambient.os

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * A completely invisible activity that handles image sharing without any UI.
 * It uploads the image and then finishes itself immediately.
 */
class SilentShareActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val TAG = "SilentShareActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            handleSharedImage(intent)
        } else {
            finish()
        }
    }

    private fun handleSharedImage(intent: Intent) {
        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (imageUri == null) {
            showCustomToast("No image found", false)
            finish()
            return
        }
        
        val ipAddress = getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)
            .getFormattedIpAddress()
        
        if (ipAddress.isEmpty()) {
            showCustomToast("Server address not configured", false)
            // Open MainActivity to configure IP
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        
        showCustomToast("Uploading image...", true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = createTempFileFromUri(imageUri)
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
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            showCustomToast("Image uploaded successfully!", true)
                        } else {
                            showCustomToast("Upload failed: ${response.message}", false)
                        }
                        finish()
                    }
                }
                
                file.delete() // Clean up temp file
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                withContext(Dispatchers.Main) {
                    showCustomToast("Error: ${e.message}", false)
                    finish()
                }
            }
        }
    }

    private fun createTempFileFromUri(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val fileName = getFileName(uri)
        val file = File(cacheDir, fileName)
        
        FileOutputStream(file).use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
        
        return file
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "image.jpg"
        
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex("_display_name")
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        
        return fileName
    }
    
    private fun showCustomToast(message: String, isSuccess: Boolean) {
        try {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val layout = inflater.inflate(R.layout.custom_toast, null)
            
            val textView = layout.findViewById<TextView>(R.id.toast_text)
            textView.text = message
            
            if (isSuccess) {
                layout.setBackgroundResource(R.drawable.toast_success_background)
            } else {
                layout.setBackgroundResource(R.drawable.toast_error_background)
            }
            
            val toast = Toast(applicationContext)
            toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 150)
            toast.duration = Toast.LENGTH_SHORT
            toast.view = layout
            toast.show()
        } catch (e: Exception) {
            // Fallback to regular toast if custom toast fails
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
} 