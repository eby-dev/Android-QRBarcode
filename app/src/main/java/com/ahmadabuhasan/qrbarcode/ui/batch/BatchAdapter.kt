package com.ahmadabuhasan.qrbarcode.ui.batch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ItemBatchScanBinding
import java.text.DateFormat
import java.util.Date

class BatchAdapter(
    private val onRemove: (BatchItem) -> Unit,
) : ListAdapter<BatchItem, BatchAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemBatchScanBinding) : RecyclerView.ViewHolder(binding.root)

    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemBatchScanBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            textContent.text = item.content
            textMeta.text = root.context.getString(
                R.string.history_meta_format, item.format, timeFormat.format(Date(item.lastScannedAt))
            )
            textCount.isVisible = item.count > 1
            textCount.text = root.context.getString(R.string.batch_count, item.count)
            btnRemove.setOnClickListener { onRemove(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<BatchItem>() {
            override fun areItemsTheSame(oldItem: BatchItem, newItem: BatchItem) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: BatchItem, newItem: BatchItem) = oldItem == newItem
        }
    }
}
