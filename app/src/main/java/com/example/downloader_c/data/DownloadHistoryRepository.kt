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
    // konfigurierte json-instanz für die Serialisierung/deserialisierung
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // die datei, in der die historie im internen Speicher der App gespeichert wird.
    private val historyFile = File(context.filesDir, "download_history.json")

    // Liste im Speicher als Cache
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

        // neueste zuerst
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
            // lösche die physische Datei
            File(_files[index].filePath).delete()
            // entferne den Eintrag aus der liste
            _files.removeAt(index)
            // Speichere die aktualisierte Liste
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
                val content = historyFile.readText()
                if (content.isNotBlank()) {
                    val list = json.decodeFromString<List<DownloadedFile>>(content)
                    _files.clear()
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
            val content = json.encodeToString(_files)
            historyFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}