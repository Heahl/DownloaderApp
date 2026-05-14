package com.example.downloader_c.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.downloader_c.data.DownloadHistoryRepository
import com.example.downloader_c.data.DownloadedFile
import java.io.File

/**
 * ViewModel für die MainActivity.
 *
 * Verwaltet den gesamten UI-Zustand der DownloadApp:
 * - Speichert die Liste heruntergeladener Dateien (überlebt Konfigurationsänderungen)
 * - Verfolgt aktiven Download-Status (für Fortschrittsanzeige)
 * - Handhabt Fortschrittsaktualisierungen (0-100%)
 * - Koordiniert Datenfluss zwischen Repository und UI-Komponenten
 *
 * @param repository Zentrales Repository für Download-Historie-Verwaltung
 */
class MainViewModel(private val repository: DownloadHistoryRepository) : ViewModel() {

    // internal mutable state for downloaded files (backing property)
    private val _downloadedFiles = MutableLiveData<List<DownloadedFile>>()

    // exposed immutable liveData for ui observers (prevents direct mutation)
    val downloadedFiles: LiveData<List<DownloadedFile>> = _downloadedFiles

    // internal state for download activity flag
    private val _isDownloadActive = MutableLiveData<Boolean>(false)

    // public liveData indicating active download operations (triggers ui update)
    val isDownloadActive: LiveData<Boolean> = _isDownloadActive

    // livedata for current progress (0-100)
    private val _progress = MutableLiveData<Int>(0)

    // public liveData for progress bar updates
    val progress: LiveData<Int> = _progress

    init {
        // initialize with persisted download history on viewModel creation
        refreshDownloadList()
    }

    /**
     * Lädt die aktuelle Download-Historie aus dem Repository
     *
     * Setzt den Zustand von _downloadedFiles auf die neueste Liste aus dem Repository.
     * Wird automatisch nach Konfigurationsänderungen aufgerufen, um UI-Konsistenz zu gewährleisten.
     */
    fun refreshDownloadList() {
        // repo access is synchronous
        _downloadedFiles.value = repository.getFiles()
    }

    /**
     * Aktualisiert den Download-Aktivitätsstatus.
     *
     * @param active `true` während aktivem Download, `false` bei Inaktivität
     * **Wichtig**: Muss immer explizit auf false gesetzt werden nach Download-Ende
     */
    fun setDownloadActive(active: Boolean) {
        // simple state update
        _isDownloadActive.value = active
    }

    /**
     * Aktualisiert den Download-Fortschritt.
     *
     * @param progressValue Prozentwert (0-100), muss im gültigen Bereich sein
     */
    fun updateProgress(progressValue: Int) {
        // postValue() instead of setValue() to handle background thread calls
        _progress.postValue(progressValue)
    }

    /**
     * Fügt eine neue Datei zum Repository hinzu und aktualisiert die Liste.
     *
     * ### Ablauf:
     * 1. Speichert Metadaten im Repository
     * 2. Aktualisiert UI durch List-Refresh
     * 3. Dateipfad wird automatisch relativ zum App-Speicher gesetzt
     *
     * @param file Die physische Datei, die persistiert werden soll
     */
    fun addDownloadedFile(file: File) {
        // persist file metadata in repo
        repository.addFile(file)
        // trigger ui update with new list
        refreshDownloadList()
    }

    /**
     * Löscht eine Datei aus dem Repository und dem Dateisystem.
     *
     * ### Ablauf:
     * 1. Entfernt Metadaten aus dem Repository
     * 2. Löscht physische Datei
     * 3. Aktualisiert UI durch List-Refresh
     *
     * @param downloadedFile Das zu löschende Datei-Objekt aus der Historie
     */
    fun deleteFile(downloadedFile: DownloadedFile) {
        try {
            // only attempt deletion if repository entry was removed
            if (repository.removeFile(downloadedFile.id)) {
                // delete actual file from storage
                File(downloadedFile.filePath).delete()
                // refresh ui
                refreshDownloadList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
