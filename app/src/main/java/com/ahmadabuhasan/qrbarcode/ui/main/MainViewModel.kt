package com.ahmadabuhasan.qrbarcode.ui.main

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MainViewModel(application: Application) : ScanViewModel(application) {

    // --- Live scanning pause ---
    // Set when a result is shown so the camera doesn't fire again behind the
    // sheet; cleared when the sheet is dismissed. Lives here to survive rotation.
    var isScanningPaused = false
        private set

    // ML Kit reads a code within a frame or two, so "Scan again" while still
    // pointing at the same code would reopen the sheet instantly. Ignore that
    // same code for a short grace period after resuming.
    private var lastScannedText: String? = null
    private var resumedAt = 0L

    fun resumeScanning() {
        isScanningPaused = false
        resumedAt = System.currentTimeMillis()
    }

    fun shouldIgnoreLiveScan(text: String): Boolean =
        isScanningPaused ||
            (text == lastScannedText && System.currentTimeMillis() - resumedAt < RESCAN_GRACE_MS)

    // --- Flash state ---
    private val _flashEnabled = MutableLiveData(false)
    val flashEnabled: LiveData<Boolean> = _flashEnabled

    fun toggleFlash() {
        _flashEnabled.value = !(_flashEnabled.value ?: false)
    }

    override fun handleScanResult(text: String, format: String) {
        isScanningPaused = true
        lastScannedText = text
        super.handleScanResult(text, format)
    }

    private companion object {
        const val RESCAN_GRACE_MS = 2000L
    }
}
