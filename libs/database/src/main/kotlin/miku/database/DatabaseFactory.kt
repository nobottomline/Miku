package miku.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

class DatabaseFactory(private val config: DatabaseConfig) {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private lateinit var dataSource: HikariDataSource

    fun connect(): Database {
        logger.info("Connecting to database at ${config.url}")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.username
            password = config.password
            driverClassName = config.driver
            maximumPoolSize = config.maxPoolSize
            minimumIdle = config.minIdle
            idleTimeout = config.idleTimeout
            connectionTimeout = config.connectionTimeout
            maxLifetime = config.maxLifetime
            isAutoCommit = false

            // Security: prevent SQL injection via connection properties
            addDataSourceProperty("prepareThreshold", "5")
            addDataSourceProperty("preparedStatementCacheQueries", "256")
        }

        dataSource = HikariDataSource(hikariConfig)

        // Run migrations
        runMigrations()

        return Database.connect(dataSource)
    }

    private fun runMigrations() {
        logger.info("Running database migrations...")
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()

        val result = flyway.migrate()
        logger.info("Applied ${result.migrationsExecuted} migrations")
    }

    fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
    }
}

data class DatabaseConfig(
    val url: String = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/miku",
    val username: String = System.getenv("DATABASE_USERNAME") ?: "miku",
    val password: String = System.getenv("DATABASE_PASSWORD") ?: "miku",
    val driver: String = "org.postgresql.Driver",
    val maxPoolSize: Int = 10,
    val minIdle: Int = 2,
    val idleTimeout: Long = 600_000,
    val connectionTimeout: Long = 30_000,
    val maxLifetime: Long = 1_800_000,
)
