package miku.server.route

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import miku.domain.model.ExtensionData
import miku.extension.runner.ExtensionManager
import miku.server.config.AuthorizationException
import org.koin.java.KoinJavaComponent.inject

fun Route.extensionRoutes() {
    val extensionManager : ExtensionManager by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    route("/extensions") {

        // List installed extensions
        get("/installed") {
            val extensions = extensionManager.registry.getAllExtensions().map { ext ->
                ExtensionData(
                    packageName = ext.packageName,
                    name = ext.name,
                    versionCode = ext.versionCode,
                    versionName = ext.versionName,
                    lang = ext.lang,
                    isNsfw = ext.isNsfw,
                    sourceCount = ext.sources.size,
                )
            }
            call.respond(extensions)
        }

        // List available extensions from keiyoushi repo
        get("/available") {
            val lang = call.parameters["lang"]
            val entries = if (lang != null) {
                extensionManager.repository.getExtensionsByLang(lang)
            } else {
                extensionManager.getAvailableExtensions()
            }

            val installed = extensionManager.registry.getAllExtensions()
                .associate { it.packageName to it.versionCode }

            val result = entries.map { entry ->
                AvailableExtension(
                    pkg = entry.pkg,
                    name = entry.name,
                    apk = entry.apk,
                    lang = entry.lang,
                    versionCode = entry.code,
                    versionName = entry.version,
                    isNsfw = entry.isNsfw,
                    sources = entry.sources.map { s ->
                        AvailableSource(name = s.name, lang = s.lang, id = s.id, baseUrl = s.baseUrl)
                    },
                    installed = installed.containsKey(entry.pkg),
                    hasUpdate = installed[entry.pkg]?.let { it < entry.code } ?: false,
                )
            }

            call.respond(result)
        }

        // Search available extensions
        get("/search") {
            val query = call.parameters["q"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing 'q' parameter"))
            val results = extensionManager.repository.searchExtensions(query)
            call.respond(results)
        }

        // Check for updates
        get("/updates") {
            val updates = extensionManager.checkUpdates()
            call.respond(updates)
        }

        // Install extension from repo
        post("/install/{pkg}") {
            val pkg = call.parameters["pkg"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing package name"))

            val extension = extensionManager.installFromRepo(pkg)
            call.respond(HttpStatusCode.Created, ExtensionData(
                packageName = extension.packageName,
                name = extension.name,
                versionCode = extension.versionCode,
                versionName = extension.versionName,
                lang = extension.lang,
                isNsfw = extension.isNsfw,
                sourceCount = extension.sources.size,
            ))
        }

        // Update extension
        post("/update/{pkg}") {
            val pkg = call.parameters["pkg"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing package name"))

            val updated = extensionManager.updateExtension(pkg)
            if (updated != null) {
                call.respond(ExtensionData(
                    packageName = updated.packageName,
                    name = updated.name,
                    versionCode = updated.versionCode,
                    versionName = updated.versionName,
                    lang = updated.lang,
                    isNsfw = updated.isNsfw,
                    sourceCount = updated.sources.size,
                ))
            } else {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Already up to date"))
            }
        }

        // Uninstall extension
        delete("/{pkg}") {
            val pkg = call.parameters["pkg"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing package name"))

            val success = extensionManager.uninstallExtension(pkg)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Uninstalled"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Extension not found"))
            }
        }

        // Admin-only: reload all extensions
        authenticate("auth-jwt") {
            post("/reload") {
                val principal = call.principal<JWTPrincipal>()!!
                val role = principal.payload.getClaim("role").asString()
                if (role != "ADMIN") throw AuthorizationException("Only admins can reload extensions")

                extensionManager.reloadExtensions()
                call.respond(mapOf(
                    "message" to "Extensions reloaded",
                    "extensions" to extensionManager.registry.getExtensionCount(),
                    "sources" to extensionManager.registry.getSourceCount(),
                ))
            }
        }
    }
}

@Serializable
data class AvailableExtension(
    val pkg: String,
    val name: String,
    val apk: String,
    val lang: String,
    val versionCode: Int,
    val versionName: String,
    val isNsfw: Boolean,
    val sources: List<AvailableSource>,
    val installed: Boolean,
    val hasUpdate: Boolean,
)

@Serializable
data class AvailableSource(
    val name: String,
    val lang: String,
    val id: Long,
    val baseUrl: String,
)
