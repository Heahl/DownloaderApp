package com.example.downloader_c.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.downloader_c.domain.DownloadRepository

/**
 * Factory zur Erstellung des MainViewModel mit Dependency Injection.
 *
 * Implementiert das Factory-Pattern für ViewModel-Provision:
 * - Ermöglicht Dependency Injection des Repository-Abhängigkeiten
 * - Überlebt Konfigurationsänderungen durch ViewModelStore
 * - Zentrale Stelle für ViewModel-Initialisierung
 * - Stellt sicher, dass alle ViewModels korrekt mit Abhängigkeiten versorgt werden
 *
 * ### Wichtige Eigenschaften:
 * - Unterstützt nur den MainViewModel (keine generische Factory)
 * - Verhindert Memory Leaks durch korrekte Abhängigkeitsverwaltung
 * - Funktioniert mit ViewModelProvider von AndroidX
 * - Ermöglicht einfache Erweiterung bei neuen ViewModels
 *
 * ### Nutzung:
 * ```kotlin
 * val viewModel: MainViewModel by viewModels {
 *     MainViewModelFactory(repository)
 * }
 * ```
 *
 * @param repository Zentrales Repository für Download-Historie (muss vorab initialisiert sein)
 */
class MainViewModelFactory(private val repository: DownloadRepository) :
    ViewModelProvider.Factory {
    /**
     * Erstellt eine ViewModel-Instanz für die angegebene Klasse.
     *
     * @param modelClass Gewünschte ViewModel-Klasse
     * @return Initialisierte ViewModel-Instanz
     * @throws IllegalArgumentException Bei unbekannten ViewModel-Typen
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // check if requested class is assignable from mainViewModel
        // using isAssignableFrom instead fo == to support inheritance
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            // safe cast after type verification
            // type check above guarantees that this cast is safe
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        // fail early with descriptive error for unsupported ViewModel types
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
