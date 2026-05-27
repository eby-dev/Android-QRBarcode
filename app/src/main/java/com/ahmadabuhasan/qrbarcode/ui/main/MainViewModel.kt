package com.ahmadabuhasan.qrbarcode.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ahmadabuhasan.qrbarcode.model.ScanAction

class MainViewModel : ViewModel() {

    // --- Flash state ---
    private val _flashEnabled = MutableLiveData(false)
    val flashEnabled: LiveData<Boolean> = _flashEnabled

    fun toggleFlash() {
        _flashEnabled.value = !(_flashEnabled.value ?: false)
    }

    // --- Scan result ---
    // null = action sudah dikonsumsi Activity, supaya tidak trigger ulang saat screen rotation
    private val _scanAction = MutableLiveData<ScanAction?>()
    val scanAction: LiveData<ScanAction?> = _scanAction

    fun handleScanResult(text: String) {
        _scanAction.value = when {
            text.startsWith("https://") || text.startsWith("http://") -> ScanAction.OpenUrl(text)
            else -> ScanAction.ShareText(text)
        }
    }

    fun onScanActionConsumed() {
        _scanAction.value = null
    }
}
