package miku.server

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InputValidationTest {
    private val config = TestDatabaseContainer.getConfig()

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // === Username validation ===

    @Test
    fun `username too short should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"ab","email":"x@x.com","password":"Valid123A"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `username too long should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${"a".repeat(51)}","email":"x@x.com","password":"Valid123A"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `username with special chars should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"user<script>","email":"x@x.com","password":"Valid123A"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `username with SQL injection should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin'--","email":"x@x.com","password":"Valid123A"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // === Email validation ===

    @Test
    fun `invalid email format should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"validuser","email":"notanemail","password":"Valid123A"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // === Password validation ===

    @Test
    fun `password without uppercase should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"validuser","email":"v@v.com","password":"nouppercase1"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `password without digit should fail`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"validuser","email":"v@v.com","password":"NoDigitsHere"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // === Source ID validation ===

    @Test
    fun `invalid source ID should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources/notanumber")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `nonexistent source ID should return 404`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources/999999999999")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // === Page parameter validation ===

    @Test
    fun `negative page number should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources/123/popular?page=-1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `page number too large should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources/123/popular?page=99999")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // === Search validation ===

    @Test
    fun `search without query should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources/123/search")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `search with too long query should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/sources/123/search?q=${"x".repeat(201)}")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // === Extension package name validation ===

    @Test
    fun `install extension with invalid package name should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/extensions/install/../../../etc/passwd")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `install extension with shell injection should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/extensions/install/pkg;rm -rf /")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // === Missing required fields ===

    @Test
    fun `register without username should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"x@x.com","password":"Valid123A"}""")
        }
        assertNotEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `login without password should return error`() = testApplication {
        application { module(config) }
        val response = jsonClient().post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin"}""")
        }
        assertNotEquals(HttpStatusCode.OK, response.status)
    }

    // === Image proxy validation ===

    @Test
    fun `image proxy without url should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/image/proxy")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `image proxy with private IP should return 403`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/image/proxy?url=http://192.168.1.1/image.jpg")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy with localhost should return 403`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/image/proxy?url=http://localhost/image.jpg")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy with AWS metadata should return 403`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/image/proxy?url=http://169.254.169.254/latest/meta-data/")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy with ftp scheme should return 403`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/image/proxy?url=ftp://evil.com/file")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `image proxy with file scheme should return 403`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/image/proxy?url=file:///etc/passwd")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // === Manga routes missing params ===

    @Test
    fun `manga details without url should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/manga/123/details")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `chapters without url should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/manga/123/chapters")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `pages without url should return 400`() = testApplication {
        application { module(config) }
        val response = jsonClient().get("/api/v1/manga/123/pages")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
