package com.mefuturesaibot.app

import android.Manifest
import android.app.Activity
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

class MainActivity : Activity() {
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
        BotPreferences.setShouldRun(this, false)

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
        desk.update(ScreeningService.latest, active)
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
