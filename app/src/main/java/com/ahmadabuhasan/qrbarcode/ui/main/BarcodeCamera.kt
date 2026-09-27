package com.ahmadabuhasan.qrbarcode.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.ahmadabuhasan.qrbarcode.R
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

// Live barcode scanning shared by the scanner and the batch scanner: a CameraX
// LifecycleCameraController (tap-to-focus and pinch-to-zoom come built in) with
// an MlKitAnalyzer that reports every frame's barcodes to onBarcodes.
class BarcodeCamera(
    private val activity: AppCompatActivity,
    private val previewView: PreviewView,
    private val onBarcodes: (List<Barcode>) -> Unit,
) {

    // The controller enables photo capture by default; scanning only needs
    // preview + analysis, so drop ImageCapture to save camera resources.
    private val controller = LifecycleCameraController(activity).apply {
        setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
    }
    private var scanner: BarcodeScanner? = null

    // Bind the camera to the activity lifecycle: CameraX starts and stops it with
    // onStart/onStop. Safe to call repeatedly; only the first call binds.
    // onReady reports whether the device has a flash, once the camera is open.
    fun start(onReady: (hasFlash: Boolean) -> Unit = {}) {
        if (scanner != null) return
        val barcodeScanner = BarcodeScanning.getClient()
        scanner = barcodeScanner
        val mainExecutor = ContextCompat.getMainExecutor(activity)

        controller.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(listOf(barcodeScanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, mainExecutor) { result ->
                val barcodes = result.getValue(barcodeScanner)?.filter { it.rawValue != null }
                if (!barcodes.isNullOrEmpty()) onBarcodes(barcodes)
            }
        )
        controller.bindToLifecycle(activity)
        previewView.controller = controller

        controller.initializationFuture.addListener({
            onReady(controller.cameraInfo?.hasFlashUnit() == true)
        }, mainExecutor)
    }

    // Torch can only be set once the camera is open; before that this is a no-op,
    // so callers re-apply their saved state from start()'s onReady.
    fun enableTorch(enabled: Boolean) {
        controller.enableTorch(enabled)
    }

    fun close() {
        scanner?.close()
    }
}

fun Activity.hasCameraPermission(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

// After a denial (especially "Don't ask again") the dialog can't come back,
// so point the user to app settings instead of leaving a black preview.
fun Activity.showCameraPermissionDenied(anchor: View) {
    Snackbar.make(anchor, R.string.camera_permission_denied, Snackbar.LENGTH_INDEFINITE)
        .setAction(R.string.camera_permission_settings) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
        .show()
}
