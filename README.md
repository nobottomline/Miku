# Miku - Manga Aggregator Backend

![CI](https://github.com/nobottomline/miku/actions/workflows/ci.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-blue?logo=kotlin)
![Ktor](https://img.shields.io/badge/Ktor-3.1-purple)
![Tests](https://img.shields.io/badge/tests-99-brightgreen)
![License](https://img.shields.io/badge/license-MIT-green)

A professional manga aggregator backend service with full [keiyoushi](https://github.com/keiyoushi/extensions) extension compatibility. Automatically loads ~1500 manga source extensions via APK-to-JAR conversion pipeline, exposes REST + GraphQL APIs, and includes a modern web frontend.

## Architecture

```
                                    +------------------+
                                    |   Web Frontend   |
                                    |  React + Vite    |
                                    |  :3000           |
                                    +--------+---------+
                                             |
                                    +--------v---------+
                                    |   Ktor Server    |
                                    |   REST + GraphQL |
                                    |   :8080          |
                                    +--------+---------+
                                             |
                  +-------------+------------+------------+-------------+
                  |             |            |            |             |
           +------v-----+ +----v----+ +-----v-----+ +---v----+ +------v------+
           |  Extension | | Source  | |  Library  | |  Auth  | |    Image    |
           |  Manager   | |Executor | |  Service  | |Service | |    Proxy    |
           +------+-----+ +----+----+ +-----+-----+ +---+----+ +------+------+
                  |             |            |            |             |
           +------v-----+      |      +-----v-----+ +---v----+        |
           | APK -> JAR |      |      | PostgreSQL| | Argon2 |   SSRF Protection
           | (dex2jar)  |      |      | + Flyway  | |  JWT   |   IP Filtering
           +------+-----+      |      +-----------+ +--------+
                  |             |
           +------v-------------v------+
           |    Keiyoushi Extensions   |
           |   (~1500 manga sources)   |
           +---------------------------+
                       |
               +-------v--------+        +----------------+
               |  Redis Cache   |        |    Download    |
               |  30 min TTL    |        |    Service     |
               +----------------+        +-------+--------+
                                                 |
                                         +-------v--------+
                                         |     MinIO      |
                                         | (S3 Storage)   |
                                         | ZIP / PDF      |
                                         +----------------+
```

## Monorepo Structure

```
miku/
├── apps/
│   ├── server/              # Ktor 3.x backend (entry point)
│   │   ├── config/          # DI, auth, CORS, security, rate limiting, swagger
│   │   ├── graphql/         # GraphQL schema, queries, mutations
│   │   ├── route/           # REST API routes (v1)
│   │   └── service/         # Business logic, Redis cache, MinIO, downloads, CF bypass
│   └── web/                 # React + Vite + Tailwind CSS frontend
│
├── libs/
│   ├── extension-api/       # Mihon-compatible interfaces (eu.kanade.tachiyomi.*)
│   │   ├── source/          # Source, CatalogueSource, HttpSource, ParsedHttpSource
│   │   ├── model/           # SManga, SChapter, Page, Filter, FilterList
│   │   ├── network/         # OkHttp extensions, rate limiting, requests
│   │   └── keiyoushi/       # Full keiyoushi utils (parseAs, NextJs extraction)
│   │
│   ├── android-compat/      # Android API shims for JVM
│   │   ├── android.*        # Context, SharedPreferences, Uri, Log, Base64, etc.
│   │   └── androidx.*       # Preference stubs for ConfigurableSource
│   │
│   ├── extension-runner/    # Extension lifecycle management
│   │   ├── ApkExtractor     # DEX → JAR via dex2jar + Kotlin compat patching
│   │   ├── ExtensionLoader  # ClassLoader isolation (child-first for compat)
│   │   ├── ExtensionManager # Install/update/uninstall from keiyoushi repo
│   │   ├── ExtensionRepo    # Fetches index.min.json, downloads APKs
│   │   └── SourceExecutor   # Sandboxed execution with timeout (30s)
│   │
│   ├── domain/              # Clean Architecture domain layer
│   │   ├── model/           # Manga, Chapter, User, Category, SourceInfo
│   │   ├── repository/      # Repository interfaces
│   │   ├── service/         # Service interfaces (AuthService, SourceService)
│   │   └── usecase/         # Use cases (GetPopularManga, ManageLibrary, etc.)
│   │
│   └── database/            # PostgreSQL persistence
│       ├── table/           # Exposed ORM table definitions
│       ├── repository/      # Repository implementations
│       └── migration/       # Flyway SQL migrations
│
├── .github/workflows/       # CI/CD (build, test, Docker)
├── docker-compose.yml       # Production deployment
├── docker-compose.dev.yml   # Development (PostgreSQL + Redis + MinIO)
├── Dockerfile               # Multi-stage production build
└── .env.example             # Environment configuration template
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.1, JVM 17 |
| **Framework** | Ktor 3.1 (coroutine-native, lightweight) |
| **Database** | PostgreSQL 16 + Exposed ORM + Flyway migrations |
| **Caching** | Redis 7 (30 min TTL, graceful fallback) |
| **Storage** | MinIO (S3-compatible, for APK archives + manga downloads) |
| **Auth** | JWT (access + refresh tokens), Argon2id password hashing |
| **DI** | Koin |
| **API** | REST (versioned /api/v1/) + GraphQL + OpenAPI/Swagger |
| **Extension Pipeline** | dex2jar + ASM bytecode patching + child-first ClassLoader |
| **Frontend** | React 19, Vite, TypeScript, Tailwind CSS v4 |
| **Monitoring** | Prometheus + Grafana (custom dashboard, JVM + business metrics) |
| **Cloudflare Bypass** | FlareSolverr (automatic) + user cookie fallback |
| **DevOps** | Docker multi-stage, GitHub Actions CI/CD |

## Extension Pipeline

The core feature — running Mihon/Tachiyomi Android extensions on a JVM server:

```
1. Fetch index.min.json from keiyoushi GitHub repo
                    ↓
2. Download APK (Android package)
                    ↓
3. Extract DEX bytecode from APK (ZIP format)
                    ↓
4. Convert DEX → JAR via dex2jar library
                    ↓
5. Copy APK resources into JAR (i18n, assets — needed by multisrc extensions)
                    ↓
6. Patch JAR with Kotlin compatibility classes via ASM
   (Result, Duration, UInt, ULong — bridges Kotlin 1.x for 2.x runtime)
                    ↓
7. Load JAR via child-first ClassLoader
   (extension's patched classes take priority over stdlib)
                    ↓
8. Instantiate Source classes, register in SourceRegistry
                    ↓
9. Ready to serve manga data through API
```

Supports all ~1500 keiyoushi extensions without any modification.

## API Overview

### REST API (`/api/v1/`)

| Endpoint | Description |
|----------|-------------|
| `GET /health` | Server health + extension stats |
| `POST /api/v1/auth/register` | Register (first user = admin) |
| `POST /api/v1/auth/login` | Login → JWT tokens |
| `GET /api/v1/sources` | List installed manga sources |
| `GET /api/v1/sources/{id}/popular` | Popular manga with pagination |
| `GET /api/v1/sources/{id}/latest` | Latest updates |
| `GET /api/v1/sources/{id}/search?q=` | Search manga |
| `GET /api/v1/manga/{id}/details?url=` | Manga details |
| `GET /api/v1/manga/{id}/chapters?url=` | Chapter list |
| `GET /api/v1/manga/{id}/pages?url=` | Page image URLs |
| `GET /api/v1/extensions/available` | Browse ~1500 extensions |
| `POST /api/v1/extensions/install/{pkg}` | Install extension (APK→JAR) |
| `GET /api/v1/extensions/updates` | Check for extension updates |
| `POST /api/v1/extensions/update/{pkg}` | Update extension to latest version |
| `GET /api/v1/image/proxy?url=&sourceId=` | SSRF-protected image proxy (20MB limit) |
| `GET /api/v1/cloudflare/status` | FlareSolverr + cookie bypass status |
| `POST /api/v1/cloudflare/cookies` | Set user cookies for CF-protected domains |
| `GET /api/v1/repos` | List trusted extension repositories |
| `POST /api/v1/repos` | Add trusted repository (admin, GitHub-only) |
| `POST /api/v1/downloads/chapter` | Start chapter download to MinIO (background) |
| `GET /api/v1/downloads/status` | All downloads progress |
| `GET /api/v1/downloads/status/{id}` | Single download progress |
| `GET /api/v1/downloads/zip?sourceId=&manga=&chapter=` | Export chapter as ZIP |
| `GET /api/v1/downloads/pdf?sourceId=&manga=&chapter=` | Export chapter as PDF (JPEG 85%) |
| `GET /metrics` | Prometheus metrics (JVM + business) |
| `WS /api/v1/ws` | WebSocket real-time event stream |

Full documentation at **`/api/docs`** (Swagger UI) and **`/graphiql`** (GraphQL playground).

### GraphQL (`/api/graphql`)

```graphql
query {
  sources(lang: "en") { id name supportsLatest }
  popularManga(sourceId: "123", page: 1) { mangas { title thumbnailUrl } hasNextPage }
  mangaDetails(sourceId: "123", mangaUrl: "/manga/example") { title author genres }
}

mutation {
  login(username: "admin", password: "Pass1234") { accessToken refreshToken }
  addToLibrary(userId: 1, mangaId: 42)
}
```

## Security

| Protection | Implementation |
|-----------|---------------|
| **Password Hashing** | Argon2id (OWASP recommended, 64MB memory, 3 iterations) |
| **Authentication** | JWT with access (60 min) + refresh (30 day) tokens |
| **SQL Injection** | Exposed ORM parameterized queries + LIKE pattern escaping |
| **XSS** | React auto-escaping, CSP headers, no dangerouslySetInnerHTML |
| **SSRF** | Image proxy blocks private IPs, localhost, link-local, AWS metadata |
| **CRLF Injection** | Header value sanitization on image proxy |
| **Path Traversal** | Package name validation + file extension whitelist on uninstall |
| **Rate Limiting** | Ktor RateLimit plugin (auth: 10/min, API: 100/min, images: 200/min) |
| **Security Headers** | CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy |
| **Input Validation** | Regex on usernames/emails/packages, length limits, page bounds |
| **Extension Sandboxing** | Isolated ClassLoaders + coroutine timeout (30s per operation) |
| **APK Verification** | SHA256 checksum + URL pinning to trusted GitHub domains only |
| **Request Coalescing** | Single in-flight request per source operation (prevents IP bans) |
| **Image Size Limit** | 20MB max on image proxy (prevents OOM attacks) |

## Cloudflare Bypass

Many manga sources (~40%) use Cloudflare anti-bot protection. Miku uses a smart multi-layer bypass strategy integrated directly into the extension HTTP pipeline:

```
Extension makes HTTP request
              ↓
Strategy 1: Direct OkHttp request (~200ms)
              ↓ 403 Cloudflare?
Strategy 2: Inject FlareSolverr cookies + retry (~200ms)
              ↓ still 403? (TLS fingerprint mismatch)
Strategy 3: Full proxy through FlareSolverr Chrome (~5-10s, once per 30min)
              ↓ HTML response wrapped as OkHttp Response
Extension parses HTML normally — doesn't know about bypass
              ↓
Result cached in Redis for 30 minutes → next requests instant
```

**For images** (TLS fingerprint always matters):
```
Image request → OkHttp with CF cookies → 403?
              ↓
curl fallback (different TLS fingerprint) with CF cookies → 200
```

**Performance:** FlareSolverr is called **maximum once per 30 minutes per domain**. All subsequent requests are either cached or use cookies directly.

| Request type | First time | Cached |
|-------------|:---:|:---:|
| HTML (popular, search) | ~10s (FlareSolverr) | **~6ms** (Redis) |
| Images (thumbnails, pages) | ~600ms (curl + cookies) | ~400ms (curl, cookies cached) |

FlareSolverr is free, open-source (MIT), and runs as a Docker container on port 8191. Falls back to user-provided cookies via `POST /api/v1/cloudflare/cookies` if FlareSolverr is unavailable.

## Chapter Downloads

Download manga chapters for offline reading, export as ZIP or PDF:

```
POST /api/v1/downloads/chapter  →  Background download starts
                                    ↓
Pages fetched from source (with CF bypass if needed)
                                    ↓
Stored in MinIO (S3-compatible storage)
                                    ↓
GET /api/v1/downloads/zip   →  ZIP archive (original quality)
GET /api/v1/downloads/pdf   →  PDF document (JPEG 85%, optimized)
```

- **Background processing** — download runs asynchronously, poll progress via `/downloads/status`
- **Cloudflare support** — uses curl fallback for CF-protected image CDNs
- **PDF optimization** — WebP→JPEG 85% conversion (3-4x smaller than PNG, visually identical)
- **MinIO storage** — downloaded pages persist across server restarts

## Extension Repositories

Multi-repo architecture with security:

- **Default:** keiyoushi (official, ~1500 extensions)
- **Custom:** Admin can add repositories via `POST /api/v1/repos`
- **URL Pinning:** Only `raw.githubusercontent.com` and `github.com` domains accepted
- **SHA256 Checksum:** Every downloaded APK is verified against stored hash
- **Update Detection:** `GET /api/v1/extensions/updates` compares installed vs repo versions

## Monitoring

Built-in Prometheus metrics + pre-configured Grafana dashboard:

| Metric | Type | Description |
|--------|------|-------------|
| `ktor_http_server_requests_seconds` | Histogram | HTTP request duration by method/route/status |
| `miku_cache_hits_total` | Counter | Redis cache hits |
| `miku_cache_misses_total` | Counter | Redis cache misses |
| `miku_extensions_loaded` | Gauge | Currently loaded extensions |
| `miku_sources_active` | Gauge | Active manga sources |
| `miku_source_requests_total` | Counter | Requests to manga sources |
| `miku_source_errors_total` | Counter | Source request errors |
| `miku_extensions_installs_total` | Counter | Extension installations |
| `jvm_memory_used_bytes` | Gauge | JVM heap memory |
| `jvm_gc_pause_seconds` | Summary | Garbage collection pauses |

Grafana dashboard at `http://localhost:3001` (login: `admin` / `miku`).

## Real-Time Events (WebSocket)

Push-based architecture replaces polling for all dynamic operations:

```
Connect: ws://localhost:8080/api/v1/ws

← {"type":"download.started","data":{"downloadId":"dl_123","manga":"Nano Machine","chapter":"Ch 304"}}
← {"type":"download.progress","data":{"downloadId":"dl_123","downloaded":"5","total":"11"}}
← {"type":"download.completed","data":{"downloadId":"dl_123","pages":"11"}}
← {"type":"extension.installing","data":{"pkg":"...asurascans","step":"converting"}}
← {"type":"extension.installed","data":{"pkg":"...asurascans","sources":"1"}}
← {"type":"server.info","data":{"message":"Connected to Miku event stream"}}
```

**Event types:**

| Type | When |
|------|------|
| `download.started` | Chapter download begins |
| `download.progress` | Each page downloaded (real-time counter) |
| `download.completed` | All pages downloaded |
| `download.failed` | Download error |
| `extension.installing` | Install steps: downloading → converting → loading → completed |
| `extension.installed` | Extension ready with sources |
| `extension.failed` | Install error |
| `extension.uninstalled` | Extension removed |
| `server.info` | General server messages |

## Quick Start

### Prerequisites
- JDK 17+
- Docker + Docker Compose
- Node.js 20+ (for frontend)

### Development

```bash
# 1. Clone and enter
git clone https://github.com/nobottomline/miku.git
cd miku

# 2. Copy environment config
cp .env.example .env

# 3. Start infrastructure
docker compose -f docker-compose.dev.yml up -d

# 4. Start backend
./gradlew :apps:server:run

# 5. Start frontend (separate terminal)
cd apps/web && npm install && npm run dev
```

**Services:**

| Service | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| Frontend | http://localhost:3000 |
| Swagger Docs | http://localhost:8080/api/docs |
| GraphQL Playground | http://localhost:8080/graphiql |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin/miku) |
| MinIO Console | http://localhost:9001 (miku-admin/miku-secret-key) |
| FlareSolverr | http://localhost:8191 |

### First Steps

1. Open http://localhost:3000
2. Go to **Extensions** tab → search "Asura Scans" → click **Install**
3. Go back to **Sources** tab → click on the installed source
4. Browse popular manga, search, read chapters

### Production

```bash
docker compose up -d
```

This starts PostgreSQL, Redis, MinIO, and the Miku server as a single stack.

## Testing

```bash
# Run all tests
./gradlew :apps:server:test

# Frontend type check
cd apps/web && npx tsc --noEmit
```

## Caching Strategy

```
Request → Redis lookup (key: miku:source:{id}:{operation}:{page})
            ↓ miss
  Fetch from manga source website (via extension)
            ↓
  Store in Redis (TTL: 30 minutes)
            ↓
  Return to client

Cache behavior:
  - Empty results are NOT cached (prevents caching transient errors)
  - TTL: 30 minutes (configurable via REDIS_CACHE_TTL_MINUTES)
  - Graceful fallback: if Redis is down, requests go directly to source
  - Cache invalidation: per-source flush on extension reinstall
```

**Performance impact:**
| Request | Without Cache | With Cache |
|---------|:---:|:---:|
| Popular manga | ~500ms | **~6ms** |
| Search | ~800ms | **~5ms** |
| Manga details | ~400ms | **~4ms** |

## Environment Variables

See [`.env.example`](.env.example) for all configuration options. Key variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/miku` | PostgreSQL connection |
| `JWT_SECRET` | — | **Must set in production** |
| `REDIS_URL` | `redis://localhost:6379` | Redis for caching |
| `REDIS_CACHE_TTL_MINUTES` | `30` | Cache duration |
| `MINIO_ENDPOINT` | `http://localhost:9000` | S3-compatible storage |
| `MINIO_ACCESS_KEY` | `miku-admin` | MinIO access key |
| `MINIO_SECRET_KEY` | `miku-secret-key` | MinIO secret key |
| `FLARESOLVERR_URL` | `http://localhost:8191` | FlareSolverr endpoint |
| `CORS_ORIGINS` | `*` | Allowed origins |

## Differentiators from Suwayomi

| Aspect | Suwayomi | Miku |
|--------|----------|------|
| Architecture | Monolith | Clean Architecture (6 modules) |
| API | REST only | REST + GraphQL + OpenAPI/Swagger |
| Auth | Basic/none | JWT + Argon2id + RBAC |
| Security | Minimal | SSRF, CSP, SQL injection, XSS, CRLF, path traversal |
| Database | H2/SQLite | PostgreSQL + Flyway migrations |
| Caching | None | Redis (80x speedup, stampede protection) |
| Storage | Local filesystem | MinIO (S3-compatible) |
| Downloads | Basic | Background download + ZIP + PDF export |
| Cloudflare | Cookie injection | 3-layer: FlareSolverr proxy + cookies + curl fallback |
| Monitoring | None | Prometheus + Grafana (11-panel dashboard) |
| Frontend | Built-in legacy UI | Modern React + Tailwind SPA |
| DevOps | Docker | Docker + CI/CD + multi-stage build |
| Kotlin compat | Bundled stdlib | ASM bytecode patching + child-first ClassLoader |
| Multi-repo | Single repo | Multiple repos with SHA256 verification + URL pinning |

## License

MIT
