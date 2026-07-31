package com.ahmadabuhasan.qrbarcode.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import com.ahmadabuhasan.qrbarcode.databinding.ActivityHistoryBinding
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity

class HistoryActivity : BaseActivity() {

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.history)
        }

        val adapter = HistoryAdapter(
            onCopy = ::copyToClipboard,
            onShare = ::shareText,
            onOpen = ::openUrl,
            onDelete = ::confirmDelete
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.history.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun copyToClipboard(item: ScanHistoryEntity) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("scan", item.content))
        Toast.makeText(this, R.string.history_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareText(item: ScanHistoryEntity) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, item.content)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.history_share)))
    }

    private fun openUrl(item: ScanHistoryEntity) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.content))
        startActivity(Intent.createChooser(intent, getString(R.string.history_open)))
    }

    private fun confirmDelete(item: ScanHistoryEntity) {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_confirm_delete)
            .setPositiveButton(R.string.history_delete) { _, _ -> viewModel.delete(item) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_all -> {
                AlertDialog.Builder(this)
                    .setMessage(R.string.history_confirm_clear_all)
                    .setPositiveButton(R.string.history_clear_all) { _, _ -> viewModel.clearAll() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
