package com.ahmadabuhasan.qrbarcode.ui.batch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BatchItem(
    val content: String,
    val format: String,
    val count: Int,
    val lastScannedAt: Long,
) {
    val key: String get() = keyOf(content, format)

    companion object {
        fun keyOf(content: String, format: String) = "$format\u0000$content"
    }
}

object BatchCsv {

    private const val HEADER = "content,format,count,last_scanned"

    // A byte-order mark lets Excel detect UTF-8 instead of mangling non-ASCII codes.
    fun build(items: List<BatchItem>, timeZone: TimeZone = TimeZone.getDefault()): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { this.timeZone = timeZone }
        return buildString {
            append('\uFEFF').append(HEADER).append("\r\n")
            items.forEach { item ->
                append(cell(item.content)).append(',')
                append(cell(item.format)).append(',')
                append(item.count).append(',')
                append(dateFormat.format(Date(item.lastScannedAt))).append("\r\n")
            }
        }
    }

    // RFC 4180 quoting, plus a leading apostrophe on values a spreadsheet would
    // run as a formula — a scanned code is untrusted input (CSV injection).
    internal fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
