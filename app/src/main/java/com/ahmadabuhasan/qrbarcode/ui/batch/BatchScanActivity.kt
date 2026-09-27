package com.ahmadabuhasan.qrbarcode.ui.batch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityBatchScanBinding
import com.ahmadabuhasan.qrbarcode.ui.main.BarcodeCamera
import com.ahmadabuhasan.qrbarcode.ui.main.formatName
import com.ahmadabuhasan.qrbarcode.ui.main.hasCameraPermission
import com.ahmadabuhasan.qrbarcode.ui.main.showCameraPermissionDenied
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.ahmadabuhasan.qrbarcode.utils.Haptics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Continuous scanning for stock counts and inventories: every code goes straight
// into a list (beep + vibration, no result sheet), repeats are counted, and the
// list can be shared as text or a CSV file. No banner — this screen is all
// aiming, the same hand movement that caused accidental ad clicks before.
class BatchScanActivity : BaseActivity() {

    companion object {
        private const val PERMISSION_CODE = 101
    }

    private val viewModel: BatchScanViewModel by viewModels()
    private lateinit var binding: ActivityBatchScanBinding
    private lateinit var camera: BarcodeCamera
    private lateinit var adapter: BatchAdapter

    private var flashEnabled = false

    // Beeps through the notification stream so silent mode mutes it. Some
    // devices refuse to create a ToneGenerator; then the vibration still runs.
    private var toneGenerator: ToneGenerator? = null

    private val confirmDiscardOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = confirmDiscard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.batch_scan)
        }
        onBackPressedDispatcher.addCallback(this, confirmDiscardOnBack)
        toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()

        adapter = BatchAdapter(onRemove = viewModel::remove)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        camera = BarcodeCamera(this, binding.previewView) { barcodes ->
            val added = barcodes.count { viewModel.onDetected(it.rawValue!!, it.formatName()) }
            if (added > 0) confirmScan()
        }
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CODE)
        }

        binding.btnFlash.setOnClickListener {
            flashEnabled = !flashEnabled
            camera.enableTorch(flashEnabled)
            updateFlashIcon()
        }

        viewModel.list.observe(this) { items ->
            val grewAtTop = items.size > adapter.itemCount
            adapter.submitList(items) {
                if (grewAtTop) binding.recyclerView.scrollToPosition(0)
            }
            binding.textCount.text = resources.getQuantityString(R.plurals.batch_items, items.size, items.size)
            binding.emptyView.isVisible = items.isEmpty()
            confirmDiscardOnBack.isEnabled = items.isNotEmpty()
            invalidateOptionsMenu()
        }
    }

    private fun startCamera() {
        camera.start { hasFlash ->
            binding.btnFlash.isVisible = hasFlash
            if (hasFlash) camera.enableTorch(flashEnabled)
        }
    }

    private fun updateFlashIcon() {
        binding.btnFlash.setImageResource(if (flashEnabled) R.drawable.ic_flash_off else R.drawable.ic_flash_on)
        binding.btnFlash.contentDescription = getString(if (flashEnabled) R.string.flashOff else R.string.flashOn)
    }

    private fun confirmScan() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        Haptics.scanSuccess(this)
    }

    private fun confirmDiscard() {
        val count = viewModel.list.value.orEmpty().size
        AlertDialog.Builder(this)
            .setMessage(resources.getQuantityString(R.plurals.batch_confirm_discard, count, count))
            .setPositiveButton(R.string.batch_discard) { _, _ -> finish() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareCsv() {
        val dir = File(cacheDir, "exports").also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "batch_scan_$stamp.csv").apply { writeText(viewModel.toCsv()) }
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.batch_export_csv)))
    }

    private fun shareText() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, viewModel.toText())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.batch_share_text)))
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission()) startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        camera.close()
        toneGenerator?.release()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.isNotEmpty()) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                showCameraPermissionDenied(binding.root)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_batch_scan, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasItems = viewModel.list.value.orEmpty().isNotEmpty()
        menu.findItem(R.id.action_export_csv).isEnabled = hasItems
        menu.findItem(R.id.action_share_text).isEnabled = hasItems
        menu.findItem(R.id.action_clear).isEnabled = hasItems
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_export_csv -> {
                shareCsv()
                true
            }
            R.id.action_share_text -> {
                shareText()
                true
            }
            R.id.action_clear -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.batch_confirm_clear)
                    .setPositiveButton(R.string.history_clear_all) { _, _ -> viewModel.clear() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
