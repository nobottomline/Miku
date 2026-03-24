package app.cash.quickjs

/**
 * QuickJs compatibility stub — redirects to Rhino engine.
 * Extensions import app.cash.quickjs.QuickJs for JS evaluation.
 */
class QuickJs private constructor() : AutoCloseable {
    private val engine = eu.kanade.tachiyomi.network.JavaScriptEngine()

    fun evaluate(script: String): Any? = engine.evaluate<Any>(script)

    override fun close() {}

    companion object {
        @JvmStatic
        fun create(): QuickJs = QuickJs()
    }
}
