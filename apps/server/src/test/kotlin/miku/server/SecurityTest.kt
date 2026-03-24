package miku.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SecurityTest {

    private val config = TestDatabaseContainer.getConfig()

    @Test
    fun `security headers should be present`() = testApplication {
        application { module(config) }

        val response = client.get("/health")

        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("DENY", response.headers["X-Frame-Options"])
        assertEquals("1; mode=block", response.headers["X-XSS-Protection"])
        assertNotNull(response.headers["Content-Security-Policy"])
        assertNotNull(response.headers["Strict-Transport-Security"])
        assertNotNull(response.headers["Referrer-Policy"])
    }

    @Test
    fun `CORS preflight should succeed`() = testApplication {
        application { module(config) }

        val response = client.options("/api/v1/sources") {
            header(HttpHeaders.Origin, "http://localhost:3000")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }

        // Should not be 403 Forbidden
        assertNotEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy should reject internal IPs`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/image/proxy?url=http://127.0.0.1/secret")

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy should reject AWS metadata`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/image/proxy?url=http://169.254.169.254/latest/meta-data/")

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy should reject private networks`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/image/proxy?url=http://192.168.1.1/admin")

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy without url param should return 400`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/image/proxy")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `protected routes should require auth`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/library")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `404 should return structured error`() = testApplication {
        application { module(config) }

        val response = client.get("/api/v1/nonexistent")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `register with SQL injection attempt should be safe`() = testApplication {
        application { module(config) }

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin'; DROP TABLE users;--","email":"hack@test.com","password":"Hack1234!"}""")
        }

        // Should fail validation (special characters), not execute SQL
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
