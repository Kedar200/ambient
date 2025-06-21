package com.ambient.os

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ambient.os.databinding.ActivityMainBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedImageUri: Uri? = null
    private val preferences by lazy {
        getSharedPreferences("ambient_os_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadIpAddress()

        binding.saveIpButton.setOnClickListener {
            saveIpAddress()
        }

        binding.selectImageButton.setOnClickListener {
            openImageChooser()
        }

        binding.uploadButton.setOnClickListener {
            uploadImage()
        }

        binding.syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleClipboardSync(isChecked)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startClipboardSync()
            } else {
                Toast.makeText(this, "Notification permission is required for clipboard sync.", Toast.LENGTH_LONG).show()
                binding.syncSwitch.isChecked = false
            }
        }

    private fun saveIpAddress() {
        val ipAddress = binding.ipAddressInput.text.toString()
        if (ipAddress.isNotBlank()) {
            preferences.edit().putString("agent_ip_address", ipAddress).apply()
            Toast.makeText(this, "IP Address Saved", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "IP Address cannot be empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadIpAddress() {
        val savedIp = preferences.getString("agent_ip_address", "")
        binding.ipAddressInput.setText(savedIp)
    }

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.previewImageView.setImageURI(uri)
            }
        }
    }

    private fun openImageChooser() {
        Intent(Intent.ACTION_PICK).also {
            it.type = "image/*"
            selectImageLauncher.launch(it)
        }
    }

    private fun uploadImage() {
        val ipAddress = binding.ipAddressInput.text.toString()
        if (ipAddress.isBlank()) {
            Toast.makeText(this, "Please enter the agent IP address", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.uploadButton.isEnabled = false

        val file = getFileFromUri(selectedImageUri!!)
        if (file == null) {
            Toast.makeText(this, "Failed to access file", Toast.LENGTH_SHORT).show()
            binding.uploadButton.isEnabled = true
            return
        }

        val url = "http://$ipAddress:3000/upload"
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val mediaType = contentResolver.getType(selectedImageUri!!)?.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaTypeOrNull()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("Upload", "Failed", e)
                    binding.uploadButton.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Upload successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Upload failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                    binding.uploadButton.isEnabled = true
                }
            }
        })
    }
    
    private fun getFileFromUri(uri: Uri): File? {
        val contentResolver = this.contentResolver
        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: return null

        val file = File(this.cacheDir, fileName)
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return file
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun toggleClipboardSync(enable: Boolean) {
        if (enable) {
            if (binding.ipAddressInput.text.isBlank()) {
                Toast.makeText(this, "Please enter IP address first", Toast.LENGTH_SHORT).show()
                binding.syncSwitch.isChecked = false
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    PackageManager.PERMISSION_GRANTED -> startClipboardSync()
                    else -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                startClipboardSync()
            }
        } else {
            stopClipboardSync()
        }
    }

    private fun startClipboardSync() {
        val intent = Intent(this, ClipboardSyncService::class.java).also {
            it.putExtra("IP_ADDRESS", binding.ipAddressInput.text.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Clipboard sync started", Toast.LENGTH_SHORT).show()
    }

    private fun stopClipboardSync() {
        val intent = Intent(this, ClipboardSyncService::class.java)
        stopService(intent)
        Toast.makeText(this, "Clipboard sync stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        binding.syncSwitch.isChecked = isServiceRunning(ClipboardSyncService::class.java)
    }
} 