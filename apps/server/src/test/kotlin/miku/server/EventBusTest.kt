package miku.server

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import miku.server.service.EventBus
import miku.server.service.ServerEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EventBusTest {

    @Test
    fun `emit and collect event`() = runBlocking {
        val collected = mutableListOf<ServerEvent>()

        val job = launch {
            EventBus.events.take(1).toList(collected)
        }

        delay(50)
        EventBus.emit(ServerEvent.serverEvent("test message"))
        job.join()

        assertEquals(1, collected.size)
        assertEquals("server.info", collected[0].type)
        assertEquals("test message", collected[0].data["message"])
    }

    @Test
    fun `tryEmit should not block`() {
        val result = EventBus.tryEmit(ServerEvent.serverEvent("non-blocking"))
        // tryEmit returns true if buffered, may return false if buffer full
        // Either way should not throw
        assertNotNull(result)
    }

    @Test
    fun `download events should have correct types`() {
        val started = ServerEvent.downloadStarted("dl1", "Manga", "Ch1")
        assertEquals("download.started", started.type)
        assertEquals("dl1", started.data["downloadId"])

        val progress = ServerEvent.downloadProgress("dl1", 5, 10, "Manga", "Ch1")
        assertEquals("download.progress", progress.type)
        assertEquals("5", progress.data["downloaded"])
        assertEquals("10", progress.data["total"])

        val completed = ServerEvent.downloadCompleted("dl1", "Manga", "Ch1", 10)
        assertEquals("download.completed", completed.type)

        val failed = ServerEvent.downloadFailed("dl1", "Manga", "Ch1", "timeout")
        assertEquals("download.failed", failed.type)
        assertEquals("timeout", failed.data["error"])
    }

    @Test
    fun `extension events should have correct types`() {
        val installing = ServerEvent.extensionInstalling("pkg", "Name", "downloading")
        assertEquals("extension.installing", installing.type)
        assertEquals("downloading", installing.data["step"])

        val installed = ServerEvent.extensionInstalled("pkg", "Name", 3)
        assertEquals("extension.installed", installed.type)
        assertEquals("3", installed.data["sources"])

        val failed = ServerEvent.extensionFailed("pkg", "error msg")
        assertEquals("extension.failed", failed.type)

        val uninstalled = ServerEvent.extensionUninstalled("pkg")
        assertEquals("extension.uninstalled", uninstalled.type)
    }

    @Test
    fun `event serialization should produce valid JSON`() {
        val event = ServerEvent.downloadProgress("dl1", 3, 10, "Test", "Ch1")
        val json = EventBus.serialize(event)
        assertTrue(json.contains("download.progress"))
        assertTrue(json.contains("\"downloaded\":\"3\""))
        assertTrue(json.contains("\"total\":\"10\""))
    }

    @Test
    fun `events should have timestamp`() {
        val before = System.currentTimeMillis()
        val event = ServerEvent.serverEvent("test")
        val after = System.currentTimeMillis()
        assertTrue(event.timestamp in before..after)
    }
}
