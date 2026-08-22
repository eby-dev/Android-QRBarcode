package com.ahmadabuhasan.qrbarcode.model

data class ScanResult(
    val text: String,
    val format: String,
    val isUrl: Boolean,
)
