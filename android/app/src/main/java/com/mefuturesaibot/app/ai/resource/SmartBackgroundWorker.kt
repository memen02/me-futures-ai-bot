package com.mefuturesaibot.app.ai.resource

import java.io.Closeable
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

// Optional maintenance only; never schedule order protection on this worker.
internal class SmartBackgroundWorker(private val task: () -> Unit,
    private val onFailure: (Exception) -> Unit) : Closeable {
    private val executor = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
        setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
    }
    private var started = false
    private var closed = false
    @Synchronized fun start(intervalMs: Long = 60000L): Boolean {
        require(intervalMs in 10000L..3600000L)
        if (started || closed) return false
        executor.scheduleWithFixedDelay({
            try { task() } catch (e: Exception) { onFailure(e) }
        },intervalMs,intervalMs,TimeUnit.MILLISECONDS)
        started = true
        return true
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        executor.shutdownNow()
    }
}
