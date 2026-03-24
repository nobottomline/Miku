package android.util

import org.slf4j.LoggerFactory

object Log {
    private val logger = LoggerFactory.getLogger("AndroidCompat")

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    @JvmStatic
    fun v(tag: String, msg: String): Int { logger.trace("[$tag] $msg"); return 0 }
    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable): Int { logger.trace("[$tag] $msg", tr); return 0 }
    @JvmStatic
    fun d(tag: String, msg: String): Int { logger.debug("[$tag] $msg"); return 0 }
    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable): Int { logger.debug("[$tag] $msg", tr); return 0 }
    @JvmStatic
    fun i(tag: String, msg: String): Int { logger.info("[$tag] $msg"); return 0 }
    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable): Int { logger.info("[$tag] $msg", tr); return 0 }
    @JvmStatic
    fun w(tag: String, msg: String): Int { logger.warn("[$tag] $msg"); return 0 }
    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable): Int { logger.warn("[$tag] $msg", tr); return 0 }
    @JvmStatic
    fun e(tag: String, msg: String): Int { logger.error("[$tag] $msg"); return 0 }
    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable): Int { logger.error("[$tag] $msg", tr); return 0 }
}
