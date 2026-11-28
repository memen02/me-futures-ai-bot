package com.mefuturesaibot.app.auth

import android.os.Bundle
import com.mefuturesaibot.app.MainActivity
import com.mefuturesaibot.app.StartupActivity
import com.mefuturesaibot.app.maintenance.MeProjectApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class LockedDeviceApplication : MeProjectApplication() {
    internal override val devicePermission: DevicePermission = object : DevicePermission {
        override fun allowed() = false
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = LockedDeviceApplication::class)
class AccessNavigationTest {
    @Test fun directDashboardAndRestoredStartupReturnToDeviceGate() {
        val home = Robolectric.buildActivity(MainActivity::class.java).create(Bundle())
        assertTrue(home.get().isFinishing)
        assertEquals(DeviceAccessActivity::class.java.name, shadowOf(home.get()).nextStartedActivity.component!!.className)
        home.destroy()
        val startup = Robolectric.buildActivity(StartupActivity::class.java).create()
        assertTrue(startup.get().isFinishing)
        assertEquals(DeviceAccessActivity::class.java.name, shadowOf(startup.get()).nextStartedActivity.component!!.className)
        startup.destroy()
    }
}
