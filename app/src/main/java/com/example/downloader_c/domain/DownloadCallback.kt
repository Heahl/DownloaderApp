package com.example.downloader_c.domain

import java.io.File

/**
 * Callback-Interface für die Kommunikation zwischen Download-Service und UI-Schicht.
 *
 * Ermöglicht die asynchrone Kommunikation zwischen dem [com.example.downloader_c.data.DownloadService]
 * (Hintergrund) und der UI-Komponente (Activity/Fragment), um den Download-Status zu verfolgen.
 * Wird von der UI-Komponente implementiert und dem Service übergeben.
 *
 * ### Nutzungsmuster:
 * 1. Activity implementiert dieses Interface
 * 2. Activity bindet an den DownloadService
 * 3. Activity setzt sich selbst als Callback
 * 4. Service ruft Callback-Methoden während des Downloads auf
 */
interface DownloadCallback {
    /**
     * Wird bei Fortschrittsaktualisierungen aufgerufen.
     *
     * Der Fortschritt wird als Prozentwert (0-100) übermittelt. Bei unbekannter Gesamtgröße
     * (Content-Length) wird -1 übermittelt, um einen indeterminierten Fortschritt anzuzeigen.
     *
     * @param progress Der aktuelle Fortschritt (0-100) oder -1 für indeterminiert
     */
    fun onProgressUpdate(progress: Int)

    /**
     * Wird bei erfolgreichem Abschluss des Downloads aufgerufen.
     *
     * Übermittelt die heruntergeladene Datei, die nun lokal verfügbar ist.
     * Die Datei sollte geöffnet oder anderweitig verarbeitet werden.
     *
     * @param file Die vollständig heruntergeladene Datei
     */
    fun onDownloadComplete(file: File)

    /**
     * Wird bei einem Fehler während des Downloads aufgerufen.
     *
     * Übermittelt eine Fehlermeldung, die für den Benutzer angezeigt werden kann.
     * Die Fehlermeldung sollte verständlich sein und den Benutzer informieren, was schiefgelaufen ist.
     *
     * @param message Menschlich lesbare Fehlerbeschreibung
     */
    fun onDownloadError(message: String)
}
