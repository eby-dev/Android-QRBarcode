package com.ahmadabuhasan.qrbarcode.ui.wadirect

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class WaDirectParams(val number: String, val message: String)

class WaDirectViewModel : ViewModel() {

    private val _openWhatsApp = MutableLiveData<WaDirectParams?>()
    val openWhatsApp: LiveData<WaDirectParams?> = _openWhatsApp

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun onOpenClicked(phone: String, countryCode: String, message: String) {
        val formatted = formatNumber(phone.trim(), countryCode)
        if (formatted == null) {
            _error.value = "invalid"
        } else {
            _openWhatsApp.value = WaDirectParams(formatted, message.trim())
        }
    }

    fun onWhatsAppOpened() {
        _openWhatsApp.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }

    private fun formatNumber(input: String, countryCode: String): String? {
        if (input.isBlank()) return null
        val digits = input.filter { it.isDigit() }
        if (digits.length < 5) return null

        return when {
            digits.startsWith(countryCode) -> digits
            digits.startsWith("0") -> countryCode + digits.substring(1)
            else -> countryCode + digits
        }
    }
}
