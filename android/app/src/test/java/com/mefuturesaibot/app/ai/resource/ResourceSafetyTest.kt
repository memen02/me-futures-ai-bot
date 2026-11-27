package com.mefuturesaibot.app.ai.resource

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ResourceSafetyTest {
    @Test fun budgetsFailConservatively() {
        val mib = 1024L*1024
        assertEquals(ResourceBudget(1,false),SmartRamManager.budget(100*mib,100*mib,false))
        assertEquals(ResourceBudget(1,false),SmartRamManager.budget(1000*mib,100*mib,true))
        assertEquals(ResourceBudget(2,false),SmartRamManager.budget(200*mib,100*mib,false))
        assertEquals(ResourceBudget(8,true),SmartRamManager.budget(500*mib,100*mib,false))
    }

    @Test fun cleanerOnlyDeletesExpiredOwnedFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir,"ai_disposable_research_v1").apply { mkdirs() }
        val old = File(root,"a".repeat(64)+".cache").apply { writeText("old") }
        val fresh = File(root,"b".repeat(64)+".cache").apply { writeText("fresh") }
        val protected = File(root,"journal.db").apply { writeText("keep") }
        val outside = File(context.cacheDir,"c".repeat(64)+".cache").apply { writeText("keep") }
        val now = System.currentTimeMillis()
        try {
            assertTrue(old.setLastModified(now-90000000L))
            assertTrue(fresh.setLastModified(now))
            assertTrue(protected.setLastModified(now-90000000L))
            assertTrue(outside.setLastModified(now-90000000L))
            assertEquals(1,SmartStorageCleaner(context).clean(now))
            assertFalse(old.exists())
            assertTrue(fresh.exists() && protected.exists() && outside.exists())
        } finally { old.delete(); fresh.delete(); protected.delete(); outside.delete(); root.delete() }
    }

    @Test fun workerCannotStartTwiceOrAfterClose() {
        val worker = SmartBackgroundWorker({}, {})
        try {
            assertTrue(worker.start())
            assertFalse(worker.start())
        } finally { worker.close() }
        assertFalse(worker.start())
    }
}
