package miku.server

object TestDatabaseContainer {
    fun getConfig(): ServerConfig {
        return ServerConfig(
            host = "0.0.0.0",
            port = 0,
            environment = "test",
            jwtSecret = "test-secret-key-for-testing-only-32chars",
            databaseUrl = System.getenv("TEST_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/miku_test",
            databaseUsername = System.getenv("TEST_DATABASE_USERNAME") ?: "miku",
            databasePassword = System.getenv("TEST_DATABASE_PASSWORD") ?: "miku",
            argon2Iterations = 1,
            argon2Memory = 4096,
            argon2Parallelism = 1,
        )
    }
}
