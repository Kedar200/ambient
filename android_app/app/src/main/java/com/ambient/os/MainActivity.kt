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
                binding.previewImageView.visibility = android.view.View.VISIBLE
                binding.emptyStateLayout.visibility = android.view.View.GONE
            }
        }
    }

    private var proximityBeacon: ProximityBeacon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("ambient_prefs", Context.MODE_PRIVATE)
        
        // Start Proximity Beacon (BLE Advertising)
        proximityBeacon = ProximityBeacon(this)
        
        // Check Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val neededPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            
            if (neededPermissions.isEmpty()) {
                proximityBeacon?.startAdvertising()
            } else {
                ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), BLE_PERMISSION_CODE)
            }
        } else {
            // Older Android versions
            proximityBeacon?.startAdvertising()
        }
        
        // Load saved IP address
        val savedIp = sharedPreferences.getString("ip_address", "")
        binding.ipAddressInput.setText(savedIp)

        // Update connection status based on saved IP
        updateConnectionStatus(!savedIp.isNullOrEmpty())

        // Setup clipboard sync switch
        val isSyncEnabled = sharedPreferences.getBoolean("clipboard_sync_enabled", false)
        binding.syncSwitch.isChecked = isSyncEnabled
        updateSyncStatus(isSyncEnabled)
        
        // Setup photo wall switch
        val isPhotoWallEnabled = sharedPreferences.getBoolean("photo_wall_enabled", false)
        binding.photoWallSwitch.isChecked = isPhotoWallEnabled
        updatePhotoWallStatus(isPhotoWallEnabled)
        
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
                updateConnectionStatus(true)
                Toast.makeText(this, R.string.server_address_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.enter_valid_ip, Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.autoDiscoverButton.setOnClickListener {
            startDeviceDiscovery()
        }
        
        binding.selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }
        
        binding.uploadButton.setOnClickListener {
            selectedImageUri?.let { uri ->
                uploadImage(uri)
            } ?: Toast.makeText(this, R.string.select_image_first, Toast.LENGTH_SHORT).show()
        }
        
        binding.syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("clipboard_sync_enabled", isChecked).apply()
            updateSyncStatus(isChecked)

            if (isChecked) {
                startClipboardService()
            } else {
                stopClipboardService()
            }
        }
        
        binding.photoWallSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check for READ_MEDIA_IMAGES permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                            MEDIA_PERMISSION_CODE
                        )
                        // Will start service in onRequestPermissionsResult
                        return@setOnCheckedChangeListener
                    }
                }
                sharedPreferences.edit().putBoolean("photo_wall_enabled", true).apply()
                updatePhotoWallStatus(true)
                startPhotoWatcherService()
            } else {
                sharedPreferences.edit().putBoolean("photo_wall_enabled", false).apply()
                updatePhotoWallStatus(false)
                stopPhotoWatcherService()
            }
        }
    }
    
    private var discoveryManager: DeviceDiscoveryManager? = null
    
    private fun startDeviceDiscovery() {
        // Update button to show scanning state
        binding.autoDiscoverButton.isEnabled = false
        binding.autoDiscoverButton.text = getString(R.string.scanning)
        
        if (discoveryManager == null) {
            discoveryManager = DeviceDiscoveryManager(this)
        }
        
        discoveryManager?.startDiscovery(object : DeviceDiscoveryManager.DiscoveryCallback {
            override fun onDeviceFound(hostAddress: String, port: Int, deviceName: String) {
                runOnUiThread {
                    // Auto-fill the IP address
                    val address = "$hostAddress:$port"
                    binding.ipAddressInput.setText(address)
                    
                    // Save it automatically
                    sharedPreferences.edit().putString("ip_address", address).apply()
                    updateConnectionStatus(true)
                    
                    Toast.makeText(
                        this@MainActivity, 
                        getString(R.string.device_found, deviceName), 
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Stop discovery after finding a device
                    discoveryManager?.stopDiscovery()
                    resetDiscoveryButton()
                }
            }
            
            override fun onDiscoveryStarted() {
                // Already showing scanning state
            }
            
            override fun onDiscoveryStopped() {
                runOnUiThread {
                    resetDiscoveryButton()
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity, 
                        getString(R.string.discovery_error, message), 
                        Toast.LENGTH_SHORT
                    ).show()
                    resetDiscoveryButton()
                }
            }
        })
        
        // Stop discovery after 10 seconds if no device found
        android.os.Handler(mainLooper).postDelayed({
            if (discoveryManager?.isDiscovering() == true) {
                discoveryManager?.stopDiscovery()
                runOnUiThread {
                    Toast.makeText(this, R.string.no_device_found, Toast.LENGTH_SHORT).show()
                    resetDiscoveryButton()
                }
            }
        }, 10000)
    }
    
    private fun resetDiscoveryButton() {
        binding.autoDiscoverButton.isEnabled = true
        binding.autoDiscoverButton.text = getString(R.string.auto_discover)
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
    
    private fun startPhotoWatcherService() {
        val serviceIntent = Intent(this, PhotoWatcherService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    private fun stopPhotoWatcherService() {
        val serviceIntent = Intent(this, PhotoWatcherService::class.java)
        stopService(serviceIntent)
    }
    
    private fun updatePhotoWallStatus(isEnabled: Boolean) {
        if (isEnabled) {
            binding.photoWallStatusText.text = getString(R.string.photo_wall_active)
            binding.photoWallStatusText.setTextColor(getColor(R.color.success))
        } else {
            binding.photoWallStatusText.text = getString(R.string.photo_wall_inactive)
            binding.photoWallStatusText.setTextColor(getColor(R.color.text_tertiary))
        }
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
            Toast.makeText(this, R.string.set_server_ip_first, Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        binding.uploadButton.isEnabled = false
        binding.uploadButton.text = getString(R.string.uploading)

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
                            Toast.makeText(this@MainActivity, R.string.upload_successful, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.upload_failed, response.message), Toast.LENGTH_SHORT).show()
                        }
                        // Reset button state
                        binding.uploadButton.isEnabled = true
                        binding.uploadButton.text = getString(R.string.upload_image)
                    }
                }

                file.delete() // Clean up temp file
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_occurred, e.message), Toast.LENGTH_SHORT).show()
                    // Reset button state
                    binding.uploadButton.isEnabled = true
                    binding.uploadButton.text = getString(R.string.upload_image)
                }
            }
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            binding.connectionStatusChip.text = getString(R.string.status_connected)
            binding.connectionStatusChip.setChipBackgroundColorResource(R.color.success_container)
            binding.connectionStatusChip.setTextColor(getColor(R.color.success))
        } else {
            binding.connectionStatusChip.text = getString(R.string.status_disconnected)
            binding.connectionStatusChip.setChipBackgroundColorResource(R.color.surface_variant)
            binding.connectionStatusChip.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun updateSyncStatus(isEnabled: Boolean) {
        if (isEnabled) {
            binding.syncStatusText.text = getString(R.string.sync_active)
            binding.syncStatusText.setTextColor(getColor(R.color.success))
        } else {
            binding.syncStatusText.text = getString(R.string.sync_inactive)
            binding.syncStatusText.setTextColor(getColor(R.color.text_tertiary))
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
                Toast.makeText(this, R.string.notification_permission_required, Toast.LENGTH_LONG).show()
                binding.syncSwitch.isChecked = false
                updateSyncStatus(false)
            }
        } else if (requestCode == MEDIA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sharedPreferences.edit().putBoolean("photo_wall_enabled", true).apply()
                updatePhotoWallStatus(true)
                startPhotoWatcherService()
                binding.photoWallSwitch.isChecked = false
                updatePhotoWallStatus(false)
            }
        } else if (requestCode == BLE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                proximityBeacon?.startAdvertising()
            } else {
                Toast.makeText(this, "Bluetooth access required for Proximity Shield", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 101
        private const val MEDIA_PERMISSION_CODE = 102
        private const val BLE_PERMISSION_CODE = 103
    }

    override fun onDestroy() {
        super.onDestroy()
        proximityBeacon?.stopAdvertising()
    }
} 