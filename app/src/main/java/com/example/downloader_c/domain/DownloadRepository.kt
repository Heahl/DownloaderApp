package com.example.downloader_c.domain

import java.io.File

/**
 * Interface für die Verwaltung der Download-Historie.
 * Entspricht dem Dependency Inversion Principle.
 */
interface DownloadRepository {
    /**
     * Gibt alle gespeicherten Downloads zurück.
     */
    fun getFiles(): List<DownloadedFile>

    /**
     * Fügt eine neue Datei zur Historie hinzu.
     */
    fun addFile(file: File)

    /**
     * Entfernt eine Datei aus der Historie.
     */
    fun removeFile(fileId: String): Boolean
}
