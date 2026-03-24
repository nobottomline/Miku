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
import miku.server.service.CloudflareBypassService
import miku.server.service.DownloadService
import miku.server.service.MinioStorageService
import miku.server.service.RedisCacheService
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

                // Infrastructure
                single { RedisCacheService() }
                single { MinioStorageService() }
                single { CloudflareBypassService() }

                // Repositories
                single<MangaRepository> { ExposedMangaRepository() }
                single<ChapterRepository> { ExposedChapterRepository() }
                single<LibraryRepository> { ExposedLibraryRepository() }
                single<UserRepository> { ExposedUserRepository() }

                // Extension system
                single {
                    ExtensionManager(File(config.extensionsDir)).also { mgr ->
                        mgr.initialize()

                        // Wire Cloudflare bypass into extension HTTP pipeline
                        val cfService = get<CloudflareBypassService>()
                        val proxy = mgr.networkHelper.cloudflareProxy

                        // Strategy 2: cookie-only bypass
                        proxy.onSolveCloudflareCookies = { domain ->
                            val cookies = cfService.getCookiesForDomain(domain)
                            if (cookies != null) {
                                val parsed = cookies.cookies.split("; ")
                                    .filter { it.contains("=") }
                                    .associate { it.substringBefore("=") to it.substringAfter("=") }
                                mgr.networkHelper.injectCookies(domain, parsed, cookies.userAgent)
                                true
                            } else false
                        }

                        // Strategy 3: full proxy through FlareSolverr
                        proxy.onProxyThroughSolver = { url ->
                            cfService.proxyRequest(url)
                        }

                        // Wire extension install events → WebSocket push
                        mgr.onInstallEvent = { pkg, name, step ->
                            miku.server.service.EventBus.tryEmit(
                                miku.server.service.ServerEvent.extensionInstalling(pkg, name, step)
                            )
                        }
                    }
                }
                single { get<ExtensionManager>().registry }
                single { SourceExecutor(get()) }

                // Services
                single { MikuSourceService(get(), get(), get()) }
                single { DownloadService(get(), get(), get()) }
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
