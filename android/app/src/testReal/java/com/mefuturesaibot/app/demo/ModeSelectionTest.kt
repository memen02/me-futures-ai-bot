package com.mefuturesaibot.app.demo

import android.content.Context
import android.view.View
import android.widget.TextView
import com.mefuturesaibot.app.MainActivity
import com.mefuturesaibot.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = com.mefuturesaibot.app.auth.ApprovedDeviceApplication::class, sdk = [35])
class ModeSelectionTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val demo = FuturesVenue.DEMO.prefs(context)
    private val live = FuturesVenue.REAL.prefs(context)

    @Test fun activeLegacyMainnetIsRecoveredOnTheOriginalVenue() {
        live.edit().putString("intent","pending").commit()
        assertEquals(FuturesVenue.REAL,FuturesVenue.selected(context))
        assertFalse(FuturesVenue.select(context,FuturesVenue.DEMO))
        live.edit().remove("intent").putBoolean("enabled",true).commit()
        assertFalse(FuturesVenue.select(context,FuturesVenue.DEMO))
        live.edit().putBoolean("enabled",false).commit()
        assertEquals(FuturesVenue.REAL,FuturesVenue.selected(context))
        assertTrue(FuturesVenue.select(context,FuturesVenue.DEMO))
    }

    @Test fun changingModeKeepsJournalsSeparateAndNeverEnablesMainnet() {
        assertEquals(FuturesVenue.DEMO,FuturesVenue.selected(context))
        demo.edit().putInt("n",7).putBoolean("enabled",true).commit()
        live.edit().putInt("n",2).putString("consentKey","old").commit()
        assertFalse(FuturesVenue.select(context,FuturesVenue.REAL))
        demo.edit().putBoolean("enabled",false).putString("intent","open").commit()
        assertFalse(FuturesVenue.select(context,FuturesVenue.REAL))
        demo.edit().remove("intent").commit()
        assertTrue(FuturesVenue.select(context,FuturesVenue.REAL))
        assertFalse(live.contains("consentKey"))
        assertFalse(live.getBoolean("enabled",false))
        assertEquals(7,demo.getInt("n",0))
        assertEquals(2,live.getInt("n",0))
    }

    @Test fun settingsUpdatesBannerCredentialsAndPersistsThroughRecreation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            var a = controller.get()
            a.findViewById<View>(R.id.nav_account).performClick()
            assertFalse(a.findViewById<View>(R.id.mainnet_credentials).isShown)
            assertTrue(a.findViewById<View>(R.id.demo_keys).isShown)
            a.findViewById<View>(R.id.mode_mainnet).performClick()
            assertEquals("MAINNET · Uang asli",a.findViewById<TextView>(R.id.active_venue).text.toString())
            assertTrue(a.findViewById<View>(R.id.mainnet_credentials).isShown)
            assertFalse(a.findViewById<View>(R.id.demo_keys).isShown)
            controller.recreate()
            a = controller.get()
            assertEquals("MAINNET · Uang asli",a.findViewById<TextView>(R.id.active_venue).text.toString())
            a.findViewById<View>(R.id.nav_account).performClick()
            a.findViewById<View>(R.id.mode_demo).performClick()
            assertEquals("DEMO · Saldo virtual",a.findViewById<TextView>(R.id.active_venue).text.toString())
            assertFalse(a.findViewById<View>(R.id.mainnet_credentials).isShown)
            assertFalse(live.getBoolean("enabled",false))
        } finally { controller.pause().stop().destroy() }
    }
}
