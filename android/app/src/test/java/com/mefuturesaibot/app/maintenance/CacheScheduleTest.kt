package com.mefuturesaibot.app.maintenance

import android.app.Application
import android.app.job.JobScheduler
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CacheScheduleTest {
    @Test fun startupRegistersOnePersistedBatteryAwareJob() {
        val app: Application = RuntimeEnvironment.getApplication()
        val scheduler = requireNotNull(app.getSystemService(JobScheduler::class.java))
        assertNotNull(scheduler.getPendingJob(CacheCleanupJob.JOB_ID))
        CacheCleanupJob.schedule(app)
        val job = scheduler.allPendingJobs.single { it.id == CacheCleanupJob.JOB_ID }
        assertEquals(CacheCleanupJob::class.java.name, job.service.className)
        assertEquals(TimeUnit.DAYS.toMillis(1), job.intervalMillis)
        assertTrue(job.isPersisted)
        assertTrue(job.isRequireBatteryNotLow)
    }
}
