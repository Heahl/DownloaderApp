package com.example.downloader_c

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.downloader_c.databinding.ActivityMainBinding
import java.io.File
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.downloader_c.data.DownloadHistoryRepository
import com.example.downloader_c.data.DownloadListAdapter
import com.example.downloader_c.data.DownloadedFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder


/**
 * Main Activity der Downloader-C-App
 *
 * Diese Activity ist für die Benutzeroberfläche zuständig:
 * - Eingabe des URLs
 * - Starten des Downloads durch DownloadService
 * - Anzeigen des Fortschritts
 * - Öffnen der heruntergeladenen Datei
 *
 * Sie bindet sich an den DownloadService, um Fortschritts-Updates zu erhalten.
 * Der Download selbst wird über einen Started Foreground Service durchgeführt, um auch dann
 * fortzufahren, wenn die Activity nicht im Vordergrund ist.
 * Die heruntergeladenen Dateien und deren Metadaten werden lokal gespeichert.
 */
class MainActivity : AppCompatActivity(), DownloadCallback {
    // ViewBinding für die UI-Elemente
    private lateinit var binding: ActivityMainBinding

    // repository für die verwaltung der Download-historie
    private lateinit var repository: DownloadHistoryRepository

    // adapter für die recyclerview zur anzeige der Download-historie
    private lateinit var adapter: DownloadListAdapter

    // Referenz auf den Service
    private var downloadService: DownloadService? = null

    // Flag zum Überwachen des Bindungsstatus
    private var isBound = false

    // Flag zum Überwachen des Downloadstatus
    private var isDownloadActive = false

    // Launcher für die runtime permission request für benachrichtigungen
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Berechtigung erteilt, Download kann starten
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) startDownloadService(url)
        } else {
            Toast.makeText(this, "Benachrichtigungs-Berechtigung erforderlich", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * Implementierung des ServiceConnection-Interfaces.
     * Wird benötigt, um eine Bindung zum Service herzustellen und zu verwalten.
     */
    private val connection = object : ServiceConnection {
        /**
         * Wird aufgerufen, wenn die Verbindung zum Service erfolgreich hergestellt wurde.
         * Hier wird die Service-Instanz abgerufen und der Callback gesetzt.
         */
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DownloadService.LocalBinder
            // Stellt sicher, dass der Service den Callback der Activity kennt, um Fortschritts-
            // Updates senden zu können
            downloadService = binder.getService()
            downloadService?.setCallback(this@MainActivity)
            isBound = true
            updateUiState(isDownloadActive)
        }

        /**
         * Wird aufgerufen, wenn die Verbindung zum Service unerwartet getrennt wurde (zB. durch Absturz)
         */
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            downloadService = null // setzt die Referenz auf null, wenn die Verbindung verloren geht
        }
    }

    /**
     * Wird beim ersten Erstellen der Activity aufgerufen.
     * Initialisiert ViewBinding und setzt den Click-Listener für den Download-Button.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // initialisiert ViewBinding. binding.root ist das Root-Element des Layouts.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // repository initialisieren
        repository = DownloadHistoryRepository(this)

        // recyclerview setup
        setupRecyclerView()

        // initiale liste laden
        updateDownloadList()

        // Setzt den OnClickListener für den btnDownload
        binding.btnDownload.setOnClickListener {
            val url = binding.etUrl.text.toString().trim() // holt die URL
            if (url.isNotEmpty()) { // url validierung
                checkNotificationPermissionAndStart(url)
            } else {
                Toast.makeText(this, "Bitte URL eingeben", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Prüft die Benachrichtigungs-Berechtigung und startet den Download oder fordert die Berechtigung an.
     *
     * @param url {String} Die zu startende Download-URL.
     */
    private fun checkNotificationPermissionAndStart(url: String) {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED -> {
                // Berechtigung erteilt -> starte den Service
                startDownloadService(url)
            }

            else -> {
                // Berechtigung nicht erteilt -> fordere sie an
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Richtet die RecyclerView ein, die die Download-Historie anzeigt.
     * Konfiguriert den Adapter mit Click-Handlern für "Öffnen" und "Löschen".
     */
    private fun setupRecyclerView() {
        // initialisiert den Adapter mit Lambda-Funktionen für Klick-Events
        adapter = DownloadListAdapter(
            onOpenClick = { file -> openFile(File(file.filePath)) },    // öffnet die Datei
            onDeleteClick = { file -> deleteFile(file) }            // löscht die Datei
        )

        // aktualisiert die Sichtbarkeit des leeren Listen-Textes und des Recyclers
        binding.tvEmptyList.visibility =
            if (repository.getFiles().isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerDownloads.visibility =
            if (repository.getFiles().isEmpty()) View.GONE else View.VISIBLE

        // wendet den adapter, layoutManager und animator auf die recyclerview an
        binding.recyclerDownloads.apply {
            this.adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = DefaultItemAnimator()
        }
    }

    /**
     * Lädt die aktuelle Liste der heruntergeladenen Dateien aus dem Repository und aktualisiert die
     * RecyclerView. Passt auch die Sichtbarkeit des leeren Listen-Textes an.
     */
    private fun updateDownloadList() {
        val files = repository.getFiles()   // holt die Dateiliste vom repository
        adapter.submitList(files)           // übergibt die Liste dem adapter

        // Sichtbarkeit aktualisieren
        binding.tvEmptyList.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerDownloads.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Zeigt einen Bestätigungsdialog an und löscht die Datei, wenn der Nutzer zustimmt.
     * Entfernt den Eintrag sowohl aus dem Repository als auch vom Dateisystem.
     *
     * @param downloadedFile {DownloadedFile} Das zu löschende [DownloadedFile]-Objekt.
     */
    private fun deleteFile(downloadedFile: DownloadedFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Datei löschen?")
            .setMessage("Möchtest du '${downloadedFile.fileName}' wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                // versucht den eintrag aus dem repository zu löschen
                if (repository.removeFile(downloadedFile.id)) {
                    File(downloadedFile.filePath).delete()
                    // aktualisiere die Listenansicht
                    updateDownloadList()
                    Toast.makeText(this, "Gelöscht", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null) // Schließt den Dialog ohne aktion
            .show()
    }

    /**
     * Startet den DownloadService als Started Foreground Service und bindet sich danach.
     * Trennt die Verantwortung für das Starten (mit URL) und die Kommunikation (über Bindung).
     *
     * @param url {String} Der URL der herunterzuladenden Datei
     */
    private fun startDownloadService(url: String) {
        // 1. Intent für StartService: Enthält URL
        // Startet den Service und übergibt den URL als Parameter
        val startIntent = Intent(this, DownloadService::class.java)
        startIntent.putExtra("url", url)
        startService(startIntent)

        // 2. Intent für bindService: Separat erstellt, um Verwirrung zu vermeiden
        // Dient nur der Herstellung der Binding und nicht der Datenübergabe
        // URL ist bereits über startService bekannt
        val bindIntent = Intent(this, DownloadService::class.java)
        // bindIntent enthält KEINE Extras.
        // Kommunikation (Fortschritt, Status) erfolg über das Callback-Interface nach der Bindung.
        bindService(bindIntent, connection, BIND_AUTO_CREATE)

        // setze downloadstatus auf aktiv
        isDownloadActive = true
        // aktualisiere UI, um aktiven Download anzuzeigen
        updateUiState(isDownloadActive)
    }

    /**
     * Aktualisiert den Zustand der Benutzeroberfläche basierend auf dem Download-Status.
     *
     * @param isActive {Boolean} Gibt an, ob ein Download läuft oder nicht (true = Download läuft)
     */
    private fun updateUiState(isActive: Boolean) {
        // deaktiviere/ aktiviere den Download-btn
        binding.btnDownload.isEnabled = !isActive
        // text der btn ändern
        binding.btnDownload.text = if (isActive) "Download läuft..." else "Download starten"
        // et-url deaktivieren
        binding.etUrl.isEnabled = !isActive
    }

    /**
     * Wird vom Service aufgerufen, wenn sich der Download-Fortschritt ändert.
     * Wird nicht automatisch im UI-Thread ausgeführt.
     * UI-Code muss daher innerhalb von runOnUiThread ausgeführt werden.
     */
    @SuppressLint("SetTextI18n")
    override fun onProgressUpdate(progress: Int) {
        runOnUiThread {
            if (progress >= 0) {
                binding.progressBar.isIndeterminate = false
                // Aktualisiere die ProgressBar mit dem neuen Fortschritt
                binding.progressBar.progress = progress
                // Aktualisiere den Text, der den Fortschritt anzeigt
                binding.tvProgress.text = "$progress%"
            } else {
                binding.progressBar.isIndeterminate = true
                binding.tvProgress.text = getString(R.string.l_dt)
            }
        }
    }

    /**
     * Wird vom Service aufgerufen, wenn der Download erfolgreich abgeschlossen wurde.
     * Öffnet die heruntergeladene Datei.
     */
    override fun onDownloadComplete(file: File) {
        runOnUiThread {
            // setze den Fortschritt auf 100%
            binding.progressBar.progress = 100
            binding.tvProgress.text = getString(R.string._100)
            Toast.makeText(this, "Download abgeschlossen", Toast.LENGTH_LONG).show()
            // Füge die Datei zum Repository hinzu, damit sie im RecyclerView angezeigt wird
            repository.addFile(file)
            updateDownloadList()
            // Öffne die heruntergeladene Datei
            openFile(file)
            // setze den Download-status auf inaktiv
            isDownloadActive = false
            // aktualisiere die ui, um den abgeschlossenen Download anzuzeigen
            updateUiState(false)
        }
    }

    /**
     * Wird vom Service aufgerufen, wenn ein Fehler während des Downloads aufgetreten ist.
     */
    override fun onDownloadError(message: String) {
        runOnUiThread {
            Toast.makeText(this, "Fehler: $message", Toast.LENGTH_LONG).show()
            // setze den download-status auf inaktiv
            isDownloadActive = false
            // aktualisiere die UI, um den Fehler anzuzeigen
            updateUiState(false)
        }
    }

    /**
     * Öffnet die heruntergeladene Datei mit einer geeigneten externen App.
     * Verwendet FileProvider für sicheren Dateizugriff.
     *
     * @param file {File} Die heruntergeladene Datei
     */
    private fun openFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        // Bestimme den MIME-Typ basierend auf der Dateierweiterung
        val mimeType = getMimeType(file.name) ?: "application/octet-stream"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // prüft, ob eine App existiert, die den Intent verarbeiten kann
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Keine App zum Öffnen der Datei gefunden", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * Bestimmt den MIME-Typ basierend auf der Dateierweiterung.
     *
     * @param fileName {String} Der Name der Datei
     * @return {String?} Der MIME-Typ oder null wenn nicht erkennbar
     */
    private fun getMimeType(fileName: String): String? {
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
            else -> null
        }
    }

    /**
     * Wird aufgerufen, wenn die Activity zerstört wird.
     * Stellt sicher, dass die Service-Bindung korrekt aufgelöst wird, um Memory-Leaks zu vermeiden.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
            // entfernt den Callback im Service, da die Activity nicht mehr existiert
            downloadService?.setCallback(null)
        }
    }
}