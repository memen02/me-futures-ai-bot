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
    @Test fun unapprovedDemoEntryDoesNotContactExchange() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val venue = com.mefuturesaibot.app.demo.FuturesVenue.DEMO
        venue.prefs(context).edit().putBoolean("enabled", true).commit()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { error("Unexpected exchange request") }.build()
        val api = com.mefuturesaibot.app.demo.DemoExchange(
            com.mefuturesaibot.app.account.BinanceCredentials("fixture", "test"), http, venue)
        assertTrue(com.mefuturesaibot.app.demo.DemoRunner(context, venue).cycle(api).contains("izin pemilik"))
        assertFalse(venue.prefs(context).contains("intent"))
    }

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
