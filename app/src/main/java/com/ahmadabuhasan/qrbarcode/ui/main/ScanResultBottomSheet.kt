package com.ahmadabuhasan.qrbarcode.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.ahmadabuhasan.qrbarcode.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class ScanResultBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onScanResultSheetDismissed()
    }

    private val text: String get() = requireArguments().getString(ARG_TEXT).orEmpty()
    private val format: String get() = requireArguments().getString(ARG_FORMAT).orEmpty()
    private val isUrl: Boolean get() = requireArguments().getBoolean(ARG_IS_URL)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_scan_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val typeLabel = if (isUrl) getString(R.string.scan_result_type_url) else getString(R.string.scan_result_type_text)
        view.findViewById<TextView>(R.id.textTypeLabel).text = getString(R.string.history_meta_format, typeLabel, format)
        view.findViewById<TextView>(R.id.textContent).text = text

        view.findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("scan", text))
            Toast.makeText(requireContext(), R.string.scan_result_copied, Toast.LENGTH_SHORT).show()
        }

        val btnOpen = view.findViewById<MaterialButton>(R.id.btnOpen)
        btnOpen.visibility = if (isUrl) View.VISIBLE else View.GONE
        btnOpen.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
            startActivity(Intent.createChooser(intent, getString(R.string.scan_result_open)))
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.scan_result_share)))
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnScanAgain).setOnClickListener {
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? Listener)?.onScanResultSheetDismissed()
    }

    companion object {
        const val TAG = "ScanResultBottomSheet"
        private const val ARG_TEXT = "text"
        private const val ARG_FORMAT = "format"
        private const val ARG_IS_URL = "is_url"

        fun new(text: String, format: String, isUrl: Boolean): ScanResultBottomSheet =
            ScanResultBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEXT, text)
                    putString(ARG_FORMAT, format)
                    putBoolean(ARG_IS_URL, isUrl)
                }
            }
    }
}
