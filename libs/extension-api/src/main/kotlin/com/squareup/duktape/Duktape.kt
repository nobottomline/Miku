package com.squareup.duktape

/**
 * Duktape compatibility stub — redirects to Rhino engine.
 */
class Duktape private constructor() : AutoCloseable {
    private val engine = eu.kanade.tachiyomi.network.JavaScriptEngine()

    fun evaluate(script: String): Any? = engine.evaluate<Any>(script)

    override fun close() {}

    companion object {
        @JvmStatic
        fun create(): Duktape = Duktape()
    }
}
