package com.example.downloader_c.data

import android.content.Context
import androidx.core.net.toUri
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repository zur Verwaltung der Download-Historie.
 *
 * Diese Klasse ist für das Laden, Speichern, Hinzufügen und Entfernen von [DownloadedFile]-Objekten
 * verantwortlich. Sie verwendet eine lokale JSON-Datei im privaten App-Verzeichnis zur persistenten
 * Speicherung und hält eine interne Liste im Speicher als Cache für schnellen Zugriff.
 *
 * Da die App keine parallelen Downloads ermöglicht, habe ich hier nicht auf thread-sicherheit bei crud-
 * operationen geachtet.
 *
 * @param context Der Application Context, um auf das interne Speicherverzeichnis zugreifen zu können.
 */
class DownloadHistoryRepository(private val context: Context) {
    // configure json instance for serialization / deserialization
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // the file that holds the download history in the internal storage
    private val historyFile = File(context.filesDir, "download_history.json")

    // list on storage as cache
    private val _files = mutableListOf<DownloadedFile>()

    /**
     * Initialisierungsblock, der beim Erstellen der Instanz aufgerufen wird.
     * Lädt die bestehende Download-Historie von der Festplatte in den Speicher-Cache.
     */
    init {
        loadFromDisk()
    }

    /**
     * Gibt eine unveränderliche Kopie der Liste aller heruntergeladenen Dateien zurück.
     *
     * @return Eine `List<DownloadedFile>`, die die aktuelle Historie repräsentiert.
     */
    fun getFiles(): List<DownloadedFile> = _files.toList()

    /**
     * Fügt eine neue heruntergeladene Datei zur Historie hinzu und speichert die aktualisierte Liste.
     *
     * Die Dateiinformationen werden aus dem gegebenen `File`-Objekt extrahiert.
     * Der Eintrag wird immer an den Anfang der Liste gesetzt, sodass die neuesten Downloads zuerst erscheinen.
     *
     * @param file {File} Das `java.io.File`-Objekt der neu heruntergeladenen Datei.
     */
    fun addFile(file: File) {
        val downloadedFile = DownloadedFile(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            mimeType = context.contentResolver.getType(file.toUri())
        )

        // latest first
        _files.add(0, downloadedFile)
        saveToDisk()
    }

    /**
     * Entfernt einen Eintrag aus der Download-Historie anhand seiner ID.
     *
     * Wenn der Eintrag gefunden wird, wird er sowohl aus der internen Liste als auch physisch vom
     * Dateisystem gelöscht. Anschließend wird die aktualisierte Liste auf die Festplatte geschrieben.
     *
     * @param fileId {String} Die eindeutige ID ([DownloadedFile.id]) des zu entfernenden Eintrags.
     * @return `true`, wenn der Eintrag gefunden und erfolgreich entfernt wurde, sonst `false`.
     */
    fun removeFile(fileId: String): Boolean {
        val index = _files.indexOfFirst { it.id == fileId }
        if (index != -1) {
            // remove the physical file
            File(_files[index].filePath).delete()
            // remove entry from list
            _files.removeAt(index)
            // save updated list
            saveToDisk()
            return true
        }
        return false
    }

    /**
     * Lädt die Download-Historie aus der lokalen JSON-Datei in den Speicher-Cache.
     *
     * Falls die Datei nicht existiert oder das Lesen/Deserialisieren fehlschlägt, bleibt die interne
     * Liste leer oder behält ihren alten Zustand bei.
     * Fehler werden lediglich über `printStackTrace()` protokolliert.
     */
    private fun loadFromDisk() {
        if (historyFile.exists()) {
            try {
                // read raw history file content
                val content = historyFile.readText()
                if (content.isNotBlank()) {
                    // deserialize json string to list DownloadedFile objects
                    val list = json.decodeFromString<List<DownloadedFile>>(content)
                    // clear existing cach before loading new data
                    _files.clear()
                    // add all deserialized items to memory cache
                    _files.addAll(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Speichert die aktuelle Liste der heruntergeladenen Dateien im Speicher als JSON-String in die
     * lokale Datei.
     *
     * Fehler beim Schreiben werden lediglich über `printStackTrace()` protokolliert.
     */
    private fun saveToDisk() {
        try {
            // convert memory cach to json string
            val content = json.encodeToString(_files)
            // write serialized data to disk file
            historyFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}