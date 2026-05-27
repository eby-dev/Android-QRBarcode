package com.ahmadabuhasan.qrbarcode.ui.qrgenerator

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

class QrGeneratorViewModel : ViewModel() {

    private val _qrBitmap = MutableLiveData<Bitmap?>()
    val qrBitmap: LiveData<Bitmap?> = _qrBitmap

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun generate(content: String) {
        if (content.isBlank()) {
            _error.value = "empty"
            return
        }
        _qrBitmap.value = createQrBitmap(content)
    }

    fun onErrorShown() {
        _error.value = null
    }

    private fun createQrBitmap(content: String, size: Int = 512): Bitmap {
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size) { index ->
            val x = index % size
            val y = index / size
            if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }
}
