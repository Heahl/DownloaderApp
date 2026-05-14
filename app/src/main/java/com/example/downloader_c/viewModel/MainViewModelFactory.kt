package com.example.downloader_c.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.downloader_c.data.DownloadHistoryRepository

/**
 * Factory zur Erstellung des MainViewModel mit einem Repository-Parameter.
 *
 * Implementiert das Factory-Pattern für ViewModel-Provision:
 * - Ermöglicht Dependency Injection des Repository-Abhängigkeiten
 * - Überlebt Konfigurationsänderungen durch ViewModelStore
 * - Zentrale Stelle für ViewModel-Initialisierung
 *
 * ### Vorteile des Factory-Patterns
 * 1. Ermöglicht das Übergeben von Abhängigkeiten (wie [DownloadHistoryRepository], um auf die
 *      Download-Historie zuzugreifen)
 * 2. Behält das Repository (und damit Zustand) bei Bildschirmdrehung
 * 3. Vermeidet Referenzen auf zerstörte Activities
 * 4. Sauberere Architektur:
 *      - Repository: Datenzugriff
 *      - Factory: ViewModel-Erstellung
 *      - UI-Komponenten: Nur Nutzung des ViewModels
 * 5. Reduziert Kopplung: Activities und Fragments müssen nichts über das Repository wissen.
 *
 * @param repository Zentrales Repository für Download-Historie (muss vorher initialisiert sein)
 */
class MainViewModelFactory(private val repository: DownloadHistoryRepository) :
    ViewModelProvider.Factory {

    /**
     * Erstellt eine ViewModel-Instanz für die angegebene Klasse.
     *
     * @param modelClass Gewünschte ViewModel-Klasse
     * @return Initialisierte ViewModel-Instanz
     * @throws IllegalArgumentException Bei unbekannten ViewModel-Typen
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // type check: ensure we only create mainViewModel instance
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            // safe cast after type verification
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        // fail early
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
