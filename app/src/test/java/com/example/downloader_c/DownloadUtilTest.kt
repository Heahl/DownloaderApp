package com.example.downloader_c

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import com.example.downloader_c.data.DownloadUtils
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DownloadUtilTest {

    // ==================== getFileNameFromUrl Tests ====================

    @Test
    fun `getFileNameFromUrl returns correct name for simple URL`() {
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/files/report.pdf",
            contentDisposition = null
        )
        assertEquals("report.pdf", result)
    }

    @Test
    fun `getFileNameFromUrl returns correct name from content disposition`() {
        val contentDisposition = "attachment; filename=\"image.jpg\""
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/files/download",
            contentDisposition
        )
        assertEquals("image.jpg", result)
    }

    @Test
    fun `getFileNameFromUrl returns fallback name for malformed URL`() {
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/",
            null
        )
        assertEquals("downloaded_file", result)
    }

    @Test
    fun `getFileNameFromUrl strips query parameters`() {
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/files/data.csv?v=2&token=abc",
            null
        )
        assertEquals("data.csv", result)
    }

    @Test
    fun `getFileNameFromUrl handles empty lastPathSegment`() {
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/",
            null
        )
        assertEquals("downloaded_file", result)
    }

    @Test
    fun `getFileNameFromUrl prefers ContentDisposition over URL filename`() {
        val contentDisposition = "attachment; filename=\"header_name.pdf\""
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/files/url_name.txt",
            contentDisposition
        )
        assertEquals("header_name.pdf", result)
    }

    @Test
    fun `getFileNameFromUrl handles filename with special characters`() {
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/files/my-report_v2.1-final.pdf",
            null
        )
        assertEquals("my-report_v2.1-final.pdf", result)
    }

    @Test
    fun `getFileNameFromUrl handles content disposition without filename param`() {
        val contentDisposition = "attachment"
        val result = DownloadUtils.getFileNameFromUrl(
            "https://example.com/files/report.pdf",
            contentDisposition
        )
        assertEquals("report.pdf", result)
    }

    // ==================== Progress-Berechnung Tests ====================

    /**
     * Helper-Funktion, die die gleiche Logik wie in DownloadUtils.startDownload() abbildet.
     * Wichtig: totalSize > 0 prüfen, um Division durch Null zu vermeiden!
     */
    private fun calculateProgress(downloadedSize: Long, totalSize: Long): Int {
        return if (totalSize > 0) {
            ((downloadedSize * 100) / totalSize).toInt()
        } else {
            -1 // Unbekannte Größe → indeterminate
        }
    }

    @Test
    fun `calculateProgress returns correct percentage for various stages`() {
        assertEquals(0, calculateProgress(0, 102400))
        assertEquals(25, calculateProgress(25600, 102400))
        assertEquals(50, calculateProgress(51200, 102400))
        assertEquals(75, calculateProgress(76800, 102400))
        assertEquals(100, calculateProgress(102400, 102400))
    }

    @Test
    fun `calculateProgress handles unknown content length`() {
        assertEquals(-1, calculateProgress(5000, -1))
        assertEquals(-1, calculateProgress(0, -1))
    }

    @Test
    fun `calculateProgress handles zero total size gracefully`() {
        assertEquals(-1, calculateProgress(0, 0))
        assertEquals(-1, calculateProgress(100, 0))
    }

    @Test
    fun `calculateProgress handles large file sizes without overflow`() {
        val fileSize = 2L * 1024 * 1024 * 1024 // 2 GB
        assertEquals(50, calculateProgress(fileSize / 2, fileSize))
        assertEquals(9, calculateProgress(fileSize / 10, fileSize))
    }

    @Test
    fun `calculateProgress rounds down correctly`() {
        assertEquals(33, calculateProgress(333, 1000))
        assertEquals(66, calculateProgress(666, 1000))
        assertEquals(99, calculateProgress(999, 1000))
    }
}