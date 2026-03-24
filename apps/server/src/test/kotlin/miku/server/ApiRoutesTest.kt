package miku.server

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiRoutesTest {
    private val config = TestDatabaseContainer.getConfig()

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // === Health ===

    @Test
    fun `health should return ok with version`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
        assertNotNull(body["version"])
        assertNotNull(body["extensions"])
        assertNotNull(body["sources"])
        assertNotNull(body["wsConnections"])
        assertNotNull(body["redisConnected"])
    }

    // === Sources ===

    @Test
    fun `list sources should return array`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonArray>(response.bodyAsText())
        assertNotNull(body)
    }

    @Test
    fun `list sources with lang filter should return array`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources?lang=en")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `list languages should return array`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources/languages")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // === Extensions (local operations only — no external HTTP) ===

    @Test
    fun `list installed extensions should return array`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/extensions/installed")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonArray>(response.bodyAsText())
        assertNotNull(body)
    }

    @Test
    fun `search extensions without query should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/extensions/search")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `uninstall nonexistent extension should return 404`() = testApplication {
        application { module(config) }
        val response = jsonClient().delete("/api/v1/extensions/com.nonexistent.pkg")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // === Repos ===

    @Test
    fun `list repos should return default keiyoushi`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/repos")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonArray>(response.bodyAsText())
        assertTrue(body.size >= 1)
    }

    // === Cloudflare ===

    @Test
    fun `set cloudflare cookies should succeed`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/cloudflare/cookies") {
            contentType(ContentType.Application.Json)
            setBody("""{"domain":"example.com","cookies":"cf_clearance=test123"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `set cloudflare cookies without domain should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/cloudflare/cookies") {
            contentType(ContentType.Application.Json)
            setBody("""{"domain":"","cookies":"cf_clearance=test123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `clear cloudflare cookies should succeed`() = testApplication {
        application { module(config) }
        val response = jsonClient().delete("/api/v1/cloudflare/cookies/example.com")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // === Downloads ===

    @Test
    fun `get download status should return map`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/downloads/status")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `get nonexistent download should return 404`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/downloads/status/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `download zip without params should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/downloads/zip")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `download pdf without params should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/downloads/pdf")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `download zip for non-downloaded chapter should return 404`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/downloads/zip?sourceId=1&manga=Test&chapter=Ch1")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // === Swagger ===

    @Test
    fun `swagger docs should return HTML`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/docs")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("swagger-ui"))
    }

    @Test
    fun `openapi json should return valid spec`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("3.1.0", body["openapi"]?.jsonPrimitive?.content)
        assertNotNull(body["paths"])
    }

    // === 404 handling ===

    @Test
    fun `unknown route should return structured error`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // === Metrics ===

    @Test
    fun `metrics endpoint should return prometheus format`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("jvm_"))
    }
}
