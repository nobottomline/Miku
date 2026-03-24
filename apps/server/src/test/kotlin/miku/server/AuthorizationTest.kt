package miku.server

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import miku.server.route.TokenResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AuthorizationTest {
    companion object {
        private val config = TestDatabaseContainer.getConfig()
        private var adminToken = ""
        private var userToken = ""
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(username: String, email: String, password: String): String {
        val client = jsonClient()
        client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","email":"$email","password":"$password"}""")
        }
        val loginResp = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$username","password":"$password"}""")
        }
        return Json.decodeFromString<TokenResponse>(loginResp.bodyAsText()).accessToken
    }

    @Test
    @Order(1)
    fun `setup admin and user accounts`() = testApplication {
        application { module(config) }
        adminToken = registerAndLogin("authadmin", "authadmin@test.com", "Admin123A")
        userToken = registerAndLogin("authuser", "authuser@test.com", "User123A")
        assertTrue(adminToken.isNotBlank())
        assertTrue(userToken.isNotBlank())
    }

    // === Library requires auth ===

    @Test
    @Order(2)
    fun `library without token should return 401`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/library")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @Order(3)
    fun `library with valid token should return 200`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/library") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // === Categories require auth ===

    @Test
    @Order(4)
    fun `categories without token should return 401`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/categories")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @Order(5)
    fun `create category with token should succeed`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/categories") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Action"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    // === Extension reload requires ADMIN ===

    @Test
    @Order(6)
    fun `extension reload without token should return 401`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/extensions/reload")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @Order(7)
    fun `extension reload with user token should return 403`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/extensions/reload") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // === Expired/invalid token ===

    @Test
    @Order(8)
    fun `request with invalid token should return 401`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/auth/me") {
            header(HttpHeaders.Authorization, "Bearer invalid.token.here")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    @Order(9)
    fun `request with malformed auth header should return 401`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/auth/me") {
            header(HttpHeaders.Authorization, "NotBearer sometoken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // === Change password ===

    @Test
    @Order(10)
    fun `change password without auth should return 401`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/change-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"oldPassword":"Admin123A","newPassword":"NewPass123A"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
