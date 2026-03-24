package miku.server.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Server-wide event bus for real-time push notifications via WebSocket.
 *
 * Architecture:
 *   Service emits event → EventBus → All connected WebSocket clients
 *
 * Replaces polling patterns with push:
 *   Before: Frontend polls GET /downloads/status every 2s
 *   After:  Server pushes {"type":"download.progress","data":{...}} instantly
 */
object EventBus {
    private val _events = MutableSharedFlow<ServerEvent>(
        replay = 0,
        extraBufferCapacity = 256,
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun emit(event: ServerEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: ServerEvent) {
        _events.tryEmit(event)
    }

    fun serialize(event: ServerEvent): String = json.encodeToString(event)
}

@Serializable
data class ServerEvent(
    val type: String,
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        // Download events
        fun downloadStarted(downloadId: String, manga: String, chapter: String) = ServerEvent(
            type = "download.started",
            data = mapOf("downloadId" to downloadId, "manga" to manga, "chapter" to chapter),
        )

        fun downloadProgress(downloadId: String, downloaded: Int, total: Int, manga: String, chapter: String) = ServerEvent(
            type = "download.progress",
            data = mapOf(
                "downloadId" to downloadId,
                "downloaded" to downloaded.toString(),
                "total" to total.toString(),
                "manga" to manga,
                "chapter" to chapter,
            ),
        )

        fun downloadCompleted(downloadId: String, manga: String, chapter: String, pages: Int) = ServerEvent(
            type = "download.completed",
            data = mapOf("downloadId" to downloadId, "manga" to manga, "chapter" to chapter, "pages" to pages.toString()),
        )

        fun downloadFailed(downloadId: String, manga: String, chapter: String, error: String) = ServerEvent(
            type = "download.failed",
            data = mapOf("downloadId" to downloadId, "manga" to manga, "chapter" to chapter, "error" to error),
        )

        // Extension events
        fun extensionInstalling(pkg: String, name: String, step: String) = ServerEvent(
            type = "extension.installing",
            data = mapOf("pkg" to pkg, "name" to name, "step" to step),
        )

        fun extensionInstalled(pkg: String, name: String, sources: Int) = ServerEvent(
            type = "extension.installed",
            data = mapOf("pkg" to pkg, "name" to name, "sources" to sources.toString()),
        )

        fun extensionFailed(pkg: String, error: String) = ServerEvent(
            type = "extension.failed",
            data = mapOf("pkg" to pkg, "error" to error),
        )

        fun extensionUninstalled(pkg: String) = ServerEvent(
            type = "extension.uninstalled",
            data = mapOf("pkg" to pkg),
        )

        // Server events
        fun serverEvent(message: String) = ServerEvent(
            type = "server.info",
            data = mapOf("message" to message),
        )
    }
}
