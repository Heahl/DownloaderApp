package com.example.downloader_c.data

import com.example.downloader_c.domain.DownloadCancelledException
import com.example.downloader_c.domain.DownloadException
import com.example.downloader_c.domain.FileDownloader
import com.example.downloader_c.domain.FileStorageException
import com.example.downloader_c.domain.HttpException
import com.example.downloader_c.domain.NetworkConnectionException
import com.example.downloader_c.domain.NetworkSecurityException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * HttpURLConnection-basierte Implementierung des [FileDownloader].
 *
 * Downloadet Dateien von einer URL in ein Zielverzeichnis mit Fortschrittsanzeige.
 *
 * ### Eigenschaften:
 * - Unterstützt Fortschrittsaktualisierungen während des Downloads
 * - Handhabt unbekannte Content-Length
 * - Nutzt sichere Ressourcenverwaltung mit use()-Funktionen
 * - Validiert HTTP-Antwortcodes
 * - Extrahiert Dateinamen aus URL oder Content-Disposition
 */
class HttpFileDownloader : FileDownloader {

    override fun downloadFile(
        url: String,
        targetDir: File?,
        onProgress: (Int) -> Unit,
    ): File {
        // Parse URL and validate protocol
        val urlObject = try {
            URL(url)
        } catch (e: Exception) {
            throw DownloadException("Ungültige URL: $url", e)
        }

        if (urlObject.protocol != "http" && urlObject.protocol != "https") {
            throw DownloadException("Nur HTTP und HTTPS werden unterstützt")
        }

        val connection = (urlObject.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }

        var downloadedFile: File? = null

        try {
            // handle http errors with specific exceptions
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw HttpException(responseCode, connection.responseMessage)
            }

            // extract filename from url or content disposition header
            val fileName = DownloadUtils.getFileNameFromUrl(
                url,
                connection.getHeaderField("Content-Disposition"),
            )

            // validate target dir
            if (targetDir == null || (!targetDir.exists()) || (!targetDir.isDirectory)) {
                throw FileStorageException("Ungültiges Zielverzeichnis")
            }

            val file = File(targetDir, fileName)
            downloadedFile = file

            connection.inputStream.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val totalSize = connection.contentLengthLong
                    var downloadedSize: Long = 0
                    val buffer = ByteArray(8192) // larger buffer for efficiency
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // check for interruption
                        if (Thread.currentThread().isInterrupted) {
                            throw DownloadCancelledException("Download wurde abgebrochen")
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead

                        // calc and report progress
                        if (totalSize > 0) {
                            val progress = ((downloadedSize * 100) / totalSize).toInt()
                            onProgress(progress)
                        } else {
                            // unknown content length - (indeterminate progress)
                            onProgress(-1)
                        }
                    }
                }
            }
            return file
        } catch (e: UnknownHostException) {
            deletePartialFile(downloadedFile)
            throw NetworkConnectionException("Keine Internetverbindung", e)
        } catch (e: InterruptedIOException) {
            deletePartialFile(downloadedFile)
            throw DownloadCancelledException("Download wurde abgebrochen", e)
        } catch (e: SecurityException) {
            deletePartialFile(downloadedFile)
            throw NetworkSecurityException(
                "Sicherheitsfehler beim Download (z.B. Cleartext nicht erlaubt)",
                e
            )
        } catch (e: HttpException) {
            deletePartialFile(downloadedFile)
            throw e
        } catch (e: FileStorageException) {
            throw e
        } catch (e: IOException) {
            deletePartialFile(downloadedFile)
            throw DownloadException("Fehler beim Dateidownload: ${e.message}", e)
        } catch (e: Exception) {
            deletePartialFile(downloadedFile)
            throw DownloadException("Unerwarteter Fehler: ${e.message}", e)
        } finally {
            // always disconnect to free resources
            connection.disconnect()
        }
    }

    /**
     * Löscht die Datei, wenn der Download fehlgeschlagen ist oder abgebrochen wurde.
     */
    private fun deletePartialFile(file: File?) {
        try {
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            // Ignore errors during deletion
        }
    }
}
