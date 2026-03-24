package miku.server.config

import io.ktor.server.application.*
import miku.database.DatabaseConfig
import miku.database.DatabaseFactory
import miku.database.repository.ExposedChapterRepository
import miku.database.repository.ExposedLibraryRepository
import miku.database.repository.ExposedMangaRepository
import miku.database.repository.ExposedUserRepository
import miku.domain.repository.ChapterRepository
import miku.domain.repository.LibraryRepository
import miku.domain.repository.MangaRepository
import miku.domain.repository.UserRepository
import miku.domain.usecase.library.ManageLibraryUseCase
import miku.domain.usecase.manga.*
import miku.extension.runner.ExtensionManager
import miku.extension.runner.SourceExecutor
import miku.server.ServerConfig
import miku.server.service.JwtService
import miku.server.service.MikuAuthService
import miku.server.service.MikuSourceService
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.io.File

fun Application.configureDI(config: ServerConfig) {
    install(Koin) {
        slf4jLogger()
        modules(
            module {
                // Config
                single { config }

                // Database (eager initialization)
                single(createdAtStart = true) {
                    DatabaseFactory(
                        DatabaseConfig(
                            url = config.databaseUrl,
                            username = config.databaseUsername,
                            password = config.databasePassword,
                        )
                    ).also { it.connect() }
                }

                // Repositories
                single<MangaRepository> { ExposedMangaRepository() }
                single<ChapterRepository> { ExposedChapterRepository() }
                single<LibraryRepository> { ExposedLibraryRepository() }
                single<UserRepository> { ExposedUserRepository() }

                // Extension system
                single { ExtensionManager(File(config.extensionsDir)).also { it.initialize() } }
                single { get<ExtensionManager>().registry }
                single { SourceExecutor(get()) }

                // Services
                single { MikuSourceService(get(), get()) }
                single { JwtService(config) }
                single { MikuAuthService(get(), get(), config) }

                // Use Cases
                single { GetPopularMangaUseCase(get<MikuSourceService>()) }
                single { SearchMangaUseCase(get<MikuSourceService>()) }
                single { GetMangaDetailsUseCase(get<MikuSourceService>(), get()) }
                single { GetChapterListUseCase(get<MikuSourceService>(), get(), get()) }
                single { GetPageListUseCase(get<MikuSourceService>()) }
                single { ManageLibraryUseCase(get(), get()) }
            }
        )
    }
}
