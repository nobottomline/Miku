package eu.kanade.tachiyomi.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkHelper {
    val cookieJar = MemoryCookieJar()

    private val cloudflareInterceptor = CloudflareProxyInterceptor(cookieJar)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(cloudflareInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    @Deprecated("The regular client handles requests")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    val nonCloudflareClient: OkHttpClient = client

    fun defaultUserAgentProvider(): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun injectCookies(domain: String, cookies: Map<String, String>, userAgent: String? = null) {
        val url = "https://$domain/".toHttpUrlOrNull() ?: return
        val cookieList = cookies.map { (name, value) ->
            Cookie.Builder().domain(domain).path("/").name(name).value(value).build()
        }
        cookieJar.saveFromResponse(url, cookieList)
        if (userAgent != null) {
            cloudflareInterceptor.userAgentOverride[domain] = userAgent
        }
    }

    /**
     * Access the interceptor for wiring callbacks from server side.
     */
    val cloudflareProxy: CloudflareProxyInterceptor get() = cloudflareInterceptor
}

/**
 * Smart Cloudflare bypass interceptor with three strategies:
 *
 * 1. DIRECT: Normal OkHttp request (fast, ~200ms)
 *    ↓ 403 Cloudflare?
 * 2. COOKIE RETRY: Retry with cached FlareSolverr cookies (fast, ~200ms)
 *    ↓ still 403? (TLS fingerprint mismatch)
 * 3. FULL PROXY: Send entire request through FlareSolverr (slow, ~5s, once per 30min)
 *    FlareSolverr returns full HTML → we wrap it as OkHttp Response
 *
 * Strategy 3 is the fallback that always works because FlareSolverr uses
 * a real Chrome browser (matching TLS fingerprint).
 * Results from strategy 3 are cached by Redis (30 min) so subsequent
 * requests hit cache and never reach FlareSolverr again.
 */
class CloudflareProxyInterceptor(
    private val cookieJar: MemoryCookieJar,
) : Interceptor {

    val userAgentOverride = ConcurrentHashMap<String, String>()

    // Domains known to need full proxy (skip cookie-only retry)
    private val fullProxyDomains = ConcurrentHashMap.newKeySet<String>()

    // Domains where CF was recently bypassed with cookies (try cookies first)
    private val cookieBypassDomains = ConcurrentHashMap.newKeySet<String>()

    /**
     * Callback: try to solve CF challenge and inject cookies.
     * Returns true if cookies were obtained.
     */
    var onSolveCloudflareCookies: ((domain: String) -> Boolean)? = null

    /**
     * Callback: proxy entire request through FlareSolverr.
     * Input: full URL. Returns: Pair(html, httpStatus) or null if failed.
     */
    var onProxyThroughSolver: ((url: String) -> ProxyResult?)? = null

    data class ProxyResult(
        val body: String,
        val statusCode: Int,
        val cookies: Map<String, String> = emptyMap(),
        val userAgent: String = "",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val domain = request.url.host
        val url = request.url.toString()

        // Apply cached User-Agent override
        userAgentOverride[domain]?.let { ua ->
            request = request.newBuilder().header("User-Agent", ua).build()
        }

        // If this domain is known to need full proxy, skip direct request entirely
        if (domain in fullProxyDomains) {
            val proxyResult = onProxyThroughSolver?.invoke(url)
            if (proxyResult != null && proxyResult.statusCode in 200..299) {
                cacheProxyCookies(domain, proxyResult)
                return buildFakeResponse(request, proxyResult)
            }
        }

        // Strategy 1: Direct request
        val response = chain.proceed(request)

        if (response.code != 403 || !isCloudflare(response)) {
            return response // Not Cloudflare — return as-is
        }

        System.err.println("[CF-Bypass] Cloudflare 403 for $domain")

        // Strategy 2: Cookie retry (only if not already known to need full proxy)
        if (domain !in fullProxyDomains) {
            val cookiesSolved = onSolveCloudflareCookies?.invoke(domain) ?: false
            if (cookiesSolved) {
                response.close()
                val cookies = cookieJar.loadForRequest(request.url)
                val retryRequest = request.newBuilder()
                    .header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
                    .header("User-Agent", userAgentOverride[domain] ?: request.header("User-Agent") ?: "")
                    .build()

                val retryResponse = chain.proceed(retryRequest)
                if (retryResponse.code != 403) {
                    cookieBypassDomains.add(domain)
                    System.err.println("[CF-Bypass] Cookie retry succeeded for $domain")
                    return retryResponse
                }
                retryResponse.close()
                System.err.println("[CF-Bypass] Cookie retry failed (TLS fingerprint?), escalating to full proxy")
            }
        }

        // Strategy 3: Full proxy through FlareSolverr
        response.close()
        fullProxyDomains.add(domain)
        val proxyResult = onProxyThroughSolver?.invoke(url)

        if (proxyResult != null && proxyResult.statusCode in 200..299) {
            cacheProxyCookies(domain, proxyResult)
            System.err.println("[CF-Bypass] Full proxy succeeded for $domain (${proxyResult.body.length} chars)")
            return buildFakeResponse(request, proxyResult)
        }

        System.err.println("[CF-Bypass] All strategies failed for $domain")
        // Return a 403 with clear message
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Cloudflare protection - bypass failed")
            .body("Cloudflare protection active. FlareSolverr could not bypass.".toResponseBody("text/plain".toMediaType()))
            .build()
    }

    /**
     * Build a fake OkHttp Response from FlareSolverr's proxied result.
     * The extension will parse this HTML as if OkHttp fetched it directly.
     */
    private fun buildFakeResponse(request: okhttp3.Request, result: ProxyResult): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(result.statusCode)
            .message("OK")
            .body(result.body.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()
    }

    private fun cacheProxyCookies(domain: String, result: ProxyResult) {
        if (result.cookies.isNotEmpty()) {
            val url = "https://$domain/".toHttpUrlOrNull() ?: return
            val cookieList = result.cookies.map { (name, value) ->
                Cookie.Builder().domain(domain).path("/").name(name).value(value).build()
            }
            cookieJar.saveFromResponse(url, cookieList)
        }
        if (result.userAgent.isNotBlank()) {
            userAgentOverride[domain] = result.userAgent
        }
    }

    private fun isCloudflare(response: Response): Boolean {
        val server = response.header("server")?.lowercase() ?: ""
        return server.contains("cloudflare") || response.header("cf-ray") != null
    }
}
