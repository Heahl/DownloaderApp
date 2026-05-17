package com.example.downloader_c.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.downloader_c.domain.DownloadRepository
import com.example.downloader_c.domain.DownloadedFile
import java.io.File

/**
 * ViewModel für die MainActivity.
 *
 * Verwaltet den gesamten UI-Zustand der Download-Anwendung:
 * - Speichert die Liste heruntergeladener Dateien (überlebt Konfigurationsänderungen)
 * - Verfolgt aktiven Download-Status (für Fortschrittsanzeige)
 * - Handhabt Fortschrittsaktualisierungen (0 - 100%)
 * - Koordiniert Datenfluss zwischen Repository und UI-Komponenten
 *
 * @param repository Zentrales Repository für Download-Historie-Verwaltung
 */
class MainViewModel(private val repository: DownloadRepository) : ViewModel() {

    // internal mutable state for downloaded file
    private val _downloadedFiles = MutableLiveData<List<DownloadedFile>>()

    /**
     * Öffentliche, unveränderliche [LiveData] für UI Beobachter
     */
    val downloadedFiles: LiveData<List<DownloadedFile>> = _downloadedFiles

    // internal state for download activity flag
    private val _isDownloadActive = MutableLiveData<Boolean>(false)

    /**
     * Öffentliche [LiveData], um aktiven Download anzuzeigen
     */
    val isDownloadActive: LiveData<Boolean> = _isDownloadActive

    // internal progress state (0 - 100)
    private val _progress = MutableLiveData<Int>(0)

    /**
     * Öffentliche [LiveData] für Updates der Progress Bar
     */
    val progress: LiveData<Int> = _progress

    init {
        // init with persisted download history
        refreshDownloadList()
    }

    /**
     * Lädt die aktuelle Download-Historie aus dem Repository.
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
     * Wichtig: Muss nach Download-Ende immer explizit auf `false` gesetzt werden.
     *
     * @param active `true` während aktivem Download, `false` bei Inaktivität
     */
    fun setDownloadActive(active: Boolean) {
        // simple state update
        _isDownloadActive.value = active
    }

    /**
     * Aktualisiert den Download-Fortschritt.
     *
     * @param progressValue Prozentwert (0 - 100), muss im gültigen Bereich sein.
     */
    fun updateProgress(progressValue: Int) {
        // posValue() for thread-safety
        _progress.postValue(progressValue)
    }

    /**
     * Fügt eine neu heruntergeladene Datei zum System hinzu.
     *
     * ### Ablauf:
     * 1. Speichert Metadaten im Repository
     * 2. Aktualisiert UI durch List-Refresh
     * 3. Dateipfad wird automatisch relativ zum App-Speicher gesetzt
     *
     * @param file Die physische Datei, die persistiert werden soll
     */
    fun addDownloadedFile(file: File) {
        // persist file metadata in (path, timestamp, size) repo
        repository.addFile(file)
        // trigger ui update
        refreshDownloadList()
    }

    /**
     * Löscht eine Datei vollständig aus dem System.
     *
     * Ablauf:
     * 1. Delegiert die Löschung an das Repository (Metadaten + physische Datei)
     * 2. Aktualisiert UI durch List-Refresh, wenn Löschung erfolgreich war
     *
     * @param downloadedFile Das zu löschende Datei-Objekt aus der Historie
     */
    fun deleteFile(downloadedFile: DownloadedFile) {
        // only refresh ui if repository successfully removed the file
        if (repository.removeFile(downloadedFile.id)) {
            refreshDownloadList()
        }
    }
}
