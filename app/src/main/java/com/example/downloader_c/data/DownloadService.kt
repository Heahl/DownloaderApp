package com.example.downloader_c.data

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
import com.example.downloader_c.domain.DownloadCallback
import com.example.downloader_c.domain.FileDownloader
import java.util.concurrent.Executors

/**
 * Foreground Service für den Download.
 *
 * ### Eigenschaften:
 * - Läuft als Foreground-Service 
 * - Nutzt Notification 
 * - Implementiert FileDownloader-Interface
 * - Unterstützt Fortschrittsaktualisierungen
 * - Überlebt Konfigurationsänderungen durch Service-Lifecycle
 *
 * ### Nutzung:
 * 1. Starten durch startService() mit URL-Parameter
 * 2. Binden durch bindService() für Fortschrittsupdates
 * 3. Stoppen durch stopSelf() nach Download-Ende
 * 4. Verwenden von DownloadCallback für UI-Integration
 */
class DownloadService : Service() {
    // binder object for client connections

    private val binder = LocalBinder()

    // reference to ui callback
    private var callback: DownloadCallback? = null

    // thread pool for download operations
    private val executor = Executors.newSingleThreadExecutor()

    // interface-based dependency 
    private lateinit var downloader: FileDownloader

    // notification constants for consistent identification
    private val notificationId = 1
    private val channelId = "download_channel"

    /**
     * Binder für die Kommunikation zwischen Activity und Service.
     *
     * Ermöglicht das Herstellen einer Bindung zum Service und den Zugriff auf dessen Methoden.
     * Wird von Activities verwendet, um eine Referenz auf den Service zu erhalten.
     */
    inner class LocalBinder : Binder() {
        /**
         * Gibt eine Referenz auf den Service zurück.
         *
         * Wird von der bindenden Activity verwendet, um auf Service-Methoden zuzugreifen.
         *
         * @return Referenz auf den DownloadService
         */
        fun getService(): DownloadService = this@DownloadService
    }

    /**
     * Wird aufgerufen, wenn die Activity eine Bindung zum Service herstellt.
     *
     * Gibt den Binder zurück, der für die Kommunikation zwischen Activity und Service verwendet wird.
     *
     * @param intent Der Intent, der die Bindung anfordert
     * @return Binder-Objekt für die Kommunikation
     */
    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Setzt den Callback für Fortschritts- und Ergebnis-Updates.
     *
     * Der Callback wird verwendet, um die UI über den Download-Status zu informieren.
     * Sollte von der bindenden Activity gesetzt werden, um Fortschrittsupdates zu erhalten.
     *
     * @param callback Der zu setzende DownloadCallback (kann null sein)
     */
    fun setCallback(callback: DownloadCallback?) {
        this.callback = callback
    }

    /**
     * Wird beim Erstellen des Service aufgerufen.
     *
     * Initialisiert den Service und erstellt den Benachrichtigungskanal.
     */
    override fun onCreate() {
        super.onCreate()
        // initialize with HttpFileDownloader
        downloader = HttpFileDownloader()
        createNotificationChannel()
    }

    /**
     * Wird aufgerufen, wenn der Service gestartet wird.
     *
     * Startet den Download-Vorgang mit der übergebenen URL. Wenn keine URL vorhanden ist, wird der
     * Service beendet.
     *
     * @param intent Der Intent, der den Service startet (enthält den URL)
     * @param flags Zusätzliche Flags für den Startvorgang
     * @param startId Eindeutige ID für diesen Startvorgang
     * @return START_STICKY, um Neustart nach unerwartetem Beenden zu ermöglichen
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // extract url from intent
        val url = intent?.getStringExtra("url") ?: run {
            // no url provided: invalid start command -> stop immediately
            stopSelf()
            return START_NOT_STICKY
        }
        startDownload(url)
        return START_STICKY
    }

    /**
     * Startet den Download-Vorgang für die angegebene URL.
     *
     * Initialisiert den Download in einem Hintergrund-Thread und zeigt eine Benachrichtigung an. 
     *
     * @param url Der zu downloadende URL
     */
    private fun startDownload(url: String) {
        // create initial notification to start as foreground service
        // required by android to prevent service from being killed
        val initialNotification = createNotification(0)

        // start as foreground service with data sync type (android 10+)
        // must call foreground service within 5 sec (android 9+)
        startForeground(
            notificationId,
            initialNotification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        // execute download in background thread to avoid anr
        executor.execute {
            try {
                // download file with progress callback
                val file = downloader.downloadFile(
                    url,
                    getExternalFilesDir(null)
                ) { progress ->
                    if (progress >= 0) updateProgress(progress)
                }

                // download completed successfully -> remove foreground status and notify ui
                stopForeground(STOP_FOREGROUND_REMOVE)
                callback?.onDownloadComplete(file)
                stopSelf()
            } catch (e: Exception) {
                // error during download -> remove foreground status and notify ui
                stopForeground(STOP_FOREGROUND_REMOVE)
                callback?.onDownloadError(e.message ?: "Unbekannter Fehler")
                stopSelf()
            }
        }
    }

    /**
     * Aktualisiert den Download-Fortschritt.
     *
     * Aktualisiert sowohl die Benachrichtigung als auch den UI-Callback mit dem aktuellen Fortschritt.
     *
     * @param progress Der aktuelle Fortschritt (0-100)
     */
    private fun updateProgress(progress: Int) {
        // update notification through notification manager
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, createNotification(progress))

        // notify ui through callback (if set)
        callback?.onProgressUpdate(progress)
    }

    /**
     * Erstellt eine Benachrichtigung für den Download-Fortschritt.
     *
     * Generiert eine Benachrichtigung, die den aktuellen Fortschritt anzeigt.
     *
     * @param progress Der aktuelle Fortschritt (0-100, negativ für indeterminate)
     * @return Die erstellte Notification
     */
    private fun createNotification(progress: Int): Notification {
        // create title based on progress state
        val title = if (progress >= 100)
            getString(R.string.download_finished)
        else getString(R.string.download_running)

        // format content text with progress information
        val contentText =
            if (progress >= 0) getString(
                R.string.download_progress,
                progress
            ) else getString(R.string.connecting)

        // build notification with appropriate properties
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, if (progress < 0) 0 else progress, progress < 0)
            .setOngoing(progress < 100) // ongoing until complete
            .build()
    }

    /**
     * Erstellt den Benachrichtigungskanal 
     *
     * Ab Android 8.0 (API 26) sind Notification Channels erforderlich, um Benachrichtigungen
     * anzeigen zu können. Dieser Channel wird einmalig erstellt und bleibt für die gesamte
     * Lebensdauer der App bestehen.
     */
    private fun createNotificationChannel() {
        // create channel with low importance
        val channel = NotificationChannel(
            channelId,
            "Download-Fortschritt",
            NotificationManager.IMPORTANCE_LOW
        )

        // get notification manager and create cannel - no op if channel already exists
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Wird beim Beenden des Service aufgerufen.
     *
     * Bereinigt alle Ressourcen und beendet den Executor ordnungsgemäß.
     */
    override fun onDestroy() {
        // first clean up resources
        executor.shutdownNow()
        // then let the superclass do its cleanup
        super.onDestroy()
    }
}
