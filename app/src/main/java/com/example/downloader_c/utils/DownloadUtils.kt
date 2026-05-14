package com.example.downloader_c.utils

import java.net.URLDecoder

/**
 * Utility-Klasse für allgemeine Download-Aufgaben wie MIME-Typ-Bestimmung
 * und Dateinamens-Extraktion.
 *
 * Durch das Auslagern dieser Logik wird die MainActivity entlastet und
 * der Code wird besser testbar.
 */
object DownloadUtils {

    /**
     * Bestimmt den MIME-Typ basierend auf der Dateierweiterung.
     *
     * @param fileName Der Name der Datei
     * @return Der MIME-Typ oder "application/octet-stream" als Fallback
     */
    fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    /**
     * Extrahiert den Dateinamen aus dem URL oder dem Content-Disposition-Header.
     *
     * @param url Die URL der Datei.
     * @param contentDisposition Der Content-Disposition-Header-Wert (optional).
     * @return Der extrahierte Dateiname oder ein Standardname.
     */
    fun getFileNameFromUrl(url: String, contentDisposition: String?): String {
        // 1. extract filenames from content-disposition-header
        contentDisposition?.let {
            val filenameStarRegex = Regex("filename\\*\\s*=\\s*[^']+'[^']+'([^\";]+)")
            val starMatch = filenameStarRegex.find(it)
            starMatch?.groupValues?.get(1)?.let { name ->
                return try {
                    URLDecoder.decode(name, "UTF-8")
                } catch (_: Exception) {
                    name
                }
            }

            val filenameRegex = Regex("filename\\s*=\\s*\"?([^\";]+)\"?")
            val matchResult = filenameRegex.find(it)
            matchResult?.groupValues?.get(1)?.let { extractedFilename ->
                return extractedFilename.trim(' ', '"', '\'')
            }
        }

        // 2. extract filename from the url
        val lastSlashIndex = url.lastIndexOf('/')
        if (lastSlashIndex > -1 && lastSlashIndex < url.length - 1) {
            val potentialFilename = url.substring(lastSlashIndex + 1)
            val cleanFilename = potentialFilename.split('?')[0]
            if (cleanFilename.isNotEmpty()) {
                return cleanFilename
            }
        }

        return "downloaded_file"
    }
}