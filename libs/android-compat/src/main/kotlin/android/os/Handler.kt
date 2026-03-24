package android.os

import java.util.concurrent.Executors

open class Handler(private val looper: Looper = Looper.getMainLooper()) {
    private val executor = Executors.newSingleThreadExecutor()

    fun post(r: Runnable): Boolean {
        executor.execute(r)
        return true
    }

    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        executor.execute {
            Thread.sleep(delayMillis)
            r.run()
        }
        return true
    }

    fun removeCallbacks(r: Runnable) {}
    fun removeCallbacksAndMessages(token: Any?) {}
}
