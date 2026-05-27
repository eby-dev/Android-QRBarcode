package com.ahmadabuhasan.qrbarcode.model

sealed class ScanAction {
    data class OpenUrl(val url: String) : ScanAction()
    data class ShareText(val text: String) : ScanAction()
}
