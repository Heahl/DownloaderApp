package com.example.downloader_c

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Startet Foreground Service für den Download von Dateien.
 *
 * Dieser Service läuft im Hintergrund, auch wenn die Activity nicht aktiv ist.
 * - startet als Foreground Service, was eine Benachrichtigung erfordert.
 * - führt den Download in einem separaten Thread aus und kommuniziert den Fortschritt und das Ergebnis
 * über das DownloadCallback-Interface zurück zur Activity
 */
class DownloadService : Service() {

    // Binder-Objekt, das von der Activity verwendet wird, um auf diesen Service zuzugreifen
    private val binder = LocalBinder()

    // Callback-Referenz, die von der Activity gesetzt wird, um Updates zu erhalten
    private var callback: DownloadCallback? = null

    // Executor für die Durchführung des Download-Vorgangs in einem Hintergrundthread
    private val executor = Executors.newSingleThreadExecutor()

    // Eindeutige IDs für Benachrichtigungen und Kanäle
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "download_channel"

    /**
     * Lokaler Binder, der den Activity Zugriff auf die Service-Instanz gewährt.
     */
    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    /**
     * Wird aufgerufen, wenn eine Activity versucht, sich an diesen Service zu binden.
     * Gibt das LocalBinder-Objekt zurück.
     */
    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Legt den Callback fest, den der Service verwendet, um der Activity den Download-Fortschritt
     * und das Ergebnis mitzuteilen.
     *
     * @param callback {DownloadCallback?} Das DownloadCallback-Objekt der Activity.
     */
    fun setCallback(callback: DownloadCallback?) {
        this.callback = callback
    }

    /**
     * Wird beim Erstellen des Service aufgerufen.
     * Hier wird der Notification Channel erstellt.
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Wird aufgerufen, wenn der Service über startService gestartet wird.
     * Extrahiert den URL aus dem Intent und startet den Download-Prozess.
     *
     * @param intent {Intent?} Der Intent, der an startService übergeben wurde.
     * @param flags {Int} Zusätzliche Flags
     * @param startId {Int} Die ID der Startanfrage
     * @return START_STICKY, damit der Service bei einem Absturz neu gestartet wird.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: run {
            // falls keine URL im Intent ist, stoppe den Service sauber
            stopSelf()
            return START_NOT_STICKY
        }
        // starte den Download
        startDownload(url)
        // start_sticky, damit der Service bei einem Absturz neu gestartet wird
        return START_STICKY
    }

    /**
     * Startet den Download in einem separaten Thread.
     * Zeigt eine Foregroun-Benachrichtigung an und aktualisiert diese.
     * Informiert die Activity über den Fortschritt und das Ergebnis.
     *
     * @param url {String} Der URL der herunterzuladenden Datei
     */
    private fun startDownload(url: String) {
        // 1. Initiale Foreground-Benachrichtigung anzeigen
        val initialNotification = createNotification(0)

        startForeground(
            NOTIFICATION_ID,
            initialNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        // 2. Download im Hintergrund ausführen
        executor.execute {
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            var connection: HttpURLConnection? = null

            try {
                val urlObject = URL(url)
                connection = urlObject.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.inputStream

                    val fileName =
                        getFileNameFromUrl(url, connection.getHeaderField("Content-Disposition"))
                    val file = File(getExternalFilesDir(null), fileName)

                    outputStream = FileOutputStream(file)

                    val totalSize = connection.contentLength.toLong()
                    var downloadedSize: Long = 0
                    val buffer = ByteArray(4096)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead

                        // Progress nur bei bekannter Größe berechnen
                        val progress = if (totalSize > 0) {
                            ((downloadedSize * 100) / totalSize).toInt()
                        } else {
                            -1
                        }

                        // Nur bei gültigem Progress updaten
                        if (progress >= 0) {
                            updateProgress(progress)
                        }
                    }

                    // Download abgeschlossen
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    callback?.onDownloadComplete(file)
                    stopSelf()
                } else {
                    throw Exception("HTTP-Fehler ${connection.responseCode}: ${connection.responseMessage}")
                }
            } catch (e: Exception) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                callback?.onDownloadError(e.message ?: "Unbekannter Fehler")
                stopSelf()
            } finally {
                inputStream?.close()
                outputStream?.close()
                connection?.disconnect()
            }
        }
    }

    /**
     * Aktualisiert die Foreground-Benachrichtigung mit dem aktuellen Fortschritt und informiert die
     * Activity über den Fortschritt.
     *
     * @param progress {Int} Der aktuelle Download-Fortschritt in Prozent (0-100)
     */
    private fun updateProgress(progress: Int) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(progress)
        notificationManager.notify(
            NOTIFICATION_ID,
            notification
        ) // aktualisiert die Benachrichtigung
        callback?.onProgressUpdate(progress) // informiert die Activity
    }

    /**
     * Erstellt eine Benachrichtigung mit dem angegebenen Fortschritt.
     * Diese Benachrichtigung wird als Foreground-Benachrichtigung verwendet.
     *
     * @param progress {Int} Der aktuelle Fortschritt in Prozent (0-100)
     * @return {Notification} Die erstellte Notification-Instanz
     */
    private fun createNotification(progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download läuft...")
            .setContentText("Fortschritt: $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true) // verhindert, dass der Nutzer die Benachrichtigung verwirft
            .build()
    }

    /**
     * Erstellt einen Notification Channel, um Benachrichtigungen anzeigen zu können.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Download-Fortschritt",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt den Fortschritt laufender Downloads an."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel) // registriert den Kanal
    }

    /**
     * Extrahiert den Dateinamen aus dem URL oder dem Content-Disposition-Header.
     * @param url {String} Die URL der Datei.
     * @param contentDisposition {String?} Der Content-Disposition-Header-Wert (kann null sein).
     * @return {String} Der extrahierte Dateiname oder ein Standardname.
     */
    internal fun getFileNameFromUrl(url: String, contentDisposition: String?): String {
        // 1. Dateinamen aus dem Content-Disposition-Header extrahieren
        contentDisposition?.let {
            // Zuerst filename* (RFC 5987) versuchen
            val filenameStarRegex = Regex("filename\\*\\s*=\\s*[^']+'[^']+'([^\";]+)")
            val starMatch = filenameStarRegex.find(it)
            starMatch?.groupValues?.get(1)?.let { name ->
                return java.net.URLDecoder.decode(name, "UTF-8")
            }

            // Fallback: normales filename
            val filenameRegex = Regex("filename\\s*=\\s*\"?([^\";]+)\"?")
            val matchResult = filenameRegex.find(it)
            matchResult?.groupValues?.get(1)?.let { extractedFilename ->
                return extractedFilename.trim(' ', '"', '\'')
            }
        }

        // 2. Den Dateinamen aus dem URL extrahieren
        val lastSlashIndex = url.lastIndexOf('/')
        if (lastSlashIndex > -1 && lastSlashIndex < url.length - 1) {
            val potentialFilename = url.substring(lastSlashIndex + 1)
            // Entferne Query-Parameter (z.B. ?v=123) aus dem Dateinamen
            val cleanFilename = potentialFilename.split('?')[0]
            if (cleanFilename.isNotEmpty()) {
                return cleanFilename
            }
        }

        // 3. Falls alles fehlschlägt: Gib einen Standardnamen zurück
        return "downloaded_file"
    }

    /**
     * Wird aufgerufen, wenn der Service gestoppt wird.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Den Executor sauber herunterfahren
        executor.shutdownNow()
    }
}