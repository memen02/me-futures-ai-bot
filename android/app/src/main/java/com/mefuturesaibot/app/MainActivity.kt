package com.mefuturesaibot.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket

/**
 * FASE 10 -- Increment 3. MainActivity sekarang MURNI UI: tidak lagi memegang
 * ReconnectingWebSocket sendiri (itu sudah pindah ke BotForegroundService).
 * Status dibaca lewat polling sederhana ke companion object Service -- lihat
 * catatan desain di BotForegroundService.kt soal kenapa bukan Binder/LiveData.
 */
class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var dataView: TextView
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 500L

    private val pollRunnable = object : Runnable {
        override fun run() {
            statusView.text = describeStatus(BotForegroundService.latestStatus)
            dataView.text = BotForegroundService.latestMessage
            pollHandler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleView = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(48, 160, 48, 24)
        }

        val captionView = TextView(this).apply {
            text = "Foreground Service aktif -- coba minimize app ini, status tetap jalan"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(48, 0, 48, 24)
        }

        statusView = TextView(this).apply {
            text = "Memulai service..."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#475569"))
            setPadding(48, 0, 48, 16)
        }

        dataView = TextView(this).apply {
            text = ""
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0EA5E9"))
            setPadding(48, 0, 48, 48)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(titleView)
            addView(captionView)
            addView(statusView)
            addView(dataView)
        }

        setContentView(root)

        ensureNotificationPermission()
        startForegroundService(Intent(this, BotForegroundService::class.java))
    }

    override fun onStart() {
        super.onStart()
        pollHandler.post(pollRunnable)
    }

    override fun onStop() {
        super.onStop()
        pollHandler.removeCallbacks(pollRunnable)
        // SENGAJA TIDAK stopService() di sini -- justru itu intinya Foreground
        // Service: tetap jalan walau Activity/MainActivity ditutup.
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATIONS)
            }
        }
    }

    private fun describeStatus(status: ReconnectingWebSocket.ConnectionStatus): String = when (status) {
        ReconnectingWebSocket.ConnectionStatus.CONNECTING -> "Menyambungkan..."
        ReconnectingWebSocket.ConnectionStatus.CONNECTED -> "Tersambung \u2713 (via Foreground Service)"
        ReconnectingWebSocket.ConnectionStatus.DISCONNECTED -> "Terputus, mencoba lagi..."
        ReconnectingWebSocket.ConnectionStatus.RECONNECTING -> "Menyambung ulang..."
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }
}
