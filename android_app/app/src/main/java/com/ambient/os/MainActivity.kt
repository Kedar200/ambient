package com.ambient.os

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ambient.os.databinding.ActivityMainBinding
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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var selectedImageUri: Uri? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.previewImageView.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)

        sharedPreferences = getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)
        
        // Load saved IP address
        val savedIp = sharedPreferences.getString("ip_address", "")
        binding.ipAddressInput.setText(savedIp)
        
        // Setup clipboard sync switch
        val isSyncEnabled = sharedPreferences.getBoolean("clipboard_sync_enabled", false)
        binding.syncSwitch.isChecked = isSyncEnabled
        
        // Setup UI interactions
        setupClickListeners()
        
        // Check for clipboard permissions
        checkClipboardPermissions()
    }
    
    private fun setupClickListeners() {
        binding.saveIpButton.setOnClickListener {
            val ipAddress = binding.ipAddressInput.text.toString().trim()
            if (ipAddress.isNotEmpty()) {
                sharedPreferences.edit().putString("ip_address", ipAddress).apply()
                Toast.makeText(this, "Server address saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }
        
        binding.uploadButton.setOnClickListener {
            selectedImageUri?.let { uri ->
                uploadImage(uri)
            } ?: Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
        }
        
        binding.syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("clipboard_sync_enabled", isChecked).apply()
            
            if (isChecked) {
                startClipboardService()
            } else {
                stopClipboardService()
            }
        }
    }
    
    private fun startClipboardService() {
        val serviceIntent = Intent(this, ClipboardSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    private fun stopClipboardService() {
        val serviceIntent = Intent(this, ClipboardSyncService::class.java)
        stopService(serviceIntent)
    }
    
    private fun checkClipboardPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            } else if (binding.syncSwitch.isChecked) {
                startClipboardService()
            }
        } else if (binding.syncSwitch.isChecked) {
            startClipboardService()
        }
    }

    private fun uploadImage(uri: Uri) {
        val ipAddress = sharedPreferences.getFormattedIpAddress()
        if (ipAddress.isEmpty()) {
            Toast.makeText(this, "Please set server IP address first", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        binding.uploadButton.isEnabled = false
        binding.uploadButton.text = "Uploading..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = createTempFileFromUri(uri)
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
                            Toast.makeText(this@MainActivity, "Upload successful!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Upload failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                        // Reset button state
                        binding.uploadButton.isEnabled = true
                        binding.uploadButton.text = "Upload"
                    }
                }

                file.delete() // Clean up temp file
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Reset button state
                    binding.uploadButton.isEnabled = true
                    binding.uploadButton.text = "Upload"
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
                val displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        
        return fileName
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (binding.syncSwitch.isChecked) {
                    startClipboardService()
                }
            } else {
                Toast.makeText(this, "Notification permission is required for clipboard sync", Toast.LENGTH_LONG).show()
                binding.syncSwitch.isChecked = false
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 101
    }
} 