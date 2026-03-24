package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper {
    val cookieJar = MemoryCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
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
}
