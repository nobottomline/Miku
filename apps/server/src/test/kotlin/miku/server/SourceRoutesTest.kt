package miku.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SourceRoutesTest {

    private val config = TestDatabaseContainer.getConfig()

    @Test
    fun `list sources should return array`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/sources")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonArray>(response.bodyAsText())
        assertNotNull(body)
    }

    @Test
    fun `list languages should return set`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/sources/languages")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `get nonexistent source should return 404`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/sources/99999999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `search with too long query should return 400`() = testApplication {
        application { module(config) }

        val longQuery = "a".repeat(201)
        val response = client.get("/api/v1/sources/1/search?q=$longQuery")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `search without query param should return 400`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/sources/1/search")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
