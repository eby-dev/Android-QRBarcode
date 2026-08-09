package com.ahmadabuhasan.qrbarcode.ui.main

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ahmadabuhasan.qrbarcode.data.AppDatabase
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import com.ahmadabuhasan.qrbarcode.model.ScanAction
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // --- Gallery-decode result signals (String res id, null = consumed) ---
    sealed class GalleryDecodeError { object NoResult : GalleryDecodeError(); object ReadFailed : GalleryDecodeError() }
    private val _galleryDecodeError = MutableLiveData<GalleryDecodeError?>()
    val galleryDecodeError: LiveData<GalleryDecodeError?> = _galleryDecodeError

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

    fun onGalleryDecodeErrorConsumed() {
        _galleryDecodeError.value = null
    }

    fun decodeImageFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } catch (_: Exception) {
                null
            }

            if (bitmap == null) {
                _galleryDecodeError.postValue(GalleryDecodeError.ReadFailed)
                return@launch
            }

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.TRY_HARDER to true))
            }
            val result = try {
                reader.decodeWithState(binaryBitmap)
            } catch (_: NotFoundException) {
                null
            } catch (_: Exception) {
                null
            }

            if (result == null) {
                _galleryDecodeError.postValue(GalleryDecodeError.NoResult)
            } else {
                val text = result.text ?: result.toString()
                val format = result.barcodeFormat?.name ?: "UNKNOWN"
                withContext(Dispatchers.Main) { handleScanResult(text, format) }
            }
        }
    }
}
