package miku.server.config

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

val appMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun Application.configureMetrics() {
    install(MicrometerMetrics) {
        registry = appMeterRegistry

        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ClassLoaderMetrics(),
            ProcessorMetrics(),
            UptimeMetrics(),
        )

        // Track all HTTP request metrics automatically
        timers { call, _ ->
            tag("method", call.request.local.method.value)
            tag("route", call.request.local.uri.substringBefore("?").take(100))
            tag("status", call.response.status()?.value?.toString() ?: "unknown")
        }
    }

    routing {
        get("/metrics") {
            call.respondText(appMeterRegistry.scrape())
        }
    }
}

/**
 * Custom metrics helpers for business logic tracking.
 * Use these from services to record domain-specific metrics.
 */
object MikuMetrics {
    private val cacheHits = appMeterRegistry.counter("miku.cache.hits")
    private val cacheMisses = appMeterRegistry.counter("miku.cache.misses")
    private val extensionsLoaded = appMeterRegistry.gauge("miku.extensions.loaded", java.util.concurrent.atomic.AtomicInteger(0))!!
    private val sourcesActive = appMeterRegistry.gauge("miku.sources.active", java.util.concurrent.atomic.AtomicInteger(0))!!
    private val extensionInstalls = appMeterRegistry.counter("miku.extensions.installs")
    private val sourceRequests = appMeterRegistry.counter("miku.source.requests")
    private val sourceErrors = appMeterRegistry.counter("miku.source.errors")

    fun recordCacheHit() = cacheHits.increment()
    fun recordCacheMiss() = cacheMisses.increment()
    fun setExtensionsLoaded(count: Int) { (extensionsLoaded as java.util.concurrent.atomic.AtomicInteger).set(count) }
    fun setSourcesActive(count: Int) { (sourcesActive as java.util.concurrent.atomic.AtomicInteger).set(count) }
    fun recordExtensionInstall() = extensionInstalls.increment()
    fun recordSourceRequest() = sourceRequests.increment()
    fun recordSourceError() = sourceErrors.increment()
}
