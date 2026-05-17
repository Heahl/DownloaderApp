package com.example.downloader_c.data

import android.content.Context
import androidx.core.net.toUri
import com.example.downloader_c.domain.DownloadRepository
import com.example.downloader_c.domain.DownloadedFile
import kotlinx.serialization.json.Json
import java.io.File
import android.util.Log

/**
 * JSON-basierte Implementierung des [DownloadRepository].
 *
 * Speichert die Historie in einer lokalen JSON-Datei mit folgenden Eigenschaften:
 * - Fehlerbehandlung bei Dateioperationen
 * - Validierung von Dateipfaden und Inhalten
 * - Backup-Mechanismus für Dateiintegrität
 *
 * ### Dateiformat:
 * ```json
 * [
 *  {
 *      "id": "unique_id",
 *      "fileName": "example.pdf",
 *      "filePath": "/data/user/0/com.example/files/example.pdf",
 *      "fileSize": 12345,
 *      "mimeType": "application/pdf",
 *      "timeStamp": 12345645678637563783
 *  }
 * ]
 * ```
 *
 * @param context Der Android-Kontext für den Zugriff auf die Dateisystem-Ressourcen
 */
class JsonDownloadHistoryRepository(private val context: Context) : DownloadRepository {
    // configure json serialization with appropriate settings
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = " "
    }

    // file for storing download history
    private val historyFile = File(context.filesDir, "download_history.json")

    //in mem cach for downloaded files
    private val _files = mutableListOf<DownloadedFile>()

    init {
        // load data from disk with error handling
        loadFromDisk()
    }

    /**
     * Gibt eine unveränderliche Liste der heruntergeladenen Dateien zurück.
     *
     * Die Liste ist sortiert mit den neuesten Downloads zuerst (neueste an Position 0). Unveränderliche
     * Liste, damit der interne Zustand nicht manipuliert werden kann.
     *
     * @return Unveränderliche Liste der [DownloadedFile]-Objekte
     */
    override fun getFiles(): List<DownloadedFile> = _files.toList()

    /**
     * Fügt eine neue heruntergeladene Datei zur Historie hinzu.
     *
     * Validiert den Dateipfad und speichert die Dateiinformationen in der Historie.
     * Die neue Datei wird an erster Stelle der Liste eingefügt (neueste zuerst).
     *
     * ### Eigenschaften:
     * - Überprüft die Existenz und Gültigkeit der Datei
     * - Bestimmt den MIME-Type über den ContentResolver
     * - Speichert die Datei sofort persistent
     * - Setzt die interne Liste zurück bei Speicherfehlern
     *
     * @param file Die hinzuzufügende Datei
     * @throws IllegalArgumentException Wenn die Datei ungültig ist
     */
    override fun addFile(file: File) {
        // validate file before adding
        validateFile(file)

        val downloadedFile = createDownloadedFile(file)

        // add to mem and save to disk
        _files.add(0, downloadedFile)
        saveToDisk()
    }

    /**
     * Entfernt eine Datei aus der Historie und vom Dateisystem.
     *
     * ### Ablauf:
     * 1. Findet die die Datei in der Historie
     * 2. Löscht die physische Datei (wenn vorhanden)
     * 3. Entfernt den Eintrag aus der Historie
     * 4. Speichert die aktualisierte Historie
     *
     * @param fileId Die ID der zu entfernenden Datei
     * @return `true`, wenn die Datei erfolgreich entfernt wurde, sonst `false`
     */
    override fun removeFile(fileId: String): Boolean {
        val index = _files.indexOfFirst { it.id == fileId }
        if (index != -1) {
            val filePath = _files[index].filePath
            val file = File(filePath)

            // try to delete the physical file
            val fileDeleted = if (file.exists()) file.delete() else true

            // only remove from history if file deletion succeeded
            if (fileDeleted) {
                _files.removeAt(index)
                saveToDisk()
                return true
            }
        }
        return false
    }

    /**
     * Validiert eine Datei vor dem Hinzufügen zur Historie.
     *
     * Stellt sicher, dass die Datei existiert und gültige Eigenschaften hat.
     *
     * ### Überprüfte Kriterien:
     * - Existenz der Datei
     * - Kein Verzeichnis (nur reguläre Dateien erlaubt)
     * - Nicht-leere Datei (Größe > 0)
     *
     * @param file Vie zu validierende Datei
     * @throws IllegalArgumentException Wenn die Datei ungültig ist
     */
    private fun validateFile(file: File) {
        if (!file.exists()) {
            throw IllegalArgumentException("Datei existiert nicht: ${file.absolutePath}")
        }
        if (file.isDirectory) {
            throw IllegalArgumentException("Datei ist ein Verzeichnis: ${file.absolutePath}")
        }
        if (file.length() <= 0) {
            throw IllegalArgumentException("Datei ist leer: ${file.absolutePath}")
        }
    }

    /**
     * Erstellt ein [DownloadedFile]-Objekt aus einer Datei.
     *
     * Bestimmt den MIME-Type über den Content-Resolver und fügt einen Zeitstempel hinzu.
     *
     * @param file Die Quelldatei
     * @return Das erstellte [DownloadedFile]-Objekt
     */
    private fun createDownloadedFile(file: File): DownloadedFile {
        return DownloadedFile(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            mimeType = context.contentResolver.getType(file.toUri())
        )
    }

    /**
     * Lädt die D ownload-Historie aus der lokalen JSON-Datei in den Speicher-Cache.
     *
     * Falls die Datei nicht existiert oder das Lesen / Deserialisieren fehlschlägt, bleibt die
     * interne Liste leer oder behält ihren alten Zustand bei.
     * Fehler werden protokolliert, aber nicht weitergeleitet.
     *
     * ### Fehlerbehandlungen:
     * - Prüft auf leere Dateiinhalte
     * - Nutzt Backup-Datei bei Deserialisierungsfehlern
     * - Setzt Liste zurück bei kritischen Fehlern
     */
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
                // Log error with context
                Log.e("DownloadHistory", "Fehler beim Laden der Historie", e)

                // attempt to recover from backup if available
                val backupFile = File(context.filesDir, "download_history_backup.json")
                if (backupFile.exists()) {
                    try {
                        val backupContent = backupFile.readText()
                        if (backupContent.isNotBlank()) {
                            val list = json.decodeFromString<List<DownloadedFile>>(backupContent)
                            _files.clear()
                            _files.addAll(list)
                            Log.i("DownloadHistory", "Historie aus Backup wiederhergestellt")
                        }
                    } catch (backupEx: Exception) {
                        Log.e("DownloadHistory", "Fehler beim Laden aus Backup", backupEx)
                    }
                }
                e.printStackTrace()

            }
        }
    }

    /**
     * Speichert die aktuelle Liste der heruntergeladenen Dateien im Speicher als JSON-String in
     * eine lokale Datei.
     *
     * Vor dem Speichern wird eine Sicherungskopie der aktuellen Datei erstellt, um Datenverlust
     * bei Schreibfehlern zu vermeiden.
     *
     * ### Ablauf:
     * 1. Erstellt Backup der aktuellen Datei
     * 2. Serialisiert die Liste in JSON
     * 3. Schreibt JSON in Datei
     * 4. Löscht Backup bei Erfolg
     *
     * ### Fehlerbehandlung:
     * - Bei Fehlern wird versucht, aus dem Backup wiederherzustellen
     * - Interne Liste bleibt unverändert bei kritischen Fehlern
     */
    private fun saveToDisk() {
        try {
            // create backup before writing
            createBackup()

            // write new content
            val content = json.encodeToString(_files)
            historyFile.writeText(content)

            // clean up backup on success
            deleteBackup()
        } catch (e: Exception) {
            Log.e("DownloadHistory", "Fehler beim Speichern der Historie", e)

            // attempt recovery from backup
            restoreBackup()
        }
    }

    /**
     * Erstellt eine Sicherungskopie der aktuellen Historie-Datei.
     *
     * Die Sicherung wird in einer separaten Datei gespeichert, um Dateiverlust bei Schreibfehlern
     * zu vermeiden. Wird vor jedem Speichervorgang aufgerufen.
     */
    private fun createBackup() {
        if (historyFile.exists()) {
            val backupFile = File(context.filesDir, "download_history_backup.json")
            try {
                historyFile.copyTo(backupFile, overwrite = true)
            } catch (e: Exception) {
                Log.w("DownloadHistory", "Konnte Sicherung nicht erstellen", e)
            }
        }
    }

    /**
     * Löscht die Sicherungskopie nach erfolgreichem Speichern.
     *
     * Reduziert Speicherbedarf, indem temporäre Backupdateien entfernt werden.
     */
    private fun deleteBackup() {
        val backupFile = File(context.filesDir, "download_history_backup.json")
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }

    /**
     * Stellt die Historie-Datei aus der Sicherungskopie wieder her.
     *
     * Wird nach einem Schreibfehler aufgerufen, um Datenverlust zu vermeiden.
     * Setzt die interne Liste zurück, wenn die Wiederherstellung fehlschlägt.
     */
    private fun restoreBackup() {
        val backupFile = File(context.filesDir, "download_history_backup.json")
        if (backupFile.exists()) {
            try {
                val backupContent = backupFile.readText()
                if (backupContent.isNotBlank()) {
                    val list = json.decodeFromString<List<DownloadedFile>>(backupContent)
                    _files.clear()
                    _files.addAll(list)
                    Log.i("DownloadHistory", "Historie aus Backup wiederhergestellt")
                }
            } catch (e: Exception) {
                Log.e("DownloadHistory", "Fehler beim Wiederherstellen aus Backup", e)
                // reset list to prevent inconsistent state
                _files.clear()
            }
        }
    }
}
