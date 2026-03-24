package android.net

import java.net.URI

class Uri private constructor(private val uriString: String) {
    val scheme: String? get() = try { URI(uriString).scheme } catch (_: Exception) { null }
    val host: String? get() = try { URI(uriString).host } catch (_: Exception) { null }
    val path: String? get() = try { URI(uriString).path } catch (_: Exception) { null }
    val query: String? get() = try { URI(uriString).query } catch (_: Exception) { null }
    val fragment: String? get() = try { URI(uriString).fragment } catch (_: Exception) { null }
    val port: Int get() = try { URI(uriString).port } catch (_: Exception) { -1 }

    fun getQueryParameter(key: String): String? {
        val q = query ?: return null
        return q.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it[0] == key }
            ?.getOrNull(1)
    }

    fun buildUpon(): Builder = Builder().apply {
        scheme(this@Uri.scheme)
        authority(this@Uri.host)
        path(this@Uri.path)
        query(this@Uri.query)
        fragment(this@Uri.fragment)
    }

    override fun toString(): String = uriString

    class Builder {
        private var scheme: String? = null
        private var authority: String? = null
        private var path: String? = null
        private var query: String? = null
        private var fragment: String? = null
        private val queryParams = mutableListOf<Pair<String, String>>()

        fun scheme(scheme: String?) = apply { this.scheme = scheme }
        fun authority(authority: String?) = apply { this.authority = authority }
        fun path(path: String?) = apply { this.path = path }
        fun query(query: String?) = apply { this.query = query }
        fun fragment(fragment: String?) = apply { this.fragment = fragment }
        fun appendPath(newSegment: String) = apply { path = (path ?: "") + "/" + newSegment }
        fun appendQueryParameter(key: String, value: String) = apply { queryParams.add(key to value) }
        fun clearQuery() = apply { query = null; queryParams.clear() }

        fun build(): Uri {
            val sb = StringBuilder()
            if (scheme != null) sb.append(scheme).append("://")
            if (authority != null) sb.append(authority)
            if (path != null) sb.append(path)
            val allQuery = buildString {
                if (query != null) append(query)
                if (queryParams.isNotEmpty()) {
                    if (isNotEmpty()) append("&")
                    append(queryParams.joinToString("&") { "${it.first}=${it.second}" })
                }
            }
            if (allQuery.isNotEmpty()) sb.append("?").append(allQuery)
            if (fragment != null) sb.append("#").append(fragment)
            return Uri(sb.toString())
        }

        override fun toString(): String = build().toString()
    }

    companion object {
        @JvmStatic fun parse(uriString: String): Uri = Uri(uriString)
        @JvmField val EMPTY = Uri("")
        fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
        fun decode(s: String): String = java.net.URLDecoder.decode(s, "UTF-8")
    }
}
