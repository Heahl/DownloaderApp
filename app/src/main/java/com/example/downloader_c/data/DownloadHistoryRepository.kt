package com.example.downloader_c.data

import android.content.Context
import androidx.core.net.toUri
import kotlinx.serialization.json.Json
import java.io.File

class DownloadHistoryRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val historyFile = File(context.filesDir, "download_history.json")

    // Thread-safe Liste im Speicher als Cache
    private val _files = mutableListOf<DownloadedFile>()

    init {
        loadFromDisk()
    }

    fun getFiles(): List<DownloadedFile> = _files.toList()

    fun addFile(file: File) {
        val downloadedFile = DownloadedFile(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            mimeType = context.contentResolver.getType(file.toUri())
        )

        // neueste zuerst
        _files.add(0, downloadedFile)
        saveToDisk()
    }

    fun removeFile(fileId: String): Boolean {
        val index = _files.indexOfFirst { it.id == fileId }
        if (index != -1) {
            File(_files[index].filePath).delete()
            _files.removeAt(index)
            saveToDisk()
            return true
        }
        return false
    }

    private fun loadFromDisk() {
        if (historyFile.exists()) {
            try {
                val content = historyFile.readText()
                if (content.isNotBlank()) {
                    val list = json.decodeFromString<List<DownloadedFile>>(content)
                    _files.clear()
                    _files.addAll(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveToDisk() {
        try {
            val content = json.encodeToString(_files)
            historyFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}