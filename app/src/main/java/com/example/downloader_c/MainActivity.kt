package com.example.downloader_c

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.downloader_c.databinding.ActivityMainBinding
import java.io.File
import androidx.core.net.toUri
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
 */
class MainActivity : AppCompatActivity(), DownloadCallback {
    // ViewBinding für die UI-Elemente
    private lateinit var binding: ActivityMainBinding

    private lateinit var repository: DownloadHistoryRepository

    private lateinit var adapter: DownloadListAdapter

    // Referenz auf den Service
    private var downloadService: DownloadService? = null

    // Flag zum Überwachen des Bindungsstatus
    private var isBound = false

    // Flag zum Überwachen des Downloadstatus
    private var isDownloadActive = false

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
            // todo: ui-state aktualisieren, wenn Verbindung verloren geht und Download aktiv war.
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
                startDownloadService(url)
            } else {
                Toast.makeText(this, "Bitte URL eingeben", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = DownloadListAdapter(
            onOpenClick = { file -> openFile(File(file.filePath)) },
            onDeleteClick = { file -> deleteFile(file) }
        )

        binding.tvEmptyList.visibility =
            if (repository.getFiles().isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerDownloads.visibility =
            if (repository.getFiles().isEmpty()) View.GONE else View.VISIBLE

        binding.recyclerDownloads.apply {
            this.adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = DefaultItemAnimator()
        }
    }

    private fun updateDownloadList() {
        adapter.submitList(repository.getFiles())
    }

    private fun deleteFile(downloadedFile: DownloadedFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Datei löschen?")
            .setMessage("Möchtest du '${downloadedFile.fileName}' wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                if (repository.removeFile(downloadedFile.id)) {
                    File(downloadedFile.filePath).delete()
                    updateDownloadList()
                    Toast.makeText(this, "Gelöscht", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
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
     * Führt eine grundlegende Validierung der eingegebenen URL durch.
     * Prüft, ob Schema und Host vorhanden sind.
     *
     * @param url {String} Der zu validierende URL als String
     * @return `true`, wenn der URL gültig erscheint, sonst `false`.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = url.toUri()
            !uri.scheme.isNullOrEmpty() && !uri.host.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }

    }

    /**
     * Wird vom Service aufgerufen, wenn sich der Download-Fortschritt ändert.
     * Wird nicht automatisch im UI-Thread ausgeführt.
     * UI-Code muss daher innerhalb von runOnUiThread ausgeführt werden.
     */
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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, contentResolver.getType(uri))
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