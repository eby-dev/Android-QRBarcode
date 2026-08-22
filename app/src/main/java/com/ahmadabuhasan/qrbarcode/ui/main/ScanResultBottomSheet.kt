package com.ahmadabuhasan.qrbarcode.ui.main

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.model.ScanContent
import com.ahmadabuhasan.qrbarcode.model.ScanContentParser
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class ScanResultBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onScanResultSheetDismissed()
    }

    private val rawText: String get() = requireArguments().getString(ARG_TEXT).orEmpty()
    private val format: String get() = requireArguments().getString(ARG_FORMAT).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_scan_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val content = ScanContentParser.parse(rawText)

        val trailing = trailingHintFor(content) ?: format
        view.findViewById<TextView>(R.id.textTypeLabel).text =
            getString(R.string.history_meta_format, getString(typeLabelFor(content)), trailing)
        view.findViewById<TextView>(R.id.textContent).text = displayTextFor(content)

        val btnPrimary = view.findViewById<MaterialButton>(R.id.btnPrimary)
        val primary = primaryActionFor(content)
        if (primary == null) {
            btnPrimary.visibility = View.GONE
        } else {
            btnPrimary.visibility = View.VISIBLE
            btnPrimary.setText(primary.labelRes)
            btnPrimary.setOnClickListener { primary.action() }
        }

        view.findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener {
            copyToClipboard(rawText)
            toast(R.string.scan_result_copied)
        }

        view.findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, rawText)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.scan_result_share)))
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnScanAgain).setOnClickListener { dismiss() }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? Listener)?.onScanResultSheetDismissed()
    }

    // --- Rendering helpers ---

    @StringRes
    private fun typeLabelFor(content: ScanContent): Int = when (content) {
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

    // For URLs, prefer showing the host next to the type badge (safer than
    // the barcode format) so users can eyeball the domain before opening.
    private fun trailingHintFor(content: ScanContent): String? = when (content) {
        is ScanContent.Url -> runCatching { Uri.parse(content.raw).host }.getOrNull()?.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun displayTextFor(content: ScanContent): String = when (content) {
        is ScanContent.Wifi -> buildString {
            appendLine(getString(R.string.scan_wifi_ssid, content.ssid))
            append(
                if (content.password.isEmpty()) getString(R.string.scan_wifi_password_none)
                else getString(R.string.scan_wifi_password, content.password)
            )
            if (content.security.isNotBlank()) {
                appendLine()
                append(getString(R.string.scan_wifi_security, content.security))
            }
            if (content.hidden) {
                appendLine()
                append(getString(R.string.scan_wifi_hidden))
            }
        }
        is ScanContent.VCard -> buildString {
            content.name?.let { appendLine(getString(R.string.scan_vcard_name, it)) }
            content.phone?.let { appendLine(getString(R.string.scan_vcard_phone, it)) }
            content.email?.let { append(getString(R.string.scan_vcard_email, it)) }
        }.ifBlank { content.raw }
        is ScanContent.CalendarEvent -> buildString {
            content.title?.let { appendLine(getString(R.string.scan_event_title, it)) }
            val startMs = content.start?.let(::parseCalendarMillis)
            val endMs = content.end?.let(::parseCalendarMillis)
            val startStr = startMs?.let(::formatDateTime) ?: content.start
            val endStr = endMs?.let(::formatDateTime) ?: content.end
            when {
                startStr != null && endStr != null -> appendLine(getString(R.string.scan_event_when_range, startStr, endStr))
                startStr != null -> appendLine(getString(R.string.scan_event_when, startStr))
            }
            content.location?.let { append(getString(R.string.scan_event_location, it)) }
        }.ifBlank { content.raw }
        else -> content.raw
    }

    private data class PrimaryAction(@StringRes val labelRes: Int, val action: () -> Unit)

    private fun primaryActionFor(content: ScanContent): PrimaryAction? = when (content) {
        is ScanContent.Url -> PrimaryAction(R.string.scan_action_open) {
            launchAndDismiss(Intent(Intent.ACTION_VIEW, Uri.parse(content.raw)), chooserTitleRes = R.string.scan_action_open)
        }
        is ScanContent.Wifi -> PrimaryAction(R.string.scan_action_wifi_copy_password) {
            copyToClipboard(content.password)
            toast(R.string.scan_result_password_copied)
        }
        is ScanContent.Phone -> PrimaryAction(R.string.scan_action_dial) {
            launchAndDismiss(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${content.number}")))
        }
        is ScanContent.Sms -> PrimaryAction(R.string.scan_action_sms) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${content.number}"))
            if (content.body.isNotEmpty()) intent.putExtra("sms_body", content.body)
            launchAndDismiss(intent)
        }
        is ScanContent.Email -> PrimaryAction(R.string.scan_action_email) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${content.to}")).apply {
                if (content.subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, content.subject)
                if (content.body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, content.body)
            }
            launchAndDismiss(intent)
        }
        is ScanContent.Geo -> PrimaryAction(R.string.scan_action_maps) {
            launchAndDismiss(Intent(Intent.ACTION_VIEW, Uri.parse(content.raw)))
        }
        is ScanContent.VCard -> PrimaryAction(R.string.scan_action_save_contact) {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                content.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                content.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                content.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            }
            launchAndDismiss(intent)
        }
        is ScanContent.CalendarEvent -> PrimaryAction(R.string.scan_action_add_event) {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                content.title?.let { putExtra(CalendarContract.Events.TITLE, it) }
                content.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                content.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                content.start?.let(::parseCalendarMillis)?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                content.end?.let(::parseCalendarMillis)?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            }
            launchAndDismiss(intent)
        }
        is ScanContent.Text -> null
    }

    // --- Common helpers ---

    private fun launchAndDismiss(intent: Intent, @StringRes chooserTitleRes: Int? = null) {
        val toStart = if (chooserTitleRes != null) Intent.createChooser(intent, getString(chooserTitleRes)) else intent
        try {
            startActivity(toStart)
            dismiss()
        } catch (_: ActivityNotFoundException) {
            toast(R.string.scan_result_no_app)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("scan", text))
    }

    private fun toast(@StringRes res: Int) {
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
    }

    // vEvent DTSTART/DTEND come in ISO basic form: UTC (`yyyyMMdd'T'HHmmss'Z'`),
    // local (`yyyyMMdd'T'HHmmss`), or all-day (`yyyyMMdd`). Returns null when
    // none match so the sheet can fall back to the raw string.
    private fun parseCalendarMillis(value: String): Long? {
        val trimmed = value.trim()
        val patterns = listOf(
            "yyyyMMdd'T'HHmmss'Z'" to java.util.TimeZone.getTimeZone("UTC"),
            "yyyyMMdd'T'HHmmss" to java.util.TimeZone.getDefault(),
            "yyyyMMdd" to java.util.TimeZone.getDefault(),
        )
        for ((pattern, tz) in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                sdf.timeZone = tz
                sdf.isLenient = false
                return sdf.parse(trimmed)?.time
            } catch (_: java.text.ParseException) {
                continue
            }
        }
        return null
    }

    private fun formatDateTime(millis: Long): String =
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT,
        ).format(java.util.Date(millis))

    companion object {
        const val TAG = "ScanResultBottomSheet"
        private const val ARG_TEXT = "text"
        private const val ARG_FORMAT = "format"

        fun new(text: String, format: String): ScanResultBottomSheet =
            ScanResultBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEXT, text)
                    putString(ARG_FORMAT, format)
                }
            }
    }
}
