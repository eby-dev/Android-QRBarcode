package com.ahmadabuhasan.qrbarcode.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ahmadabuhasan.qrbarcode.data.AppDatabase
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import com.ahmadabuhasan.qrbarcode.model.ScanContent
import com.ahmadabuhasan.qrbarcode.model.ScanContentParser
import com.ahmadabuhasan.qrbarcode.model.ScanResult
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).scanHistoryDao()

    // Default options detect every supported format, same as the live scanner.
    private val galleryScanner = BarcodeScanning.getClient()

    // --- Live scanning pause ---
    // Set when a result is shown so the camera doesn't fire again behind the
    // sheet; cleared when the sheet is dismissed. Lives here to survive rotation.
    var isScanningPaused = false
        private set

    fun resumeScanning() {
        isScanningPaused = false
    }

    // --- Flash state ---
    private val _flashEnabled = MutableLiveData(false)
    val flashEnabled: LiveData<Boolean> = _flashEnabled

    fun toggleFlash() {
        _flashEnabled.value = !(_flashEnabled.value ?: false)
    }

    // --- Scan result ---
    // null = sudah dikonsumsi Activity (mis. bottom sheet sudah ditampilkan)
    private val _scanResult = MutableLiveData<ScanResult?>()
    val scanResult: LiveData<ScanResult?> = _scanResult

    // --- Gallery-decode error signals (null = consumed) ---
    sealed class GalleryDecodeError { object NoResult : GalleryDecodeError(); object ReadFailed : GalleryDecodeError() }
    private val _galleryDecodeError = MutableLiveData<GalleryDecodeError?>()
    val galleryDecodeError: LiveData<GalleryDecodeError?> = _galleryDecodeError

    fun handleScanResult(text: String, format: String) {
        isScanningPaused = true
        val isUrl = ScanContentParser.parse(text) is ScanContent.Url

        viewModelScope.launch {
            dao.upsert(
                ScanHistoryEntity(
                    content = text,
                    format = format,
                    isUrl = isUrl,
                    scannedAt = System.currentTimeMillis()
                )
            )
        }

        _scanResult.value = ScanResult(text = text, format = format)
    }

    fun onScanResultConsumed() {
        _scanResult.value = null
    }

    fun onGalleryDecodeErrorConsumed() {
        _galleryDecodeError.value = null
    }

    fun decodeImageFromUri(uri: Uri) {
        val image = try {
            InputImage.fromFilePath(getApplication(), uri)
        } catch (_: Exception) {
            _galleryDecodeError.value = GalleryDecodeError.ReadFailed
            return
        }

        // Task callbacks run on the main thread.
        galleryScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull { it.rawValue != null }
                if (barcode == null) {
                    _galleryDecodeError.value = GalleryDecodeError.NoResult
                } else {
                    handleScanResult(barcode.rawValue!!, barcode.formatName())
                }
            }
            .addOnFailureListener { _galleryDecodeError.value = GalleryDecodeError.NoResult }
    }

    override fun onCleared() {
        galleryScanner.close()
    }
}
