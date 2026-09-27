package com.ahmadabuhasan.qrbarcode.ui.main

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
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
import com.ahmadabuhasan.qrbarcode.ui.about.AboutActivity
import com.ahmadabuhasan.qrbarcode.ui.history.HistoryActivity
import com.ahmadabuhasan.qrbarcode.ui.qrgenerator.QrGeneratorActivity
import com.ahmadabuhasan.qrbarcode.ui.wadirect.WaDirectActivity
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.ahmadabuhasan.qrbarcode.utils.AppConfig
import com.ahmadabuhasan.qrbarcode.utils.ConsentManager
import com.ahmadabuhasan.qrbarcode.utils.Haptics
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

class MainActivity : BaseActivity(), ScanResultBottomSheet.Listener {

    companion object {
        private const val PERMISSION_CODE = 100
        private const val FLEXIBLE_APP_UPDATE_REQ_CODE = 123
        private var pressedTime: Long = 0
    }

    // ViewModel — semua state & logic bisnis ada di sini
    private val viewModel: MainViewModel by viewModels()

    private lateinit var cameraController: LifecycleCameraController
    private var barcodeScanner: BarcodeScanner? = null
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var installStateUpdatedListener: InstallStateUpdatedListener

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

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkUpdate()
        installStateUpdatedListener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> popupSnackBarForCompleteUpdate()
                InstallStatus.INSTALLED -> removeInstallStateUpdateListener()
                else -> Unit
            }
        }
        // Without registering, the "update downloaded → RESTART" snackbar only
        // appeared on the next launch.
        appUpdateManager.registerListener(installStateUpdatedListener)

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

        viewModel.scanResult.observe(this) { result ->
            result ?: return@observe  // null = sudah dikonsumsi
            // A dismissed sheet stays in the FragmentManager until its removal
            // commit runs, and ML Kit can deliver a new result in that gap. Flush
            // pending transactions so the check sees only a sheet that's really open.
            supportFragmentManager.executePendingTransactions()
            if (supportFragmentManager.findFragmentByTag(ScanResultBottomSheet.TAG) == null) {
                ScanResultBottomSheet.new(result.text, result.format)
                    .show(supportFragmentManager, ScanResultBottomSheet.TAG)
            } else {
                // No new sheet means no dismiss callback either; don't leave the
                // scanner paused with nothing on screen to resume it.
                viewModel.resumeScanning()
            }
            viewModel.onScanResultConsumed()
        }

        viewModel.galleryDecodeError.observe(this) { error ->
            error ?: return@observe
            val msgRes = when (error) {
                MainViewModel.GalleryDecodeError.NoResult -> R.string.scan_from_gallery_no_result
                MainViewModel.GalleryDecodeError.ReadFailed -> R.string.scan_from_gallery_read_failed
            }
            Toast.makeText(this, msgRes, Toast.LENGTH_LONG).show()
            viewModel.onGalleryDecodeErrorConsumed()
        }
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
        removeInstallStateUpdateListener()
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
        menuInflater.inflate(R.menu.optionmenu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.scan_from_gallery -> pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            R.id.history -> startActivity(Intent(this, HistoryActivity::class.java))
            R.id.qr_generator -> startActivity(Intent(this, QrGeneratorActivity::class.java))
            R.id.wa_direct -> startActivity(Intent(this, WaDirectActivity::class.java))
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FLEXIBLE_APP_UPDATE_REQ_CODE) {
            when (resultCode) {
                RESULT_CANCELED -> Toast.makeText(applicationContext, "Update canceled by user! ", Toast.LENGTH_LONG).show()
                RESULT_OK -> Toast.makeText(applicationContext, "Update success! ", Toast.LENGTH_LONG).show()
                else -> {
                    Toast.makeText(applicationContext, "Update failed! ", Toast.LENGTH_LONG).show()
                    checkUpdate()
                }
            }
        }
    }

    private fun checkUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                startUpdateFlow(appUpdateInfo)
            } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackBarForCompleteUpdate()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startUpdateFlow(appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.FLEXIBLE,
                this,
                FLEXIBLE_APP_UPDATE_REQ_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            e.printStackTrace()
        }
    }

    private fun popupSnackBarForCompleteUpdate() {
        Snackbar.make(
            findViewById(R.id.layout_activity_main),
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("RESTART") { appUpdateManager.completeUpdate() }
            setActionTextColor(ContextCompat.getColor(this@MainActivity, R.color.red))
            show()
        }
    }

    private fun removeInstallStateUpdateListener() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            finishAndRemoveTask()
        } else {
            Toast.makeText(this, "Press once again to exit", Toast.LENGTH_SHORT).show()
        }
        pressedTime = System.currentTimeMillis()
    }
}
