package com.mefuturesaibot.app.ai.resource

import android.content.Context
import java.io.Closeable

internal data class ResourceReport(val budget: ResourceBudget, val deletedFiles: Int)

// Optional maintenance coordinator. No trading state is owned or modified here.
internal class SmartResourceCoordinator(context: Context,
    private val onReport: (ResourceReport) -> Unit,
    onFailure: (Exception) -> Unit) : Closeable {
    private val app = context.applicationContext
    private val cleaner = SmartStorageCleaner(app)
    private val worker = SmartBackgroundWorker({
        val budget = SmartRamManager.sample(app)
        val deleted = cleaner.clean(System.currentTimeMillis())
        onReport(ResourceReport(budget,deleted))
    },onFailure)
    fun start(): Boolean = worker.start()
    override fun close() = worker.close()
}
