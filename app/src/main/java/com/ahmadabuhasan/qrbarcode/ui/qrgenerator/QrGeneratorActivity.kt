package com.ahmadabuhasan.qrbarcode.ui.qrgenerator

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.content.FileProvider
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityQrGeneratorBinding
import com.ahmadabuhasan.qrbarcode.utils.AppConfig
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.ahmadabuhasan.qrbarcode.utils.ConsentManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.io.File
import java.io.FileOutputStream

class QrGeneratorActivity : BaseActivity() {

    private val viewModel: QrGeneratorViewModel by viewModels()
    private lateinit var binding: ActivityQrGeneratorBinding

    private val requestWritePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveQrToGallery()
        else Toast.makeText(this, R.string.qr_generator_permission_denied, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrGeneratorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.qr_generator)
        }

        ConsentManager.gatherConsent(this) { showBanner() }
        configureTypes()

        binding.btnGenerate.setOnClickListener { generateQr() }
        binding.btnShare.setOnClickListener {
            viewModel.qrBitmap.value?.let(::shareQrImage)
        }
        binding.btnSave.setOnClickListener { requestSaveQr() }
        binding.groupWifiSecurity.setOnCheckedChangeListener { _, checkedId ->
            binding.wifiPasswordLayout.isVisible = checkedId != R.id.radioWifiNone
        }

        observeViewModel()
    }

    private fun configureTypes() {
        binding.toggleQrType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.textFields.isVisible = checkedId == R.id.btnTypeText
            binding.wifiFields.isVisible = checkedId == R.id.btnTypeWifi
            binding.whatsappFields.isVisible = checkedId == R.id.btnTypeWhatsapp
        }
    }

    private fun generateQr() {
        binding.imgQr.isVisible = false
        binding.qrActions.isVisible = false
        val content = when (binding.toggleQrType.checkedButtonId) {
            R.id.btnTypeWifi -> wifiPayload()
            R.id.btnTypeWhatsapp -> whatsappPayload()
            else -> binding.editContent.text?.toString()?.trim().orEmpty()
        }
        if (content == null) return
        if (content.isBlank()) {
            Toast.makeText(this, R.string.qr_generator_empty, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.generate(content)
    }

    private fun wifiPayload(): String? {
        val ssid = binding.editWifiSsid.text?.toString().orEmpty()
        if (ssid.isBlank()) {
            Toast.makeText(this, R.string.qr_generator_wifi_ssid_required, Toast.LENGTH_SHORT).show()
            return null
        }

        val security = when (binding.groupWifiSecurity.checkedRadioButtonId) {
            R.id.radioWifiWep -> "WEP"
            R.id.radioWifiNone -> "nopass"
            else -> "WPA"
        }
        val escapedSsid = escapeWifiField(ssid)
        if (security == "nopass") return "WIFI:T:nopass;S:$escapedSsid;;"

        val password = binding.editWifiPassword.text?.toString().orEmpty()
        if (password.isEmpty()) {
            Toast.makeText(this, R.string.qr_generator_wifi_password_required, Toast.LENGTH_SHORT).show()
            return null
        }
        val escapedPassword = escapeWifiField(password)
        return "WIFI:T:$security;S:$escapedSsid;P:$escapedPassword;;"
    }

    private fun escapeWifiField(value: String): String = buildString {
        value.forEach { char ->
            if (char in charArrayOf('\\', ';', ',', ':', '"')) append('\\')
            append(char)
        }
    }

    private fun whatsappPayload(): String? {
        val phone = binding.editWhatsappPhone.text?.toString().orEmpty().filter(Char::isDigit)
        if (phone.length !in 8..15) {
            Toast.makeText(this, R.string.qr_generator_whatsapp_phone_invalid, Toast.LENGTH_SHORT).show()
            return null
        }
        val message = binding.editWhatsappMessage.text?.toString()?.trim().orEmpty()
        return Uri.Builder()
            .scheme("https")
            .authority("wa.me")
            .appendPath(phone)
            .apply { if (message.isNotEmpty()) appendQueryParameter("text", message) }
            .build()
            .toString()
    }

    private fun showBanner() {
        val container = binding.adViewQrGeneratorContainer
        if (container.childCount > 0) return
        val adView = AdView(this).apply {
            adUnitId = AppConfig.bannerAdId()
            setAdSize(AdSize.BANNER)
        }
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun observeViewModel() {
        viewModel.qrBitmap.observe(this) { bitmap ->
            bitmap ?: return@observe
            binding.imgQr.setImageBitmap(bitmap)
            binding.imgQr.isVisible = true
            binding.qrActions.isVisible = true
        }

        viewModel.error.observe(this) { error ->
            error ?: return@observe
            Toast.makeText(this, R.string.qr_generator_error, Toast.LENGTH_SHORT).show()
            viewModel.onErrorShown()
        }
    }

    private fun requestSaveQr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveQrToGallery()
    }

    private fun saveQrToGallery() {
        val bitmap = viewModel.qrBitmap.value ?: return
        var imageUri: Uri? = null
        try {
            val fileName = "qr_code_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QRBarcode")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    putLegacyImagePath(this, fileName)
                }
            }
            val createdUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create gallery image")
            imageUri = createdUri
            val output = contentResolver.openOutputStream(createdUri) ?: error("Could not open gallery image")
            output.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) error("Could not save QR image")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(createdUri, ready, null, null)
            }
            Toast.makeText(this, R.string.qr_generator_saved, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            imageUri?.let { contentResolver.delete(it, null, null) }
            Toast.makeText(this, R.string.qr_generator_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun putLegacyImagePath(values: ContentValues, fileName: String) {
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "QRBarcode"
        )
        if (!picturesDir.exists() && !picturesDir.mkdirs()) error("Cannot create Pictures/QRBarcode")
        values.put(MediaStore.Images.Media.DATA, File(picturesDir, fileName).absolutePath)
    }

    private fun shareQrImage(bitmap: Bitmap) {
        val qrDir = File(cacheDir, "qr_codes").also { it.mkdirs() }
        val file = File(qrDir, "qr_code.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.qr_generator_share)))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
