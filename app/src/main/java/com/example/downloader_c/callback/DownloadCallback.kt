package com.example.downloader_c.callback

import java.io.File

/**
 * Callback-Interface für die Kommunikation zwischen Service und Activity.
 * Wird von der Activity implementiert, um Download-Updates zu erhalten.
 */
interface DownloadCallback {
    fun onProgressUpdate(progress: Int)
    fun onDownloadComplete(file: File)
    fun onDownloadError(message: String)
}