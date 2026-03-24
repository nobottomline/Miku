package miku.server.route

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import miku.domain.usecase.library.ManageLibraryUseCase
import miku.domain.repository.LibraryRepository
import org.koin.java.KoinJavaComponent.inject

fun Route.libraryRoutes() {
    val libraryUseCase : ManageLibraryUseCase by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }
    val libraryRepository : LibraryRepository by lazy { org.koin.java.KoinJavaComponent.getKoin().get() }

    authenticate("auth-jwt") {
        route("/library") {
            get {
                val userId = call.getUserId()
                val categoryId = call.parameters["categoryId"]?.toLongOrNull()
                val library = libraryUseCase.getLibrary(userId, categoryId)
                call.respond(library)
            }

            post("/add") {
                val userId = call.getUserId()
                val request = call.receive<AddToLibraryRequest>()
                libraryUseCase.addToLibrary(userId, request.mangaId, request.categoryId)
                call.respond(HttpStatusCode.Created, mapOf("message" to "Added to library"))
            }

            delete("/{mangaId}") {
                val userId = call.getUserId()
                val mangaId = call.parameters["mangaId"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid manga ID"))
                libraryUseCase.removeFromLibrary(userId, mangaId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Removed from library"))
            }
        }

        route("/categories") {
            get {
                val userId = call.getUserId()
                val categories = libraryRepository.getCategories(userId)
                call.respond(categories)
            }

            post {
                val userId = call.getUserId()
                val request = call.receive<CreateCategoryRequest>()
                val category = libraryRepository.createCategory(userId, request.name)
                call.respond(HttpStatusCode.Created, category)
            }

            delete("/{categoryId}") {
                val userId = call.getUserId()
                val categoryId = call.parameters["categoryId"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid category ID"))
                libraryRepository.deleteCategory(userId, categoryId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Category deleted"))
            }
        }
    }
}

fun io.ktor.server.routing.RoutingCall.getUserId(): Long {
    val principal = principal<JWTPrincipal>()!!
    return principal.payload.getClaim("userId").asLong()
}

@Serializable data class AddToLibraryRequest(val mangaId: Long, val categoryId: Long? = null)
@Serializable data class CreateCategoryRequest(val name: String)
