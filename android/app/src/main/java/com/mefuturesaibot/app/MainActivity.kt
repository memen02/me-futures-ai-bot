package com.mefuturesaibot.app

import android.Manifest
import android.app.Activity
import com.mefuturesaibot.app.security.SecureActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import com.mefuturesaibot.app.scanner.DeskTab
import com.mefuturesaibot.app.scanner.Scan
import com.mefuturesaibot.app.scanner.TradingDesk

class MainActivity : SecureActivity() {
    private lateinit var desk: TradingDesk
    private val pollHandler = Handler(Looper.getMainLooper())
    private var userRequestedStop = false
    private var visible = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshStatusText()
            pollHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trading_desk)
        setupSecuritySettings()
        findViewById<Button>(R.id.scan_stop).setOnClickListener { onStopClicked() }
        desk = TradingDesk(this, DeskTab.restore(savedInstanceState), ::onStartClicked)
        desk.onTabSelected = ::refreshStatusText
        userRequestedStop = savedInstanceState?.getBoolean("userRequestedStop") ?: false
        ensureNotificationPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("userRequestedStop", userRequestedStop)
        outState.putString(DeskTab.STATE_KEY, desk.tab.name)
    }

    override fun onStart() {
        super.onStart()
        startForegroundService(Intent(this, ScreeningService::class.java))
        visible = true
        pollRunnable.run()
    }

    override fun onStop() {
        visible = false
        pollHandler.removeCallbacks(pollRunnable)
        desk.releaseDisplayData()
        super.onStop()
    }

    @Deprecated("Native activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TokenDetailActivity.OPEN && resultCode == RESULT_OK) desk.openMarkets()
    }

    override fun onDestroy() {
        desk.close()
        super.onDestroy()
    }

    private fun setupSecuritySettings() {
        val biometric = findViewById<Button>(R.id.settings_biometric)
        val autoLock = findViewById<Button>(R.id.settings_autolock)

        biometric.isEnabled = true
        autoLock.isEnabled = true

        biometric.setOnClickListener {
            configureAppLock(true)
        }

        autoLock.setOnClickListener {
            configureAppLock(
                !com.mefuturesaibot.app.security.AppLock.isEnabled(this)
            )
        }

        refreshSecuritySettings()
    }

    private fun refreshSecuritySettings() {
        val enabled =
            com.mefuturesaibot.app.security.AppLock.isEnabled(this)

        findViewById<Button>(R.id.settings_autolock).text =
            if (enabled) "Penguncian otomatis: AKTIF"
            else "Penguncian otomatis: NONAKTIF"

        findViewById<android.widget.TextView>(
            R.id.user_access_status
        ).text = if (enabled) {
            "App Lock aktif · Lisensi online belum tersedia"
        } else {
            "App Lock nonaktif · Lisensi online belum tersedia"
        }
    }

    override fun onSecuritySettingsChanged() {
        super.onSecuritySettingsChanged()
        refreshSecuritySettings()
    }

    private fun onStartClicked() {
        // Catatan: permintaan izin notifikasi sudah dipindah ke onCreate() (lihat
        // komentar di sana) -- tidak diulang di sini supaya tidak memunculkan
        // dialog dua kali kalau pengguna menekan Start berulang.
        userRequestedStop = false
        BotPreferences.setShouldRun(this, true)
        startForegroundService(Intent(this, BotForegroundService::class.java))
    }

    private fun onStopClicked() {
        userRequestedStop = true
        synchronized(com.mefuturesaibot.app.demo.DemoRunner.entryLock) {
            BotPreferences.setShouldRun(this, false)
            com.mefuturesaibot.app.demo.FuturesVenue.selected(this).prefs(this).edit().putBoolean("enabled",false).apply()
        }

        if (BotForegroundService.isRunning) {
            startService(
                Intent(this, BotForegroundService::class.java)
                    .setAction(BotForegroundService.ACTION_STOP)
            )
        }

        refreshStatusText()
    }

    private fun refreshStatusText() {
        val active = !userRequestedStop && BotForegroundService.isRunning
        desk.update(ScreeningService.displayLatest, active)
        if (visible) desk.refreshSignals()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATIONS)
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }
}
