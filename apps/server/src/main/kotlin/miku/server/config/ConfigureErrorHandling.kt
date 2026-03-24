package miku.server.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import miku.extension.runner.SourceExecutionException
import miku.extension.runner.SourceNotFoundException
import miku.extension.runner.SourceTimeoutException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("miku.server.errors")

fun Application.configureErrorHandling() {
    install(StatusPages) {
        // Source-related errors
        exception<SourceNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("source_not_found", cause.message ?: "Source not found"))
        }

        exception<SourceTimeoutException> { call, cause ->
            call.respond(HttpStatusCode.GatewayTimeout, ErrorResponse("source_timeout", cause.message ?: "Source timed out"))
        }

        exception<SourceExecutionException> { call, cause ->
            logger.error("Source execution error", cause)
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("source_error", "Failed to fetch data from source"))
        }

        // Validation errors
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("validation_error", cause.reasons.joinToString("; ")))
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Invalid request"))
        }

        // Auth errors
        exception<AuthenticationException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", cause.message ?: "Authentication required"))
        }

        exception<AuthorizationException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", cause.message ?: "Access denied"))
        }

        // Generic errors
        exception<Exception> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "An unexpected error occurred")
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Resource not found"))
        }

        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests. Please try again later."))
        }
    }
}

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)

class AuthenticationException(message: String = "Authentication required") : RuntimeException(message)
class AuthorizationException(message: String = "Access denied") : RuntimeException(message)
