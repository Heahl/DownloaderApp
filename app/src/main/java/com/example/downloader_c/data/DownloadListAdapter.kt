package com.example.downloader_c.data

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.downloader_c.data.DownloadedFile
import com.example.downloader_c.databinding.ItemDownloadBinding

class DownloadListAdapter(
    private val onOpenClick: (DownloadedFile) -> Unit,
    private val onDeleteClick: (DownloadedFile) -> Unit
) : ListAdapter<DownloadedFile, DownloadListAdapter.DownloadViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DownloadViewHolder {
        val binding =
            ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: DownloadViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(
        private val binding: ItemDownloadBinding
    ) : ViewHolder(binding.root) {

        fun bind(item: DownloadedFile) {
            binding.tvFilename.text = item.fileName
            binding.tvMeta.text = "${item.formattedSize} * ${item.formattedDate}"

            // Klick-Listener
            binding.btnOpen.setOnClickListener { onOpenClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DownloadedFile>() {
        override fun areItemsTheSame(oldItem: DownloadedFile, newItem: DownloadedFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadedFile, newItem: DownloadedFile): Boolean {
            return oldItem == newItem
        }
    }
}