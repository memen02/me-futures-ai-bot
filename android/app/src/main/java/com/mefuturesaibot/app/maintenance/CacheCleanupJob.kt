package com.mefuturesaibot.app.maintenance

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class MeProjectApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CacheCleanupJob.schedule(this)
    }
}

class CacheCleanupJob : JobService() {
    private val main = Handler(Looper.getMainLooper())
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val task = Thread({
            val thread = Thread.currentThread()
            val result = try {
                CacheCleaner.clean(cacheDir.toPath(), System.currentTimeMillis()) { thread.isInterrupted }
            } catch (_: IOException) {
                CleanupResult(0, 0, 1)
            } catch (_: SecurityException) {
                CleanupResult(0, 0, 1)
            }
            main.post {
                if (worker !== thread) return@post
                worker = null
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("last", System.currentTimeMillis())
                    .putLong("files", result.files).putLong("bytes", result.bytes)
                    .putLong("skipped", result.skipped).apply()
                jobFinished(params, false)
            }
        }, "me-project-cache")
        worker = task
        task.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        worker?.interrupt()
        worker = null
        return true
    }

    companion object {
        internal const val JOB_ID = 4912
        private const val PREFS = "cache_maintenance"

        internal fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CacheCleanupJob::class.java))
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }

        internal fun status(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
            val last = prefs.getLong("last", 0L)
            if (last == 0L) return "Cache berusia 7 hari dibersihkan otomatis saat kondisi baterai memadai."
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last))
            val size = Formatter.formatShortFileSize(context, prefs.getLong("bytes", 0L))
            val skipped = prefs.getLong("skipped", 0L)
            val detail = if (skipped == 0L) "" else " · $skipped dilewati"
            return "Pemeriksaan $date\n${prefs.getLong("files", 0L)} file dibersihkan · $size$detail"
        }
    }
}
