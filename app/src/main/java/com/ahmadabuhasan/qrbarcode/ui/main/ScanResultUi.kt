package com.ahmadabuhasan.qrbarcode.ui.main

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.model.ScanContent

// Shows each scan the ViewModel emits in a ScanResultBottomSheet, and gallery
// decode failures as a toast. onSheetSkipped runs when a sheet is already open.
fun AppCompatActivity.observeScanResults(viewModel: ScanViewModel, onSheetSkipped: () -> Unit = {}) {
    viewModel.scanResult.observe(this) { result ->
        result ?: return@observe  // null = sudah dikonsumsi
        if (!showScanResultSheet(result.text, result.format)) onSheetSkipped()
        viewModel.onScanResultConsumed()
    }

    viewModel.galleryDecodeError.observe(this) { error ->
        error ?: return@observe
        val msgRes = when (error) {
            ScanViewModel.GalleryDecodeError.NoResult -> R.string.scan_from_gallery_no_result
            ScanViewModel.GalleryDecodeError.ReadFailed -> R.string.scan_from_gallery_read_failed
        }
        Toast.makeText(this, msgRes, Toast.LENGTH_LONG).show()
        viewModel.onGalleryDecodeErrorConsumed()
    }
}

// Returns false when a sheet is already open. A dismissed sheet stays in the
// FragmentManager until its removal commit runs, and ML Kit can deliver a new
// result in that gap, so pending transactions are flushed before the check.
fun AppCompatActivity.showScanResultSheet(text: String, format: String): Boolean {
    supportFragmentManager.executePendingTransactions()
    if (supportFragmentManager.findFragmentByTag(ScanResultBottomSheet.TAG) != null) return false
    ScanResultBottomSheet.new(text, format).show(supportFragmentManager, ScanResultBottomSheet.TAG)
    return true
}

@StringRes
fun ScanContent.typeLabelRes(): Int = when (this) {
    is ScanContent.Url -> R.string.scan_type_url
    is ScanContent.Wifi -> R.string.scan_type_wifi
    is ScanContent.Phone -> R.string.scan_type_phone
    is ScanContent.Sms -> R.string.scan_type_sms
    is ScanContent.Email -> R.string.scan_type_email
    is ScanContent.Geo -> R.string.scan_type_geo
    is ScanContent.VCard -> R.string.scan_type_vcard
    is ScanContent.CalendarEvent -> R.string.scan_type_event
    is ScanContent.Text -> R.string.scan_type_text
}
