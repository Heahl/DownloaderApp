package com.example.downloader_c.domain

import java.io.File

/**
 * Interface für den Dateidownload.
 * Entkoppelt die Netzwerk-Logik vom Service.
 */
interface FileDownloader {
    /**
     * Lädt eine Datei von der [url] herunter und speichert sie in [DownloadRepository].
     * @param onProgress Callback für den Fortschritt (0-100).
     * @return Das File-Objekt nach Abschluss oder wirft eine Exception bei Fehler.
     */
    fun downloadFile(
        url: String,
        targetDir: File?,
        onProgress: (Int) -> Unit
    ): File
}
