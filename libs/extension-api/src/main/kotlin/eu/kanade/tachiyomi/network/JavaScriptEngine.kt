package eu.kanade.tachiyomi.network

import org.mozilla.javascript.Context as RhinoContext

/**
 * JavaScript execution engine for extensions.
 * Some extensions need JS eval to decode obfuscated URLs or execute dynamic loaders.
 * Uses Mozilla Rhino — pure Java, no native dependencies.
 */
class JavaScriptEngine(context: Any? = null) {

    fun <T> evaluate(script: String): T {
        val cx = RhinoContext.enter()
        return try {
            cx.optimizationLevel = -1 // Interpreted mode (works without class generation)
            val scope = cx.initStandardObjects()
            @Suppress("UNCHECKED_CAST")
            val result = cx.evaluateString(scope, script, "eval", 1, null)
            when (result) {
                is Double -> {
                    if (result == result.toLong().toDouble()) result.toLong() as T
                    else result as T
                }
                else -> RhinoContext.toString(result) as T
            }
        } finally {
            RhinoContext.exit()
        }
    }
}
