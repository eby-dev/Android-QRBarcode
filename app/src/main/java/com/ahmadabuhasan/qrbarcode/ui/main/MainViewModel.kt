package com.ahmadabuhasan.qrbarcode.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ahmadabuhasan.qrbarcode.data.AppDatabase
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import com.ahmadabuhasan.qrbarcode.model.ScanAction
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).scanHistoryDao()

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

    fun handleScanResult(text: String, format: String) {
        val isUrl = text.startsWith("https://") || text.startsWith("http://")

        viewModelScope.launch {
            dao.insert(
                ScanHistoryEntity(
                    content = text,
                    format = format,
                    isUrl = isUrl,
                    scannedAt = System.currentTimeMillis()
                )
            )
        }

        _scanAction.value = if (isUrl) ScanAction.OpenUrl(text) else ScanAction.ShareText(text)
    }

    fun onScanActionConsumed() {
        _scanAction.value = null
    }
}
