package com.example.downloader_c.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.downloader_c.R
import com.example.downloader_c.data.JsonDownloadHistoryRepository
import com.example.downloader_c.data.DownloadService
import com.example.downloader_c.data.DownloadUtils
import com.example.downloader_c.databinding.ActivityMainBinding
import com.example.downloader_c.domain.DownloadCallback
import com.example.downloader_c.domain.DownloadedFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File


/**
 * Main Activity der DownloaderApp
 */
class MainActivity : AppCompatActivity(), DownloadCallback {
    // viewBinding for ui elements
    private lateinit var binding: ActivityMainBinding

    // viewModel for managing ui state
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(JsonDownloadHistoryRepository(applicationContext))
    }

    // adapter for the recyclerview to display download-history
    private lateinit var adapter: DownloadListAdapter

    // reference to the service
    private var downloadService: DownloadService? = null

    // flag to watch binding state
    private var isBound = false

    // launcher for the runtime permission request for notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Berechtigung erteilt, Download kann starten
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) startDownloadService(url)
        } else {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT)
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
            updateUiState(viewModel.isDownloadActive.value ?: false)
        }

        /**
         * Wird aufgerufen, wenn die Verbindung zum Service unerwartet getrennt wurde (zB. durch Absturz)
         */
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            downloadService = null // sets reference to null, if connection is lost
        }
    }

    /**
     * Wird beim ersten Erstellen der Activity aufgerufen.
     * Initialisiert ViewBinding und setzt den Click-Listener für den Download-Button.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // initialize viewBinding. binding.root is the root-element of the layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // recyclerview setup
        setupRecyclerView()

        // watch data in viewModel
        observeViewModel()

        // set onClickListener for btnDownload
        binding.btnDownload.setOnClickListener {
            val url = binding.etUrl.text.toString().trim() // get URL
            if (url.isNotEmpty()) { // url validation
                checkNotificationPermissionAndStart(url)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.type_url),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    /**
     * Registriert Observer für die LiveData-Objekte im ViewModel.
     */
    private fun observeViewModel() {
        // watch file list
        viewModel.downloadedFiles.observe(this, Observer { files ->
            adapter.submitList(files)
            updateListVisibility(files.isEmpty())
        })

        // watch download state
        viewModel.isDownloadActive.observe(this, Observer { isActive ->
            updateUiState(isActive)
        })

        // watch download progress
        viewModel.progress.observe(this, Observer { progress ->
            updateProgressUi(progress)
        })
    }

    private fun updateListVisibility(isEmpty: Boolean) {
        binding.tvEmptyList.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerDownloads.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
                // permission -> start service
                startDownloadService(url)
            }

            else -> {
                // !permission -> ask for permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Richtet die RecyclerView ein, die die Download-Historie anzeigt.
     * Konfiguriert den Adapter mit Click-Handlern für "Öffnen" und "Löschen".
     */
    private fun setupRecyclerView() {
        // initializes the adapter with lambda functions for click events
        adapter = DownloadListAdapter(
            onOpenClick = { file -> openFile(File(file.filePath)) },    // open file
            onDeleteClick = { file -> deleteFile(file) }            // close file
        )

        // apply adapter, layoutManager and animator to recyclerview
        binding.recyclerDownloads.apply {
            this.adapter = this@MainActivity.adapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = DefaultItemAnimator()
        }
    }


    /**
     * Zeigt einen Bestätigungsdialog an und löscht die Datei, wenn der Nutzer zustimmt.
     * Entfernt den Eintrag sowohl aus dem Repository als auch vom Dateisystem.
     *
     * @param downloadedFile {DownloadedFile} Das zu löschende [DownloadedFile]-Objekt.
     */
    private fun deleteFile(downloadedFile: DownloadedFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_confirmation_title))
            .setMessage(
                getString(
                    R.string.delete_confirmation_message,
                    downloadedFile.fileName
                )
            )
            .setPositiveButton(R.string.delete_btn) { _, _ ->
                viewModel.deleteFile(downloadedFile)
                Toast.makeText(
                    this, R.string.file_deleted, Toast.LENGTH_SHORT
                )
                    .show()
            }
            .setNegativeButton(
                R.string.cancel_btn, null
            ) // closes dialog without action
            .show()
    }

    /**
     * Startet den DownloadService als Started Foreground Service und bindet sich danach.
     * Trennt die Verantwortung für das Starten (mit URL) und die Kommunikation (über Bindung).
     *
     * @param url {String} Der URL der herunterzuladenden Datei
     */
    private fun startDownloadService(url: String) {
        // 1. intent fort startSarvice: contains url
        // start service and hand over url as parameter
        val startIntent = Intent(this, DownloadService::class.java)
        startIntent.putExtra("url", url)
        startService(startIntent)

        // 2. intent for bindService: created separately
        // creates binding, does not hand over data
        // url is already known through startService
        val bindIntent = Intent(this, DownloadService::class.java)
        // bindIntent does NOT contain extras
        // communication (progress, state) achieved via callback interface after binding
        bindService(bindIntent, connection, BIND_AUTO_CREATE)

        // set download state to active in viewModel
        viewModel.setDownloadActive(true)
    }

    /**
     * Aktualisiert den Zustand der Benutzeroberfläche basierend auf dem Download-Status.
     *
     * @param isActive {Boolean} Gibt an, ob ein Download läuft oder nicht (true = Download läuft)
     */
    private fun updateUiState(isActive: Boolean) {
        // deactivate / activate the download-btn
        binding.btnDownload.isEnabled = !isActive
        // change text of the btn
        binding.btnDownload.text =
            if (isActive) getString(R.string.download_running) else getString(R.string.start_download)
        // deactivate et-url
        binding.etUrl.isEnabled = !isActive
    }

    /**
     * Wird vom Service aufgerufen, wenn sich der Download-Fortschritt ändert.
     * Wird nicht automatisch im UI-Thread ausgeführt.
     * UI-Code muss daher innerhalb von runOnUiThread ausgeführt werden.
     */
    override fun onProgressUpdate(progress: Int) {
        viewModel.updateProgress(progress)
    }

    private fun updateProgressUi(progress: Int) {
        runOnUiThread {
            if (progress >= 0) {
                binding.progressBar.isIndeterminate = false
                // update progressbar with new progress
                binding.progressBar.progress = progress
                // update progress-text
                binding.tvProgress.text = getString(R.string.progress_format, progress)
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
            // set progress to 100%
            viewModel.updateProgress(100)
            Toast.makeText(
                this, getString(
                    R.string.download_completed
                ),
                Toast.LENGTH_LONG
            ).show()
            // add file to repository via viewModel
            viewModel.addDownloadedFile(file)
            // open downloaded file
            openFile(file)
            // set download state to inactive
            viewModel.setDownloadActive(false)
            // cleanup binding
            if (isBound) {
                unbindService(connection)
                isBound = false
                downloadService = null
            }
        }
    }

    /**
     * Wird vom Service aufgerufen, wenn ein Fehler während des Downloads aufgetreten ist.
     */
    override fun onDownloadError(message: String) {
        runOnUiThread {
            Toast.makeText(
                this,
                getString(
                    R.string.error_message,
                    message
                ), Toast.LENGTH_LONG
            )
                .show()
            // set download state to inactive
            viewModel.setDownloadActive(false)
            // cleanup binding
            if (isBound) {
                unbindService(connection)
                isBound = false
                downloadService = null
            }
        }
    }

    /**
     * Öffnen der heruntergeladenen Datei mit einer geeigneten externen App.
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

        // define mime type based on file ending
        val mimeType = DownloadUtils.getMimeType(file.name)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // check if app exists that can process the intent
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                getString(R.string.no_app_found),
                Toast.LENGTH_SHORT
            )
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
            // unbind before clearing callback to prevent race condition
            unbindService(connection)
            isBound = false
            // remove callback in service to prevent memory leaks
            downloadService?.setCallback(null)
        }
    }
}
