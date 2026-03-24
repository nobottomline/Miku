package keiyoushi.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

val jsonInstance: Json by injectLazy()

inline fun <reified T> String.parseAs(json: Json = jsonInstance): T = json.decodeFromString(this)

inline fun <reified T> String.parseAs(json: Json = jsonInstance, transform: (String) -> String): T = transform(this).parseAs(json)

inline fun <reified T> Response.parseAs(json: Json = jsonInstance): T = use { json.decodeFromStream(body.byteStream()) }

inline fun <reified T> Response.parseAs(json: Json = jsonInstance, transform: (String) -> String): T = use { body.string().parseAs(json, transform) }

inline fun <reified T> JsonElement.parseAs(json: Json = jsonInstance): T = json.decodeFromJsonElement(this)

inline fun <reified T> T.toJsonString(json: Json = jsonInstance): String = json.encodeToString(this)
