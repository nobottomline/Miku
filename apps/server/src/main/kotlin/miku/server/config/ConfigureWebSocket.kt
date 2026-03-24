package miku.server.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collectLatest
import miku.server.service.EventBus
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("miku.server.websocket")
private val connectionCounter = AtomicInteger(0)
private val activeConnections = Collections.synchronizedSet(mutableSetOf<WebSocketServerSession>())

fun Application.configureWebSocket() {
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        /**
         * WebSocket endpoint for real-time server events.
         *
         * Connect: ws://localhost:8080/api/v1/ws
         *
         * Events pushed to client (JSON):
         *   {"type":"download.progress","data":{"downloadId":"dl_123","downloaded":"5","total":"11"},"timestamp":1234567890}
         *   {"type":"extension.installed","data":{"pkg":"...","name":"Asura Scans","sources":"1"},"timestamp":1234567890}
         *   {"type":"server.info","data":{"message":"Cache cleared"},"timestamp":1234567890}
         *
         * Client can send:
         *   {"type":"ping"} → server responds with {"type":"pong"}
         *   {"type":"subscribe","data":{"filter":"download.*"}} → filter events (future)
         */
        webSocket("/api/v1/ws") {
            val connId = connectionCounter.incrementAndGet()
            activeConnections.add(this)
            logger.info("WebSocket client #$connId connected (total: ${activeConnections.size})")

            try {
                // Send welcome event
                send(Frame.Text(EventBus.serialize(
                    miku.server.service.ServerEvent.serverEvent("Connected to Miku event stream")
                )))

                // Forward all EventBus events to this client
                EventBus.events.collectLatest { event ->
                    try {
                        send(Frame.Text(EventBus.serialize(event)))
                    } catch (_: Exception) {
                        // Client disconnected during send
                    }
                }
            } catch (_: Exception) {
                // Connection closed
            } finally {
                activeConnections.remove(this)
                logger.info("WebSocket client #$connId disconnected (remaining: ${activeConnections.size})")
            }
        }
    }
}

/**
 * Get number of active WebSocket connections.
 */
fun getActiveWebSocketCount(): Int = activeConnections.size
