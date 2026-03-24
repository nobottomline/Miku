package miku.server.route

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import miku.extension.runner.ExtensionRegistry
import miku.server.BuildInfo
import org.koin.java.KoinJavaComponent.inject

fun Route.healthRoutes() {
    val registry : ExtensionRegistry by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    get("/health") {
        call.respond(HttpStatusCode.OK, HealthResponse(
            status = "ok",
            version = BuildInfo.VERSION,
            extensions = registry.getExtensionCount(),
            sources = registry.getSourceCount(),
        ))
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val extensions: Int,
    val sources: Int,
)
