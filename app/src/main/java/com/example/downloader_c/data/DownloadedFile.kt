package com.example.downloader_c.data

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Repräsentiert eine heruntergeladene Datei und speichert deren Metadaten.
 *
 * Diese Data Class kapselt Informationen über eine Datei, die von der App heruntergeladen wurde.
 * Sie enthält einen eindeutigen Identifikator, Dateinamen, Pfad, Größe, Zeitpunkt des Downloads
 * und optional den MIME-Typ. Zusätzlich bietet sie die Hilfsfunktion zum Formatieren des Datums und
 * der Dateigröße für die Darstellung in der Benutzeroberfläche.
 *
 * @param id {String} Eindeutiger Identifikator für die heruntergeladene Datei. UUID.
 * @param fileName {String} Der Name der heruntergeladenen Datei (zB. "document.pdf").
 * @param filePath {String} Der absolute Dateipfad, unter dem die Datei gespeichert ist.
 * @param fileSize {Long} Die Größe der Datei in Bytes
 * @param downloadedAt {Long} Der Zeitpunkt des Downloads als Unix-Timestamp (Millisekunden seit Epoche). Default == Systemzeitpunkt
 * @param mimeType {String?} Der MIME-Type der Datei (zB. "application/pdf"). Kann `null` sein.
 */
@Serializable
data class DownloadedFile(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
    val mimeType: String? = null
) {
    /**
     * Gibt das Datum der Downloads im deutschen Format "TT.MM.JJJJ HH:MM" als String zurück.
     * Beispiel: "07.05.2026 14:30".
     */
    val formattedDate: String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(downloadedAt))

    /**
     * Gibt die Dateigröße in einem human readable Format (B / KB / MB) als String zurück.
     * Beispiel: "150 B", "2.3 KB", "15.7 MB".
     */
    val formattedSize: String
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "%.1f KB".format(fileSize / 1024.0)
            else -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
        }
}