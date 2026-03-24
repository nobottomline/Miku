package miku.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import miku.server.route.TokenResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AuthRoutesTest {

    companion object {
        private val config = TestDatabaseContainer.getConfig()
        private var accessToken: String = ""
        private var refreshToken: String = ""
    }

    private fun ApplicationTestBuilder.createClient() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    @Order(1)
    fun `register first user should succeed and return ADMIN role`() = testApplication {
        application { module(config) }
        val client = createClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","email":"admin@miku.dev","password":"Admin123!"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("ADMIN", body["role"]?.jsonPrimitive?.content)
    }

    @Test
    @Order(2)
    fun `register duplicate username should fail`() = testApplication {
        application { module(config) }
        val client = createClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","email":"other@miku.dev","password":"Admin123!"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    @Order(3)
    fun `register with weak password should fail`() = testApplication {
        application { module(config) }
        val client = createClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"weakuser","email":"weak@miku.dev","password":"weak"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    @Order(4)
    fun `login with valid credentials should return tokens`() = testApplication {
        application { module(config) }
        val client = createClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"Admin123!"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val tokens = Json.decodeFromString<TokenResponse>(response.bodyAsText())
        assertTrue(tokens.accessToken.isNotBlank())
        assertTrue(tokens.refreshToken.isNotBlank())
        assertTrue(tokens.expiresIn > 0)

        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
    }

    @Test
    @Order(5)
    fun `login with wrong password should fail`() = testApplication {
        application { module(config) }
        val client = createClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"WrongPass123!"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @Order(6)
    fun `get profile with valid token should succeed`() = testApplication {
        application { module(config) }
        val client = createClient()

        // Login first to get fresh token
        val loginResp = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"Admin123!"}""")
        }
        val tokens = Json.decodeFromString<TokenResponse>(loginResp.bodyAsText())

        val response = client.get("/api/v1/auth/me") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("admin", body["username"]?.jsonPrimitive?.content)
    }

    @Test
    @Order(7)
    fun `get profile without token should fail`() = testApplication {
        application { module(config) }
        val client = createClient()

        val response = client.get("/api/v1/auth/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @Order(8)
    fun `refresh token should return new tokens`() = testApplication {
        application { module(config) }
        val client = createClient()

        // Login first
        val loginResp = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"Admin123!"}""")
        }
        val tokens = Json.decodeFromString<TokenResponse>(loginResp.bodyAsText())

        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${tokens.refreshToken}"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val newTokens = Json.decodeFromString<TokenResponse>(response.bodyAsText())
        assertTrue(newTokens.accessToken.isNotBlank())
    }

    @Test
    @Order(9)
    fun `register second user should get USER role`() = testApplication {
        application { module(config) }
        val client = createClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"reader","email":"reader@miku.dev","password":"Reader123!"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("USER", body["role"]?.jsonPrimitive?.content)
    }
}
