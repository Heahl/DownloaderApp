package com.example.downloader_c.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.downloader_c.R
import com.example.downloader_c.callback.DownloadCallback
import com.example.downloader_c.utils.DownloadUtils
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

    // binder object used by the activity, to access this service
    private val binder = LocalBinder()

    // callback reference set by the activity, to receive updates
    private var callback: DownloadCallback? = null

    // executor for executing the download in a background thread
    private val executor = Executors.newSingleThreadExecutor()

    // unique ids for notifications and channel
    private val notificationId = 1
    private val channelId = "download_channel"

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
            // no url in intent? -> stop gracefully
            stopSelf()
            return START_NOT_STICKY
        }
        // starte den Download
        startDownload(url)
        // start_sticky -> service gets restartet after crash
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
        // 1. initialize displaying foreground notification
        val initialNotification = createNotification(0)

        startForeground(
            notificationId,
            initialNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        // 2. execute download in background
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
                        DownloadUtils.getFileNameFromUrl(
                            url,
                            connection.getHeaderField("Content-Disposition")
                        )
                    val file = File(getExternalFilesDir(null), fileName)

                    outputStream = FileOutputStream(file)

                    val totalSize = connection.contentLength.toLong()
                    var downloadedSize: Long = 0
                    val buffer = ByteArray(4096)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead

                        // only calc progress if size is known
                        val progress = if (totalSize > 0) {
                            ((downloadedSize * 100) / totalSize).toInt()
                        } else {
                            -1
                        }

                        // only update if progressed
                        if (progress >= 0) {
                            updateProgress(progress)
                        }
                    }

                    // download complete
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

        // foreground service updates the existing notification with notify()
        notificationManager.notify(notificationId, notification)

        callback?.onProgressUpdate(progress) // inform the activity
    }

    /**
     * Erstellt eine Benachrichtigung mit dem angegebenen Fortschritt.
     * Diese Benachrichtigung wird als Foreground-Benachrichtigung verwendet.
     */
    private fun createNotification(progress: Int): Notification {
        val title = if (progress >= 100) "Download abgeschlossen" else "Download läuft..."
        val contentText =
            if (progress >= 0) "Fortschritt: $progress%" else "Verbindung wird hergestellt..."

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, if (progress < 0) 0 else progress, progress < 0)
            .setOngoing(progress < 100)
            .build()
    }

    /**
     * Erstellt einen Notification Channel, um Benachrichtigungen anzeigen zu können.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Download-Fortschritt",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Zeigt den Fortschritt laufender Downloads an."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel) // registers the channel
    }

    /**
     * Wird aufgerufen, wenn der Service gestoppt wird.
     */
    override fun onDestroy() {
        super.onDestroy()
        // gracefully shutdown the executor
        executor.shutdownNow()
    }
}