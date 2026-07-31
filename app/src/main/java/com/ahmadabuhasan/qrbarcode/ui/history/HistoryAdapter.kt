package com.ahmadabuhasan.qrbarcode.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import com.ahmadabuhasan.qrbarcode.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onCopy: (ScanHistoryEntity) -> Unit,
    private val onShare: (ScanHistoryEntity) -> Unit,
    private val onOpen: (ScanHistoryEntity) -> Unit,
    private val onDelete: (ScanHistoryEntity) -> Unit
) : ListAdapter<ScanHistoryEntity, HistoryAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScanHistoryEntity>() {
            override fun areItemsTheSame(o: ScanHistoryEntity, n: ScanHistoryEntity) = o.id == n.id
            override fun areContentsTheSame(o: ScanHistoryEntity, n: ScanHistoryEntity) = o == n
        }
        private val DATE_FMT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }

    inner class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textContent.text = item.content
            textMeta.text = root.context.getString(
                com.ahmadabuhasan.qrbarcode.R.string.history_meta_format,
                item.format,
                DATE_FMT.format(Date(item.scannedAt))
            )
            btnOpen.visibility = if (item.isUrl) android.view.View.VISIBLE else android.view.View.GONE
            btnOpen.setOnClickListener { onOpen(item) }
            btnCopy.setOnClickListener { onCopy(item) }
            btnShare.setOnClickListener { onShare(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
