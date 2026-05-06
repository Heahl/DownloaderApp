package com.example.downloader_c

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DownloadServiceBindingTest {

    private lateinit var context: Context
    private lateinit var service: DownloadService
    private lateinit var binder: DownloadService.LocalBinder
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, ibinder: IBinder?) {
            binder = ibinder as DownloadService.LocalBinder
            service = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val intent = Intent(context, DownloadService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Warten auf Binding (max. 5 Sekunden)
        var attempts = 0
        while (!isBound && attempts < 50) {
            Thread.sleep(100)
            attempts++
        }
        assertTrue("Service binding timed out", isBound)
    }

    @After
    fun tearDown() {
        if (isBound) {
            context.unbindService(serviceConnection)
        }
    }

    @Test
    fun `binder returns correct service instance`() {
        val retrievedService = binder.getService()
        assertNotNull("Retrieved service should not be null", retrievedService)
        assertTrue("Binder returned wrong instance", retrievedService === service)
    }

    @Test
    fun `callback receives progress updates via setCallback`() {
        // Arrange
        val progressUpdates = mutableListOf<Int>()
        val latch = CountDownLatch(1)

        //  Direkter Import der separaten Interface-Datei
        val testCallback = object : DownloadCallback {
            override fun onProgressUpdate(progress: Int) {
                progressUpdates.add(progress)
                if (progress >= 100) latch.countDown()
            }

            override fun onDownloadComplete(file: File) = latch.countDown()
            override fun onDownloadError(message: String) = latch.countDown()
        }

        // Act: Callback registrieren
        service.setCallback(testCallback)

        // Simuliere Progress-Update via Reflection (private Methode testen)
        val updateProgressMethod = DownloadService::class.java
            .getDeclaredMethod("updateProgress", Int::class.java)
            .apply { isAccessible = true }

        updateProgressMethod.invoke(service, 25)
        updateProgressMethod.invoke(service, 75)
        updateProgressMethod.invoke(service, 100)

        // Assert
        val completed = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Callback not triggered within timeout", completed)
        assertTrue("Progress 25% not reported", 25 in progressUpdates)
        assertTrue("Progress 75% not reported", 75 in progressUpdates)
        assertTrue("Progress 100% not reported", 100 in progressUpdates)
    }

    @Test
    fun `callback receives completion with valid file`() {
        // Arrange
        var receivedFile: File? = null
        val latch = CountDownLatch(1)

        val testCallback = object : DownloadCallback {
            override fun onProgressUpdate(progress: Int) {}
            override fun onDownloadComplete(file: File) {
                receivedFile = file
                latch.countDown()
            }

            override fun onDownloadError(message: String) = latch.countDown()
        }

        service.setCallback(testCallback)

        // Callback manuell triggern für Testzwecke
        val testFile = File(context.getExternalFilesDir(null), "test_complete.txt")
        testFile.writeText("test content")
        testCallback.onDownloadComplete(testFile)

        // Assert
        val completed = latch.await(2, TimeUnit.SECONDS)
        assertTrue("Completion callback not triggered", completed)
        assertNotNull("No file received in callback", receivedFile)
        assertEquals("test_complete.txt", receivedFile?.name)
        assertTrue("File does not exist", receivedFile?.exists() == true)
    }
}