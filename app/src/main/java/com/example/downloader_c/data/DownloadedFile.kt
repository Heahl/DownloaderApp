package com.example.downloader_c.data

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class DownloadedFile(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
    val mimeType: String? = null
) {
    // ui helper
    val formattedDate: String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(downloadedAt))

    val formattedSize: String
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "%.1f KB".format(fileSize / 1024.0)
            else -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
        }
}