package miku.server

data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val environment: String = "development",

    // JWT
    val jwtSecret: String,
    val jwtIssuer: String = "miku-server",
    val jwtAudience: String = "miku-client",
    val jwtAccessExpireMinutes: Long = 60,
    val jwtRefreshExpireDays: Long = 30,

    // Database
    val databaseUrl: String,
    val databaseUsername: String,
    val databasePassword: String,

    // CORS
    val allowedOrigins: List<String> = listOf("*"),

    // Extensions
    val extensionsDir: String = "extensions",

    // Security — Argon2id (OWASP recommended)
    val argon2Iterations: Int = 3,
    val argon2Memory: Int = 65536, // 64 MB
    val argon2Parallelism: Int = 4,
    val maxRequestBodySize: Long = 10 * 1024 * 1024, // 10MB
) {
    val isProduction: Boolean get() = environment == "production"

    companion object {
        fun load(): ServerConfig {
            return ServerConfig(
                host = env("SERVER_HOST", "0.0.0.0"),
                port = env("SERVER_PORT", "8080").toInt(),
                environment = env("SERVER_ENV", "development"),
                jwtSecret = env("JWT_SECRET", "miku-dev-secret-change-in-production-" + System.currentTimeMillis()),
                jwtIssuer = env("JWT_ISSUER", "miku-server"),
                jwtAudience = env("JWT_AUDIENCE", "miku-client"),
                jwtAccessExpireMinutes = env("JWT_ACCESS_EXPIRE_MINUTES", "60").toLong(),
                jwtRefreshExpireDays = env("JWT_REFRESH_EXPIRE_DAYS", "30").toLong(),
                databaseUrl = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/miku"),
                databaseUsername = env("DATABASE_USERNAME", "miku"),
                databasePassword = env("DATABASE_PASSWORD", "miku"),
                allowedOrigins = env("CORS_ORIGINS", "*").split(",").map { it.trim() },
                extensionsDir = env("EXTENSIONS_DIR", "extensions"),
                argon2Iterations = env("ARGON2_ITERATIONS", "3").toInt(),
                argon2Memory = env("ARGON2_MEMORY", "65536").toInt(),
                argon2Parallelism = env("ARGON2_PARALLELISM", "4").toInt(),
            )
        }

        private fun env(name: String, default: String): String =
            System.getenv(name) ?: System.getProperty(name) ?: default
    }
}

object BuildInfo {
    const val VERSION = "0.1.0"
    const val NAME = "Miku Server"
}
