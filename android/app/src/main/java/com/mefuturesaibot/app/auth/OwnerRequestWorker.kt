package com.mefuturesaibot.app.auth

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class OwnerRequestWorker : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Synchronized fun <T> submit(action: () -> T, complete: (Result<T>) -> Unit): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) return false
        try {
            executor.execute {
                if (closed.get()) return@execute
                val result = runCatching { action() }
                main.post {
                    busy.set(false)
                    if (!closed.get()) complete(result)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            busy.set(false)
            return false
        }
        return true
    }

    @Synchronized override fun close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow()
            main.removeCallbacksAndMessages(null)
        }
    }
}
