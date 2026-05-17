package com.example.downloader_c

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.example.downloader_c.domain.DownloadCallback
import com.example.downloader_c.data.DownloadService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadServiceIntegrationTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var mockServer: MockWebServer

    @Before
    fun setUp() {
        mockServer = MockWebServer().apply { start() }

    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `full download flow saves file and triggers callback`() {
        // 1. Mock-Server: 4KB Test-Datei
        val testContent = "Download-Test-Inhalt\n".repeat(200)
        val testFileName = "integration_test.pdf"

        mockServer.enqueue(
            MockResponse()
                .setBody(testContent)
                .addHeader("Content-Length", testContent.length.toString())
                .addHeader("Content-Disposition", "attachment; filename=\"$testFileName\"")
        )

        //  MockWebServer hostName für Device-Konnektivität
        val downloadUrl = mockServer.url("/$testFileName").toString()
            .replace("localhost", mockServer.hostName)

        // 2. Callback mit Tracking
        var downloadedFile: File? = null
        val progressValues = mutableListOf<Int>()
        val latch = CountDownLatch(1)

        val testCallback = object : DownloadCallback {
            override fun onProgressUpdate(progress: Int) {
                progressValues.add(progress)
            }

            override fun onDownloadComplete(file: File) {
                downloadedFile = file
                latch.countDown()
            }

            override fun onDownloadError(message: String) {
                System.err.println("Download error: $message")
                latch.countDown()
            }
        }

        //  3. Binden ZUERST für Callback
        val bindIntent =
            Intent(ApplicationProvider.getApplicationContext(), DownloadService::class.java)
        val binder = serviceRule.bindService(bindIntent) as DownloadService.LocalBinder
        val service = binder.getService()
        service.setCallback(testCallback)

        //  4. DANN Service als Foreground Service starten (Download startet in onStartCommand)
        val startIntent =
            Intent(ApplicationProvider.getApplicationContext(), DownloadService::class.java)
                .putExtra("url", downloadUrl)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            androidx.core.content.ContextCompat.startForegroundService(
                ApplicationProvider.getApplicationContext(),
                startIntent
            )
        } else {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .startService(startIntent)
        }

        // 5. Warten auf Abschluss (länger für Emulator)
        val completed = latch.await(45, TimeUnit.SECONDS)

        // 6. Assertions
        assertTrue("Download did not complete within timeout", completed)
        assertTrue("No progress updates received", progressValues.isNotEmpty())
        assertTrue("Final progress 100% not reported", 100 in progressValues)

        assertNotNull("No file received in callback", downloadedFile)
        assertTrue("Downloaded file does not exist", downloadedFile!!.exists())
        assertEquals("Filename mismatch", testFileName, downloadedFile.name)
        assertEquals("File content mismatch", testContent, downloadedFile.readText())
    }

    @Test
    fun `download with HTTP error triggers onError callback`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )
        val downloadUrl = mockServer.url("/missing_file.zip").toString()
            .replace("localhost", mockServer.hostName)

        var errorMessage: String? = null
        val latch = CountDownLatch(1)

        val testCallback = object : DownloadCallback {
            override fun onProgressUpdate(progress: Int) {}
            override fun onDownloadComplete(file: File) = latch.countDown()
            override fun onDownloadError(message: String) {
                errorMessage = message
                latch.countDown()
            }
        }

        //  Binden
        val bindIntent =
            Intent(ApplicationProvider.getApplicationContext(), DownloadService::class.java)
        val binder = serviceRule.bindService(bindIntent) as DownloadService.LocalBinder
        val service = binder.getService()
        service.setCallback(testCallback)

        //  Starten
        val startIntent =
            Intent(ApplicationProvider.getApplicationContext(), DownloadService::class.java)
                .putExtra("url", downloadUrl)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            androidx.core.content.ContextCompat.startForegroundService(
                ApplicationProvider.getApplicationContext(),
                startIntent
            )
        } else {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .startService(startIntent)
        }

        val completed = latch.await(20, TimeUnit.SECONDS)

        assertTrue("Error callback not triggered within timeout", completed)
        assertTrue(
            "Expected HTTP error message, got: '$errorMessage'",
            errorMessage?.contains("404") == true || errorMessage?.contains("HTTP") == true
        )
    }

    @Test
    fun `download with unknown content length handles gracefully`() {
        val testContent = "Chunked transfer test content"
        mockServer.enqueue(
            MockResponse()
                .setChunkedBody(testContent, 5)
        )
        val downloadUrl = mockServer.url("/chunked.bin").toString()
            .replace("localhost", mockServer.hostName)

        var completedFile: File? = null
        val latch = CountDownLatch(1)

        val testCallback = object : DownloadCallback {
            override fun onProgressUpdate(progress: Int) {}
            override fun onDownloadComplete(file: File) {
                completedFile = file
                latch.countDown()
            }

            override fun onDownloadError(message: String) {
                System.err.println("Download error: $message")
                latch.countDown()
            }
        }

        //  Binden
        val bindIntent =
            Intent(ApplicationProvider.getApplicationContext(), DownloadService::class.java)
        val binder = serviceRule.bindService(bindIntent) as DownloadService.LocalBinder
        val service = binder.getService()
        service.setCallback(testCallback)

        //  Starten
        val startIntent =
            Intent(ApplicationProvider.getApplicationContext(), DownloadService::class.java)
                .putExtra("url", downloadUrl)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            androidx.core.content.ContextCompat.startForegroundService(
                ApplicationProvider.getApplicationContext(),
                startIntent
            )
        } else {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .startService(startIntent)
        }

        val completed = latch.await(45, TimeUnit.SECONDS)

        assertTrue("Download did not complete", completed)
        assertNotNull("No file received", completedFile)
        assertTrue("Downloaded file does not exist", completedFile!!.exists())
    }
}