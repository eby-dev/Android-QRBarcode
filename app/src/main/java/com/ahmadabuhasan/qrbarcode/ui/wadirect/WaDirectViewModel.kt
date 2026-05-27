package com.ahmadabuhasan.qrbarcode.ui.wadirect

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WaDirectViewModel : ViewModel() {

    private val _phoneNumber = MutableLiveData<String>()

    // Hasil format nomor yang siap dibuka di WhatsApp, null jika nomor tidak valid
    private val _openWhatsApp = MutableLiveData<String?>()
    val openWhatsApp: LiveData<String?> = _openWhatsApp

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun onOpenClicked(input: String) {
        val formatted = formatNumber(input.trim())
        if (formatted == null) {
            _error.value = "invalid"
        } else {
            _openWhatsApp.value = formatted
        }
    }

    fun onWhatsAppOpened() {
        _openWhatsApp.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }

    // Konversi berbagai format nomor ke format wa.me (628xxx)
    private fun formatNumber(input: String): String? {
        if (input.isBlank()) return null

        // Ambil digit saja
        val digits = input.filter { it.isDigit() }
        if (digits.length < 7) return null

        return when {
            digits.startsWith("62") -> digits          // sudah format internasional
            digits.startsWith("0") -> "62" + digits.substring(1)  // 08xxx → 628xxx
            else -> "62$digits"                         // langsung tambah 62
        }
    }
}
