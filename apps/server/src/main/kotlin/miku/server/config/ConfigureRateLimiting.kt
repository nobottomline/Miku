package miku.server.config

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(RateLimitName("api")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
        }
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }
        register(RateLimitName("source")) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
        }
        register(RateLimitName("image")) {
            rateLimiter(limit = 200, refillPeriod = 1.minutes)
        }
    }
}
