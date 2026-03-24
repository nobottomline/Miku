package miku.server.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cloudflare bypass service with three-layer strategy:
 * 1. FlareSolverr (automatic headless browser)
 * 2. User-provided cookies (manual fallback)
 * 3. Error message asking user to provide cookies
 */
class CloudflareBypassService(
    private val flareSolverrUrl: String = System.getenv("FLARESOLVERR_URL") ?: "http://localhost:8191",
) {
    private val logger = LoggerFactory.getLogger(CloudflareBypassService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // FlareSolverr can be slow
        .build()

    // Cache: domain → cookies (from FlareSolverr or user)
    private val cookieCache = ConcurrentHashMap<String, CookieSet>()

    // User-provided cookies per domain
    private val userCookies = ConcurrentHashMap<String, String>()

    data class CookieSet(
        val cookies: String,
        val userAgent: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        // Cookies expire after 30 minutes
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > 30 * 60 * 1000
    }

    /**
     * Get Cloudflare bypass cookies for a domain.
     * Returns null if no bypass is available.
     */
    fun getCookiesForDomain(domain: String): CookieSet? {
        // Check cache first
        val cached = cookieCache[domain]
        if (cached != null && !cached.isExpired()) return cached

        // Try user-provided cookies
        val userCookie = userCookies[domain]
        if (userCookie != null) {
            return CookieSet(userCookie, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }

        // Try FlareSolverr
        return tryFlareSolverr(domain)
    }

    /**
     * Try to get Cloudflare cookies via FlareSolverr.
     */
    private fun tryFlareSolverr(domain: String): CookieSet? {
        if (!isFlareSolverrAvailable()) return null

        return try {
            val requestBody = json.encodeToString(FlareSolverrRequest.serializer(), FlareSolverrRequest(
                cmd = "request.get",
                url = "https://$domain/",
                maxTimeout = 30000,
            ))

            val request = Request.Builder()
                .url("$flareSolverrUrl/v1")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                logger.warn("FlareSolverr returned HTTP ${response.code} for $domain")
                return null
            }

            val body = response.body.string()
            val result = json.decodeFromString<FlareSolverrResponse>(body)

            if (result.status == "ok" && result.solution != null) {
                val cookies = result.solution.cookies
                    .joinToString("; ") { "${it.name}=${it.value}" }
                val cookieSet = CookieSet(cookies, result.solution.userAgent)
                cookieCache[domain] = cookieSet
                logger.info("FlareSolverr bypass successful for $domain")
                cookieSet
            } else {
                logger.warn("FlareSolverr failed for $domain: ${result.message}")
                null
            }
        } catch (e: Exception) {
            logger.debug("FlareSolverr not available for $domain: ${e.message}")
            null
        }
    }

    /**
     * Set user-provided cookies for a domain.
     * Called when user manually provides cookies through the API.
     */
    fun setUserCookies(domain: String, cookies: String) {
        userCookies[domain] = cookies
        // Also put in cache
        cookieCache[domain] = CookieSet(cookies, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        logger.info("User cookies set for $domain")
    }

    fun getUserCookies(domain: String): String? = userCookies[domain]

    fun clearCookies(domain: String) {
        cookieCache.remove(domain)
        userCookies.remove(domain)
    }

    /**
     * Full proxy: send URL through FlareSolverr and return the complete HTML response.
     * This bypasses TLS fingerprint issues because FlareSolverr uses a real Chrome browser.
     */
    fun proxyRequest(url: String): eu.kanade.tachiyomi.network.CloudflareProxyInterceptor.ProxyResult? {
        if (!isFlareSolverrAvailable()) return null

        return try {
            val requestBody = json.encodeToString(FlareSolverrRequest.serializer(), FlareSolverrRequest(
                cmd = "request.get",
                url = url,
                maxTimeout = 45000,
            ))

            val request = Request.Builder()
                .url("$flareSolverrUrl/v1")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body.string()
            // FlareSolverr may return control characters in HTML, parse leniently
            val result = try {
                json.decodeFromString<FlareSolverrResponse>(body)
            } catch (_: Exception) {
                // Fallback: manual extraction
                null
            } ?: return null

            if (result.status == "ok" && result.solution != null) {
                val sol = result.solution
                val cookieMap = sol.cookies.associate { it.name to it.value }

                // Cache cookies for future direct requests
                val domain = try { java.net.URI(url).host } catch (_: Exception) { null }
                if (domain != null) {
                    val cookieStr = sol.cookies.joinToString("; ") { "${it.name}=${it.value}" }
                    cookieCache[domain] = CookieSet(cookieStr, sol.userAgent)
                }

                logger.info("FlareSolverr proxied $url (${sol.response.length} chars)")
                eu.kanade.tachiyomi.network.CloudflareProxyInterceptor.ProxyResult(
                    body = sol.response,
                    statusCode = sol.status,
                    cookies = cookieMap,
                    userAgent = sol.userAgent,
                )
            } else {
                logger.warn("FlareSolverr proxy failed for $url: ${result.message}")
                null
            }
        } catch (e: Exception) {
            logger.error("FlareSolverr proxy error for $url: ${e.message}")
            null
        }
    }

    fun isFlareSolverrAvailable(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$flareSolverrUrl/health")
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    fun getStatus(): Map<String, Any> = mapOf(
        "flareSolverrAvailable" to isFlareSolverrAvailable(),
        "cachedDomains" to cookieCache.keys.toList(),
        "userCookieDomains" to userCookies.keys.toList(),
    )
}

@Serializable
private data class FlareSolverrRequest(
    val cmd: String,
    val url: String,
    val maxTimeout: Int = 30000,
)

@Serializable
private data class FlareSolverrResponse(
    val status: String,
    val message: String = "",
    val solution: FlareSolverrSolution? = null,
)

@Serializable
private data class FlareSolverrSolution(
    val url: String = "",
    val status: Int = 0,
    val cookies: List<FlareSolverrCookie> = emptyList(),
    val userAgent: String = "",
    val response: String = "",
)

@Serializable
private data class FlareSolverrCookie(
    val name: String,
    val value: String,
    val domain: String = "",
)
