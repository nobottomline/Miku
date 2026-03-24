package miku.server.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import miku.server.BuildInfo

fun Application.configureSwagger() {
    routing {
        get("/api/docs") {
            call.respondText(swaggerHtml(), ContentType.Text.Html)
        }
        get("/api/openapi.json") {
            call.respondText(openApiSpec(), ContentType.Application.Json)
        }
    }
}

private fun swaggerHtml() = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Miku API Documentation</title>
    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css" />
</head>
<body>
    <div id="swagger-ui"></div>
    <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js"></script>
    <script>
        SwaggerUIBundle({
            url: "/api/openapi.json",
            dom_id: '#swagger-ui',
            deepLinking: true,
            presets: [SwaggerUIBundle.presets.apis],
            layout: "BaseLayout"
        });
    </script>
</body>
</html>
""".trimIndent()

private fun openApiSpec() = """
{
  "openapi": "3.1.0",
  "info": {
    "title": "Miku API",
    "version": "${BuildInfo.VERSION}",
    "description": "Professional manga aggregator backend with full keiyoushi extension compatibility. Supports ~1500 manga source extensions via automatic APK-to-JAR conversion pipeline.\n\n## Authentication\nMost endpoints are public. Library management and admin actions require JWT authentication.\nUse `/api/v1/auth/login` to obtain tokens, then pass `Authorization: Bearer <token>` header.\n\n## Rate Limits\n- Auth: 10 requests/minute\n- Source data: 30 requests/minute\n- General API: 100 requests/minute\n- Image proxy: 200 requests/minute\n\n## Extensions\nSupports all ~1500 keiyoushi/Tachiyomi extensions via automatic APK DEX-to-JAR conversion.",
    "license": { "name": "MIT" }
  },
  "servers": [
    { "url": "http://localhost:8080", "description": "Development" }
  ],
  "tags": [
    { "name": "System", "description": "Health check and server status" },
    { "name": "Authentication", "description": "User registration, login, and token management" },
    { "name": "Sources", "description": "Manga source listing and browsing" },
    { "name": "Manga", "description": "Manga details, chapters, and pages" },
    { "name": "Library", "description": "Personal manga library management (requires auth)" },
    { "name": "Categories", "description": "Library category management (requires auth)" },
    { "name": "Extensions", "description": "Extension installation and management" },
    { "name": "Downloads", "description": "Chapter download, ZIP and PDF export via MinIO storage" },
    { "name": "Image", "description": "Image proxy with source-specific headers and Cloudflare bypass" },
    { "name": "Cloudflare", "description": "Cloudflare bypass management (FlareSolverr + user cookies)" },
    { "name": "Repositories", "description": "Extension repository management (multi-repo, trusted domains)" },
    { "name": "Monitoring", "description": "Prometheus metrics and server monitoring" }
  ],
  "components": {
    "securitySchemes": {
      "bearerAuth": {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT"
      }
    },
    "schemas": {
      "ErrorResponse": {
        "type": "object",
        "properties": {
          "error": { "type": "string" }
        }
      },
      "MessageResponse": {
        "type": "object",
        "properties": {
          "message": { "type": "string" }
        }
      },
      "SourceInfo": {
        "type": "object",
        "properties": {
          "id": { "type": "string", "description": "Source ID (string to avoid JS precision loss)" },
          "name": { "type": "string" },
          "lang": { "type": "string" },
          "supportsLatest": { "type": "boolean" },
          "isNsfw": { "type": "boolean" },
          "baseUrl": { "type": "string", "nullable": true }
        }
      },
      "Manga": {
        "type": "object",
        "properties": {
          "id": { "type": "integer" },
          "sourceId": { "type": "integer" },
          "url": { "type": "string" },
          "title": { "type": "string" },
          "artist": { "type": "string", "nullable": true },
          "author": { "type": "string", "nullable": true },
          "description": { "type": "string", "nullable": true },
          "genres": { "type": "array", "items": { "type": "string" } },
          "status": { "type": "string", "enum": ["UNKNOWN", "ONGOING", "COMPLETED", "LICENSED", "PUBLISHING_FINISHED", "CANCELLED", "ON_HIATUS"] },
          "thumbnailUrl": { "type": "string", "nullable": true },
          "initialized": { "type": "boolean" }
        }
      },
      "MangaPageResult": {
        "type": "object",
        "properties": {
          "mangas": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/Manga" } },
          "hasNextPage": { "type": "boolean" },
          "page": { "type": "integer" }
        }
      },
      "Chapter": {
        "type": "object",
        "properties": {
          "url": { "type": "string" },
          "name": { "type": "string" },
          "chapterNumber": { "type": "number" },
          "scanlator": { "type": "string", "nullable": true },
          "dateUpload": { "type": "string", "nullable": true }
        }
      },
      "PageData": {
        "type": "object",
        "properties": {
          "index": { "type": "integer" },
          "url": { "type": "string" },
          "imageUrl": { "type": "string", "nullable": true }
        }
      },
      "RegisterRequest": {
        "type": "object",
        "required": ["username", "email", "password"],
        "properties": {
          "username": { "type": "string", "minLength": 3, "maxLength": 50 },
          "email": { "type": "string", "format": "email" },
          "password": { "type": "string", "minLength": 8 }
        }
      },
      "LoginRequest": {
        "type": "object",
        "required": ["username", "password"],
        "properties": {
          "username": { "type": "string" },
          "password": { "type": "string" }
        }
      },
      "RefreshRequest": {
        "type": "object",
        "required": ["refreshToken"],
        "properties": {
          "refreshToken": { "type": "string" }
        }
      },
      "ChangePasswordRequest": {
        "type": "object",
        "required": ["oldPassword", "newPassword"],
        "properties": {
          "oldPassword": { "type": "string" },
          "newPassword": { "type": "string" }
        }
      },
      "TokenResponse": {
        "type": "object",
        "properties": {
          "accessToken": { "type": "string" },
          "refreshToken": { "type": "string" },
          "expiresIn": { "type": "integer" }
        }
      },
      "UserResponse": {
        "type": "object",
        "properties": {
          "id": { "type": "integer" },
          "username": { "type": "string" },
          "email": { "type": "string" },
          "role": { "type": "string" }
        }
      },
      "ExtensionData": {
        "type": "object",
        "properties": {
          "packageName": { "type": "string" },
          "name": { "type": "string" },
          "versionCode": { "type": "integer" },
          "versionName": { "type": "string" },
          "lang": { "type": "string" },
          "isNsfw": { "type": "boolean" },
          "sourceCount": { "type": "integer" }
        }
      },
      "AvailableExtension": {
        "type": "object",
        "properties": {
          "pkg": { "type": "string" },
          "name": { "type": "string" },
          "apk": { "type": "string" },
          "lang": { "type": "string" },
          "versionCode": { "type": "integer" },
          "versionName": { "type": "string" },
          "isNsfw": { "type": "boolean" },
          "sources": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "lang": { "type": "string" },
                "id": { "type": "integer" },
                "baseUrl": { "type": "string" }
              }
            }
          },
          "installed": { "type": "boolean" },
          "hasUpdate": { "type": "boolean" }
        }
      },
      "HealthResponse": {
        "type": "object",
        "properties": {
          "status": { "type": "string" },
          "version": { "type": "string" },
          "extensions": { "type": "integer" },
          "sources": { "type": "integer" }
        }
      },
      "AddToLibraryRequest": {
        "type": "object",
        "required": ["mangaId"],
        "properties": {
          "mangaId": { "type": "integer" },
          "categoryId": { "type": "integer", "nullable": true }
        }
      },
      "CreateCategoryRequest": {
        "type": "object",
        "required": ["name"],
        "properties": {
          "name": { "type": "string" }
        }
      },
      "SetCookiesRequest": {
        "type": "object",
        "required": ["domain", "cookies"],
        "properties": {
          "domain": { "type": "string", "description": "Domain to set cookies for (e.g. asuracomic.net)" },
          "cookies": { "type": "string", "description": "Cookie string (e.g. cf_clearance=abc123; __cf_bm=xyz)" }
        }
      },
      "CloudflareStatus": {
        "type": "object",
        "properties": {
          "flareSolverrAvailable": { "type": "boolean" },
          "cachedDomains": { "type": "array", "items": { "type": "string" } },
          "userCookieDomains": { "type": "array", "items": { "type": "string" } }
        }
      },
      "RepoInfo": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "url": { "type": "string" }
        }
      },
      "AddRepoRequest": {
        "type": "object",
        "required": ["name", "url"],
        "properties": {
          "name": { "type": "string", "description": "Repository display name" },
          "url": { "type": "string", "description": "Repository base URL (must be from trusted GitHub domain)" }
        }
      }
    }
  },
  "paths": {
    "/health": {
      "get": {
        "tags": ["System"],
        "summary": "Health check",
        "operationId": "healthCheck",
        "responses": {
          "200": {
            "description": "Server status",
            "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/HealthResponse" } } }
          }
        }
      }
    },
    "/api/v1/auth/register": {
      "post": {
        "tags": ["Authentication"],
        "summary": "Register new user",
        "operationId": "register",
        "description": "First registered user automatically gets ADMIN role. Password requirements: minimum 8 characters, at least 1 uppercase letter, at least 1 digit.",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/RegisterRequest" } } }
        },
        "responses": {
          "201": { "description": "User created", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/UserResponse" } } } },
          "400": { "description": "Validation error", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } } }
        }
      }
    },
    "/api/v1/auth/login": {
      "post": {
        "tags": ["Authentication"],
        "summary": "Login",
        "operationId": "login",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/LoginRequest" } } }
        },
        "responses": {
          "200": { "description": "JWT tokens", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/TokenResponse" } } } },
          "401": { "description": "Invalid credentials", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } } }
        }
      }
    },
    "/api/v1/auth/refresh": {
      "post": {
        "tags": ["Authentication"],
        "summary": "Refresh access token",
        "operationId": "refreshToken",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/RefreshRequest" } } }
        },
        "responses": {
          "200": { "description": "New JWT tokens", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/TokenResponse" } } } },
          "401": { "description": "Invalid refresh token", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ErrorResponse" } } } }
        }
      }
    },
    "/api/v1/auth/me": {
      "get": {
        "tags": ["Authentication"],
        "summary": "Get current user profile",
        "operationId": "getCurrentUser",
        "security": [{ "bearerAuth": [] }],
        "responses": {
          "200": { "description": "User profile", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/UserResponse" } } } },
          "401": { "description": "Unauthorized" }
        }
      }
    },
    "/api/v1/auth/change-password": {
      "post": {
        "tags": ["Authentication"],
        "summary": "Change password",
        "operationId": "changePassword",
        "security": [{ "bearerAuth": [] }],
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ChangePasswordRequest" } } }
        },
        "responses": {
          "200": { "description": "Password changed", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "401": { "description": "Unauthorized" }
        }
      }
    },
    "/api/v1/sources": {
      "get": {
        "tags": ["Sources"],
        "summary": "List all installed sources",
        "operationId": "listSources",
        "parameters": [
          { "name": "lang", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Filter by language code (en, ru, ja, etc.)" }
        ],
        "responses": {
          "200": { "description": "List of sources", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/SourceInfo" } } } } }
        }
      }
    },
    "/api/v1/sources/languages": {
      "get": {
        "tags": ["Sources"],
        "summary": "List available languages",
        "operationId": "listLanguages",
        "responses": {
          "200": { "description": "Language codes", "content": { "application/json": { "schema": { "type": "array", "items": { "type": "string" } } } } }
        }
      }
    },
    "/api/v1/sources/{sourceId}": {
      "get": {
        "tags": ["Sources"],
        "summary": "Get source details",
        "operationId": "getSource",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Numeric source ID" }
        ],
        "responses": {
          "200": { "description": "Source details", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/SourceInfo" } } } },
          "400": { "description": "Invalid source ID" },
          "404": { "description": "Source not found" }
        }
      }
    },
    "/api/v1/sources/{sourceId}/filters": {
      "get": {
        "tags": ["Sources"],
        "summary": "Get source filter options",
        "operationId": "getSourceFilters",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "responses": {
          "200": { "description": "Filter definitions for the source" },
          "400": { "description": "Invalid source ID" }
        }
      }
    },
    "/api/v1/sources/{sourceId}/popular": {
      "get": {
        "tags": ["Sources"],
        "summary": "Get popular manga from source",
        "operationId": "getPopularManga",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "page", "in": "query", "required": false, "schema": { "type": "integer", "default": 1 } }
        ],
        "responses": {
          "200": { "description": "Paginated manga list", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MangaPageResult" } } } },
          "400": { "description": "Invalid source ID" }
        }
      }
    },
    "/api/v1/sources/{sourceId}/latest": {
      "get": {
        "tags": ["Sources"],
        "summary": "Get latest updates from source",
        "operationId": "getLatestManga",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "page", "in": "query", "required": false, "schema": { "type": "integer", "default": 1 } }
        ],
        "responses": {
          "200": { "description": "Paginated manga list", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MangaPageResult" } } } },
          "400": { "description": "Invalid source ID" }
        }
      }
    },
    "/api/v1/sources/{sourceId}/search": {
      "get": {
        "tags": ["Sources"],
        "summary": "Search manga on source",
        "operationId": "searchManga",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "q", "in": "query", "required": true, "schema": { "type": "string", "maxLength": 200 }, "description": "Search query (max 200 chars)" },
          { "name": "page", "in": "query", "required": false, "schema": { "type": "integer", "default": 1 } }
        ],
        "responses": {
          "200": { "description": "Search results", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MangaPageResult" } } } },
          "400": { "description": "Invalid source ID or missing/too-long query" }
        }
      }
    },
    "/api/v1/manga/{sourceId}/details": {
      "get": {
        "tags": ["Manga"],
        "summary": "Get manga details",
        "operationId": "getMangaDetails",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "url", "in": "query", "required": true, "schema": { "type": "string" }, "description": "Manga URL path from source" }
        ],
        "responses": {
          "200": { "description": "Manga details", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/Manga" } } } },
          "400": { "description": "Invalid source ID or missing URL" }
        }
      }
    },
    "/api/v1/manga/{sourceId}/chapters": {
      "get": {
        "tags": ["Manga"],
        "summary": "Get chapter list",
        "operationId": "getChapterList",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "url", "in": "query", "required": true, "schema": { "type": "string" }, "description": "Manga URL path" }
        ],
        "responses": {
          "200": { "description": "Chapter list", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/Chapter" } } } } },
          "400": { "description": "Invalid source ID or missing URL" }
        }
      }
    },
    "/api/v1/manga/{sourceId}/pages": {
      "get": {
        "tags": ["Manga"],
        "summary": "Get chapter pages",
        "operationId": "getPageList",
        "parameters": [
          { "name": "sourceId", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "url", "in": "query", "required": true, "schema": { "type": "string" }, "description": "Chapter URL path" }
        ],
        "responses": {
          "200": { "description": "Page list", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/PageData" } } } } },
          "400": { "description": "Invalid source ID or missing URL" }
        }
      }
    },
    "/api/v1/library": {
      "get": {
        "tags": ["Library"],
        "summary": "Get user library",
        "operationId": "getLibrary",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "categoryId", "in": "query", "required": false, "schema": { "type": "integer" }, "description": "Filter by category ID" }
        ],
        "responses": {
          "200": { "description": "Library entries" },
          "401": { "description": "Unauthorized" }
        }
      }
    },
    "/api/v1/library/add": {
      "post": {
        "tags": ["Library"],
        "summary": "Add manga to library",
        "operationId": "addToLibrary",
        "security": [{ "bearerAuth": [] }],
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/AddToLibraryRequest" } } }
        },
        "responses": {
          "201": { "description": "Added to library", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "401": { "description": "Unauthorized" }
        }
      }
    },
    "/api/v1/library/{mangaId}": {
      "delete": {
        "tags": ["Library"],
        "summary": "Remove manga from library",
        "operationId": "removeFromLibrary",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "mangaId", "in": "path", "required": true, "schema": { "type": "integer" } }
        ],
        "responses": {
          "200": { "description": "Removed", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "description": "Invalid manga ID" },
          "401": { "description": "Unauthorized" }
        }
      }
    },
    "/api/v1/categories": {
      "get": {
        "tags": ["Categories"],
        "summary": "List library categories",
        "operationId": "listCategories",
        "security": [{ "bearerAuth": [] }],
        "responses": {
          "200": { "description": "Category list" },
          "401": { "description": "Unauthorized" }
        }
      },
      "post": {
        "tags": ["Categories"],
        "summary": "Create a category",
        "operationId": "createCategory",
        "security": [{ "bearerAuth": [] }],
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/CreateCategoryRequest" } } }
        },
        "responses": {
          "201": { "description": "Category created" },
          "401": { "description": "Unauthorized" }
        }
      }
    },
    "/api/v1/categories/{categoryId}": {
      "delete": {
        "tags": ["Categories"],
        "summary": "Delete a category",
        "operationId": "deleteCategory",
        "security": [{ "bearerAuth": [] }],
        "parameters": [
          { "name": "categoryId", "in": "path", "required": true, "schema": { "type": "integer" } }
        ],
        "responses": {
          "200": { "description": "Category deleted", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "description": "Invalid category ID" },
          "401": { "description": "Unauthorized" }
        }
      }
    },
    "/api/v1/extensions/installed": {
      "get": {
        "tags": ["Extensions"],
        "summary": "List installed extensions",
        "operationId": "listInstalledExtensions",
        "responses": {
          "200": { "description": "Installed extensions", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/ExtensionData" } } } } }
        }
      }
    },
    "/api/v1/extensions/available": {
      "get": {
        "tags": ["Extensions"],
        "summary": "List available extensions from keiyoushi repository",
        "operationId": "listAvailableExtensions",
        "parameters": [
          { "name": "lang", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Filter by language code" }
        ],
        "responses": {
          "200": { "description": "Available extensions with install status", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/AvailableExtension" } } } } }
        }
      }
    },
    "/api/v1/extensions/search": {
      "get": {
        "tags": ["Extensions"],
        "summary": "Search available extensions",
        "operationId": "searchExtensions",
        "parameters": [
          { "name": "q", "in": "query", "required": true, "schema": { "type": "string" }, "description": "Search query" }
        ],
        "responses": {
          "200": { "description": "Matching extensions" },
          "400": { "description": "Missing query parameter" }
        }
      }
    },
    "/api/v1/extensions/updates": {
      "get": {
        "tags": ["Extensions"],
        "summary": "Check for extension updates",
        "operationId": "checkExtensionUpdates",
        "responses": {
          "200": { "description": "Extensions with available updates" }
        }
      }
    },
    "/api/v1/extensions/install/{pkg}": {
      "post": {
        "tags": ["Extensions"],
        "summary": "Install extension from keiyoushi repository",
        "operationId": "installExtension",
        "description": "Downloads APK, converts DEX to JAR via dex2jar, loads sources into the runtime.",
        "parameters": [
          { "name": "pkg", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Package name (e.g. eu.kanade.tachiyomi.extension.en.asurascans)" }
        ],
        "responses": {
          "201": { "description": "Extension installed", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ExtensionData" } } } },
          "400": { "description": "Missing package name" }
        }
      }
    },
    "/api/v1/extensions/update/{pkg}": {
      "post": {
        "tags": ["Extensions"],
        "summary": "Update an installed extension",
        "operationId": "updateExtension",
        "parameters": [
          { "name": "pkg", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "responses": {
          "200": { "description": "Updated extension or already up to date", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/ExtensionData" } } } },
          "400": { "description": "Missing package name" }
        }
      }
    },
    "/api/v1/extensions/{pkg}": {
      "delete": {
        "tags": ["Extensions"],
        "summary": "Uninstall extension",
        "operationId": "uninstallExtension",
        "parameters": [
          { "name": "pkg", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "responses": {
          "200": { "description": "Uninstalled", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "404": { "description": "Extension not found" }
        }
      }
    },
    "/api/v1/extensions/reload": {
      "post": {
        "tags": ["Extensions"],
        "summary": "Reload all extensions (admin only)",
        "operationId": "reloadExtensions",
        "security": [{ "bearerAuth": [] }],
        "description": "Requires ADMIN role. Reloads all extensions from disk.",
        "responses": {
          "200": { "description": "Reload summary with extension and source counts" },
          "401": { "description": "Unauthorized" },
          "403": { "description": "Only admins can reload extensions" }
        }
      }
    },
    "/api/v1/image/proxy": {
      "get": {
        "tags": ["Image"],
        "summary": "Proxy manga images with source-specific headers",
        "operationId": "proxyImage",
        "description": "SSRF-protected image proxy. Blocks private IPs, localhost, link-local addresses, and cloud metadata endpoints (AWS 169.254.169.254, GCP metadata.google.internal).",
        "parameters": [
          { "name": "url", "in": "query", "required": true, "schema": { "type": "string" }, "description": "Image URL to proxy" },
          { "name": "sourceId", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Source ID for source-specific headers (Referer, cookies, etc.)" }
        ],
        "responses": {
          "200": { "description": "Image bytes with appropriate Content-Type", "content": { "image/*": {} } },
          "400": { "description": "Missing URL parameter" },
          "403": { "description": "SSRF blocked - URL targets internal/private network" },
          "502": { "description": "Failed to fetch image or response is not an image" }
        }
      }
    },
    "/api/v1/cloudflare/status": {
      "get": {
        "tags": ["Cloudflare"],
        "summary": "Get Cloudflare bypass status",
        "operationId": "getCloudflareStatus",
        "description": "Returns FlareSolverr availability, cached bypass cookies, and user-provided cookie domains.",
        "responses": {
          "200": { "description": "Bypass status", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/CloudflareStatus" } } } }
        }
      }
    },
    "/api/v1/cloudflare/cookies": {
      "post": {
        "tags": ["Cloudflare"],
        "summary": "Set user cookies for Cloudflare-protected domain",
        "operationId": "setCloudfareCookies",
        "description": "Fallback when FlareSolverr cannot solve the challenge. User provides cookies obtained from their browser. Cookies are cached for 30 minutes.",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/SetCookiesRequest" } } }
        },
        "responses": {
          "200": { "description": "Cookies set", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "400": { "description": "Missing domain or cookies" }
        }
      }
    },
    "/api/v1/cloudflare/cookies/{domain}": {
      "delete": {
        "tags": ["Cloudflare"],
        "summary": "Clear cookies for a domain",
        "operationId": "clearCloudflareCookies",
        "parameters": [
          { "name": "domain", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "responses": {
          "200": { "description": "Cookies cleared", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } }
        }
      }
    },
    "/api/v1/repos": {
      "get": {
        "tags": ["Repositories"],
        "summary": "List trusted extension repositories",
        "operationId": "listRepos",
        "description": "Returns all configured extension repositories. Default: keiyoushi.",
        "responses": {
          "200": { "description": "Repository list", "content": { "application/json": { "schema": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/RepoInfo" } } } } }
        }
      },
      "post": {
        "tags": ["Repositories"],
        "summary": "Add trusted extension repository (admin only)",
        "operationId": "addRepo",
        "security": [{ "bearerAuth": [] }],
        "description": "Add a new extension repository. URL must be from a trusted GitHub domain (raw.githubusercontent.com, github.com). Requires ADMIN role.",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/AddRepoRequest" } } }
        },
        "responses": {
          "201": { "description": "Repository added", "content": { "application/json": { "schema": { "${'$'}ref": "#/components/schemas/MessageResponse" } } } },
          "401": { "description": "Unauthorized" },
          "403": { "description": "URL not from trusted domain or not admin" }
        }
      }
    },
    "/api/v1/downloads/chapter": {
      "post": {
        "tags": ["Downloads"],
        "summary": "Start chapter download",
        "operationId": "startDownload",
        "description": "Downloads all pages of a chapter to MinIO storage in the background. Returns a download ID for progress tracking.",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "type": "object", "required": ["sourceId", "mangaTitle", "chapterUrl", "chapterName"], "properties": { "sourceId": { "type": "integer" }, "mangaTitle": { "type": "string" }, "chapterUrl": { "type": "string" }, "chapterName": { "type": "string" } } } } }
        },
        "responses": {
          "202": { "description": "Download started", "content": { "application/json": { "schema": { "type": "object", "properties": { "downloadId": { "type": "string" }, "message": { "type": "string" } } } } } }
        }
      }
    },
    "/api/v1/downloads/status": {
      "get": {
        "tags": ["Downloads"],
        "summary": "Get all downloads progress",
        "operationId": "getAllDownloads",
        "responses": { "200": { "description": "Map of download ID to progress" } }
      }
    },
    "/api/v1/downloads/status/{downloadId}": {
      "get": {
        "tags": ["Downloads"],
        "summary": "Get download progress",
        "operationId": "getDownloadProgress",
        "parameters": [{ "name": "downloadId", "in": "path", "required": true, "schema": { "type": "string" } }],
        "responses": { "200": { "description": "Download progress with status, page counts" }, "404": { "description": "Download not found" } }
      }
    },
    "/api/v1/downloads/zip": {
      "get": {
        "tags": ["Downloads"],
        "summary": "Export downloaded chapter as ZIP",
        "operationId": "downloadZip",
        "parameters": [
          { "name": "sourceId", "in": "query", "required": true, "schema": { "type": "integer" } },
          { "name": "manga", "in": "query", "required": true, "schema": { "type": "string" } },
          { "name": "chapter", "in": "query", "required": true, "schema": { "type": "string" } }
        ],
        "responses": { "200": { "description": "ZIP archive", "content": { "application/zip": {} } }, "404": { "description": "Chapter not downloaded" } }
      }
    },
    "/api/v1/downloads/pdf": {
      "get": {
        "tags": ["Downloads"],
        "summary": "Export downloaded chapter as PDF",
        "operationId": "downloadPdf",
        "description": "Generates PDF from downloaded pages. WebP images are converted to JPEG 85% quality for optimal size/quality ratio.",
        "parameters": [
          { "name": "sourceId", "in": "query", "required": true, "schema": { "type": "integer" } },
          { "name": "manga", "in": "query", "required": true, "schema": { "type": "string" } },
          { "name": "chapter", "in": "query", "required": true, "schema": { "type": "string" } }
        ],
        "responses": { "200": { "description": "PDF document", "content": { "application/pdf": {} } }, "404": { "description": "Chapter not downloaded or generation failed" } }
      }
    },
    "/metrics": {
      "get": {
        "tags": ["Monitoring"],
        "summary": "Prometheus metrics endpoint",
        "operationId": "getMetrics",
        "description": "Returns all server metrics in Prometheus text format. Includes: HTTP request rates/latency, JVM memory/GC/threads, custom Miku metrics (cache hits, extension counts, source errors).",
        "responses": {
          "200": { "description": "Prometheus metrics in text format", "content": { "text/plain": {} } }
        }
      }
    },
    "/api/v1/ws": {
      "get": {
        "tags": ["Monitoring"],
        "summary": "WebSocket event stream",
        "operationId": "websocketEvents",
        "description": "Real-time server events via WebSocket (upgrade connection). Replaces polling with push notifications.\n\nEvents:\n- `download.started` / `download.progress` / `download.completed` / `download.failed`\n- `extension.installing` (steps: downloading → converting → loading → completed)\n- `extension.installed` / `extension.failed` / `extension.uninstalled`\n- `server.info`\n\nConnect: `ws://localhost:8080/api/v1/ws`",
        "responses": {
          "101": { "description": "WebSocket upgrade" }
        }
      }
    }
  }
}
""".trimIndent()
