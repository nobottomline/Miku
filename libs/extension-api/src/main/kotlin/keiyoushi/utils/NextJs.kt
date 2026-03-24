package keiyoushi.utils

import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.reflect.typeOf

private val NEXT_F_REGEX = Regex("""self\.__next_f\.push\(\s*(\[.*])\s*\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)

private fun <T> extractValueNextJs(
    payload: JsonElement,
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    if (payload !is JsonObject && payload !is JsonArray) return null
    if (predicate(payload)) {
        return jsonInstance.decodeFromJsonElement(deserializer, payload)
    }
    val children: Iterable<JsonElement> = when (payload) {
        is JsonObject -> payload.values
        is JsonArray -> payload
        else -> return null
    }
    for (child in children) {
        val result = extractValueNextJs(child, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

private fun Document.extractAppRouterPayloads(): List<JsonElement> = select("script:not([src])")
    .map { it.data() }
    .filter { "self.__next_f.push" in it }
    .flatMap { script ->
        try {
            val raw = NEXT_F_REGEX.find(script)?.groupValues?.get(1) ?: return@flatMap emptyList()
            val arr = jsonInstance.parseToJsonElement(raw).jsonArray
            val content = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()
            extractRscPayloads(content)
        } catch (_: Exception) {
            emptyList()
        }
    }

private fun Document.extractPagesRouterPayloads(): List<JsonElement> {
    val data = selectFirst("script#__NEXT_DATA__")?.data() ?: return emptyList()
    return try {
        val root = jsonInstance.parseToJsonElement(data)
        val pageProps = root.jsonObject["props"]?.jsonObject?.get("pageProps")
        listOfNotNull(pageProps, root)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun extractRscPayloads(body: String): List<JsonElement> {
    val results = mutableListOf<JsonElement>()
    var pos = 0

    while (pos < body.length) {
        val colonIdx = body.indexOf(':', pos)
        if (colonIdx == -1) break

        val id = body.substring(pos, colonIdx)
        if (id.isEmpty() || !id.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            pos++
            continue
        }

        pos = colonIdx + 1
        if (pos >= body.length) break

        if (body[pos] == 'T') {
            pos++
            val commaIdx = body.indexOf(',', pos)
            if (commaIdx == -1) break
            val byteLen = body.substring(pos, commaIdx).toIntOrNull(16) ?: break
            pos = commaIdx + 1
            var bytes = 0
            val start = pos
            while (pos < body.length && bytes < byteLen) {
                when {
                    body[pos].code < 0x80 -> bytes += 1
                    body[pos].code < 0x800 -> bytes += 2
                    Character.isHighSurrogate(body[pos]) -> {
                        bytes += 4
                        pos++
                    }
                    else -> bytes += 3
                }
                pos++
            }
            try {
                results.add(jsonInstance.parseToJsonElement(body.substring(start, pos)))
            } catch (_: Exception) {}
        } else {
            val (element, end) = parseJsonAt(body, pos)
            if (element != null) results.add(element)
            pos = end
        }
    }

    return results
}

private fun parseJsonAt(body: String, start: Int): Pair<JsonElement?, Int> {
    if (start >= body.length) return Pair(null, start)

    var depth = 0
    var inString = false
    var escape = false
    var i = start

    while (i < body.length) {
        val c = body[i++]
        if (escape) { escape = false; continue }
        if (c == '\\' && inString) { escape = true; continue }
        if (c == '"') { inString = !inString; continue }
        if (inString) continue
        when (c) {
            '{', '[' -> depth++
            '}', ']' -> if (--depth == 0) {
                return try {
                    Pair(jsonInstance.parseToJsonElement(body.substring(start, i)), i)
                } catch (_: Exception) {
                    Pair(null, i)
                }
            }
        }
        if (depth == 0 && c.isWhitespace()) {
            return try {
                Pair(jsonInstance.parseToJsonElement(body.substring(start, i - 1)), i)
            } catch (_: Exception) {
                Pair(null, i)
            }
        }
    }
    return Pair(null, i)
}

@PublishedApi
internal inline fun <reified T> inferredNextJsPredicate(): (JsonElement) -> Boolean {
    val kType = typeOf<T>()
    val isList = kType.classifier == List::class

    val elementDescriptor = if (isList) {
        serializer<T>().descriptor.getElementDescriptor(0)
    } else {
        serializer<T>().descriptor
    }

    val requiredKeys = (0 until elementDescriptor.elementsCount)
        .filterNot { elementDescriptor.isElementOptional(it) || elementDescriptor.getElementDescriptor(it).isNullable }
        .map { elementDescriptor.getElementName(it) }
        .toSet()

    require(requiredKeys.isNotEmpty()) {
        "Cannot infer a predicate for ${elementDescriptor.serialName}: all fields are optional or nullable."
    }

    return if (isList) {
        { element ->
            element is JsonArray &&
                element.isNotEmpty() &&
                element.first() is JsonObject &&
                requiredKeys.all { it in element.first().jsonObject }
        }
    } else {
        { element ->
            element is JsonObject && requiredKeys.all { it in element }
        }
    }
}

// ---- Document ----

fun <T> Document.extractNextJs(
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    val payloads = extractAppRouterPayloads().ifEmpty { extractPagesRouterPayloads() }
    for (payload in payloads) {
        val result = extractValueNextJs(payload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

inline fun <reified T> Document.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())

inline fun <reified T> Document.extractNextJs(): T? = extractNextJs(inferredNextJsPredicate<T>(), serializer<T>())

// ---- String (RSC) ----

fun <T> String.extractNextJsRsc(
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    for (payload in extractRscPayloads(this)) {
        val result = extractValueNextJs(payload, predicate, deserializer)
        if (result != null) return result
    }
    return null
}

inline fun <reified T> String.extractNextJsRsc(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJsRsc(predicate, serializer<T>())

inline fun <reified T> String.extractNextJsRsc(): T? = extractNextJsRsc(inferredNextJsPredicate<T>(), serializer<T>())

// ---- OkHttp Response ----

fun <T> Response.extractNextJs(
    predicate: (JsonElement) -> Boolean,
    deserializer: DeserializationStrategy<T>,
): T? {
    val contentType = header("Content-Type") ?: ""
    return when {
        "text/x-component" in contentType -> body.string().extractNextJsRsc(predicate, deserializer)
        "text/html" in contentType -> asJsoup().extractNextJs(predicate, deserializer)
        else -> error("Unsupported Content-Type for Next.js extraction: $contentType")
    }
}

inline fun <reified T> Response.extractNextJs(
    noinline predicate: (JsonElement) -> Boolean,
): T? = extractNextJs(predicate, serializer<T>())

inline fun <reified T> Response.extractNextJs(): T? = extractNextJs(inferredNextJsPredicate<T>(), serializer<T>())
