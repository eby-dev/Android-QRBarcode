package com.ahmadabuhasan.qrbarcode.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityMainBinding
import com.ahmadabuhasan.qrbarcode.utils.AppConfig
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.ahmadabuhasan.qrbarcode.utils.ConsentManager
import com.ahmadabuhasan.qrbarcode.utils.Haptics
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

// The scanner. Opened from the dashboard (HomeActivity), or straight from launch
// when "Open camera on launch" is on; Back returns to the dashboard.
class MainActivity : BaseActivity(), ScanResultBottomSheet.Listener {

    companion object {
        private const val PERMISSION_CODE = 100
    }

    // ViewModel — semua state & logic bisnis ada di sini
    private val viewModel: MainViewModel by viewModels()

    private lateinit var cameraController: LifecycleCameraController
    private var barcodeScanner: BarcodeScanner? = null

    private lateinit var binding: ActivityMainBinding

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.decodeImageFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.scan)
        }

        // The controller enables photo capture by default; the scanner only needs
        // preview + analysis, so drop ImageCapture to save camera resources.
        cameraController = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CODE)
        }

        // GDPR: the banner is only built once consent has been resolved.
        ConsentManager.gatherConsent(this) { showBanner() }

        setupFlashButtons()
        observeViewModel()
    }

    // Bind the camera to the activity lifecycle: CameraX starts and stops it with
    // onStart/onStop, so there is no manual start/stop in onResume/onDestroy.
    private fun startCamera() {
        if (barcodeScanner != null) return
        val scanner = BarcodeScanning.getClient()
        barcodeScanner = scanner
        val mainExecutor = ContextCompat.getMainExecutor(this)

        cameraController.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, mainExecutor) { result ->
                val barcode = result.getValue(scanner)?.firstOrNull { it.rawValue != null }
                if (barcode != null) onBarcodeDetected(barcode)
            }
        )
        cameraController.bindToLifecycle(this)
        binding.previewView.controller = cameraController

        cameraController.initializationFuture.addListener({
            // Torch can only be set once the camera is open, so apply the saved state now.
            if (cameraController.cameraInfo?.hasFlashUnit() == true) {
                cameraController.enableTorch(viewModel.flashEnabled.value == true)
            } else {
                binding.flashOn?.visibility = View.GONE
                binding.flashOff?.visibility = View.GONE
            }
        }, mainExecutor)
    }

    private fun onBarcodeDetected(barcode: Barcode) {
        val text = barcode.rawValue!!
        if (viewModel.shouldIgnoreLiveScan(text)) return
        viewModel.handleScanResult(text = text, format = barcode.formatName())
        Haptics.scanSuccess(this)
    }

    // Dipanggil ConsentManager setelah consent selesai. Guard isEmpty mencegah
    // banner dobel kalau callback jalan dua kali (cache + refresh).
    private fun showBanner() {
        val container = binding.adViewContainer ?: return
        if (container.childCount > 0) return
        val adView = AdView(this).apply {
            adUnitId = AppConfig.bannerAdId()
            setAdSize(AdSize.BANNER)
        }
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    // Observe perubahan dari ViewModel dan update UI
    private fun observeViewModel() {
        viewModel.flashEnabled.observe(this) { enabled ->
            cameraController.enableTorch(enabled)
            binding.flashOn?.visibility = if (enabled) View.GONE else View.VISIBLE
            binding.flashOff?.visibility = if (enabled) View.VISIBLE else View.GONE
        }

        // No new sheet means no dismiss callback either; don't leave the scanner
        // paused with nothing on screen to resume it.
        observeScanResults(viewModel, onSheetSkipped = viewModel::resumeScanning)
    }

    // Activity hanya tahu "user tap flash" → delegasi ke ViewModel
    private fun setupFlashButtons() {
        binding.flashOn?.setOnClickListener { viewModel.toggleFlash() }
        binding.flashOff?.setOnClickListener { viewModel.toggleFlash() }
    }

    // Covers returning from app settings after granting the camera permission there.
    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeScanner?.close()
    }

    // Bottom sheet ditutup → lanjut scan supaya bisa scan berikutnya
    // tanpa perlu keluar-masuk screen.
    override fun onScanResultSheetDismissed() {
        viewModel.resumeScanning()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                showCameraPermissionDenied()
            }
        }
    }

    // After a denial (especially "Don't ask again") the dialog can't come back,
    // so point the user to app settings instead of leaving a black preview.
    private fun showCameraPermissionDenied() {
        Snackbar.make(binding.root, R.string.camera_permission_denied, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.camera_permission_settings) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                )
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scanner, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.scan_from_gallery -> {
                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
