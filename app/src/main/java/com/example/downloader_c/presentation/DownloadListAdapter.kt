package com.example.downloader_c.presentation

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.downloader_c.databinding.ItemDownloadBinding
import com.example.downloader_c.domain.DownloadedFile

/**
 * Ein [ListAdapter], der eine Liste von [DownloadedFile]s in einem RecyclerView anzeigt.
 *
 * Dieser Adapter kümmert sich um das Erstellen und das Binden der ViewHolder.
 * Er verwendet [DiffUtil] für effiziente Aktualisierungen der Liste, wenn sich der Inhalt ändert.
 * Außerdem behandelt er Klick-Events für die "Öffnen" und "Löschen"-Aktionen jedes Listeneintrags.
 *
 * @param onOpenClick Lambda-Funktion, die aufgerufen wird, wenn der "Öffnen"-Button für einen Eintrag
 *      gedrückt wird. Übergibt die entsprechende [DownloadedFile]-Instanz.
 * @param onDeleteClick Lambda-Funktion, die aufgerufen wird, wenn der "Löschen"-Button für einen Eintrag
 *      gedrückt wird. Übergibt die entsprechende [DownloadedFile]-Instanz.
 */
class DownloadListAdapter(
    private val onOpenClick: (DownloadedFile) -> Unit,
    private val onDeleteClick: (DownloadedFile) -> Unit
) : ListAdapter<DownloadedFile, DownloadListAdapter.DownloadViewHolder>(DiffCallback()) {

    /**
     * Erstellt einen neuen [DownloadViewHolder].
     * Wird von der RecyclerView aufgerufen, wenn ein neues ViewHolder-Objekt benötigt wird.
     *
     * @param parent {ViewGroup} Die ViewGroup, in die die neue View hinzugefügt wird.
     * @param viewType {Int} Der View-Type der neuen View
     * @return ein neuer [DownloadViewHolder].
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DownloadViewHolder {
        val binding =
            ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    /**
     * Bindet die Daten eines [DownloadedFile] an einen bestehenden [DownloadViewHolder].
     * Wird von der RecyclerView aufgerufen, um die Inhalte eines ViewHolders mit Daten zu füllen.
     *
     * @param holder {DownloadViewHolder} Der ViewHolder, dessen Inhalt aktualisiert werden soll.
     * @param position {Int} Die Position des Elements in der Liste.
     */
    override fun onBindViewHolder(
        holder: DownloadViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder-Klasse für einen Listeneintrag.
     * Enthält die UI-Elemente (Dateiname, Metadaten, Buttons) und die Logik zum Binden der Daten.
     */
    inner class DownloadViewHolder(
        private val binding: ItemDownloadBinding
    ) : ViewHolder(binding.root) {

        /**
         * Bietet die Logik zum Füllen der UI-Elemente des ViewHolders mit den Daten eines [DownloadedFile].
         *
         * @param item {DownloadedFile} Die [DownloadedFile]-Instanz, deren Daten angezeigt werden sollen.
         */
        @SuppressLint("SetTextI18n")
        fun bind(item: DownloadedFile) {
            binding.tvFilename.text = item.fileName
            binding.tvMeta.text = "${item.formattedSize} * ${item.formattedDate}"

            // Klick-Listener
            binding.btnOpen.setOnClickListener { onOpenClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    /**
     * Implementierung von [DiffUtil.ItemCallback] für effiziente Listenaktualisierungen.
     * Definiert, wie Listenelemente verglichen werden, um zu bestimmen, welche Views neu gezeichnet
     * werden müssen.
     */
    class DiffCallback : DiffUtil.ItemCallback<DownloadedFile>() {
        /**
         * Vergleicht zwei [DownloadedFile]-Objekte anhand ihrer ID, um zu entscheiden, ob es sich
         * um dasselbe Element handelt.
         *
         * @param oldItem {DownloadedFile} Das Element an der Position in der alten Liste
         * @param newItem {DownloadedFile} Das Element an der Position in der neuen Liste
         * @return `true`, wenn die Elemente dieselbe ID haben, sonst `false`.
         */
        override fun areItemsTheSame(oldItem: DownloadedFile, newItem: DownloadedFile): Boolean {
            return oldItem.id == newItem.id
        }

        /**
         * Vergleicht denn Inhalt zweier [DownloadedFile]-Objekte, wenn `areItemsTheSame` `true` zurückgegeben
         * hat.
         *
         * @param oldItem {DownloadedFile} Das Element aus der alten Liste
         * @param newItem {DownloadedFile} Das Element aus der neuen Liste
         * @return `true`, wenn sich der Inhalt der Objekte geändert hat, sonst `false`.
         */
        override fun areContentsTheSame(oldItem: DownloadedFile, newItem: DownloadedFile): Boolean {
            return oldItem == newItem
        }
    }
}
