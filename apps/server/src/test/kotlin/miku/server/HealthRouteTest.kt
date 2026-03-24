package miku.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HealthRouteTest {

    private val config = TestDatabaseContainer.getConfig()

    @Test
    fun `health endpoint should return ok`() = testApplication {
        application { module(config) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
    }
}
