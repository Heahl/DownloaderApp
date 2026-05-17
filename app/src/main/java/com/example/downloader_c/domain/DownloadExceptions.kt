package com.example.downloader_c.domain

import java.io.IOException

/**
 * Basis-Klasse für Download-bezogene Ausnahmen.
 */
open class DownloadException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Ausnahme bei Verbindungsproblemen.
 */
class NetworkConnectionException(message: String, cause: Throwable? = null) :
    DownloadException(message, cause)

/**
 * Ausnahme bei HTTP-Fehlern (4xx, 5xx).
 */
class HttpException(code: Int, message: String) :
    DownloadException("HTTP $code: $message")

/**
 * Ausnahme bei Speicherproblemen.
 */
class FileStorageException(message: String, cause: Throwable? = null) :
    DownloadException(message, cause)

/**
 * Ausnahme bei Abbruch durch Benutzer oder System.
 */
class DownloadCancelledException(message: String, cause: Throwable? = null) :
    DownloadException(message, cause)

/**
 * Ausnahme bei Sicherheitsproblemen (z.B. Cleartext nicht erlaubt).
 */
class NetworkSecurityException(message: String, cause: Throwable? = null) :
    DownloadException(message, cause)
