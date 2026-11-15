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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket

/**
 * MURNI UI: tidak memegang ReconnectingWebSocket sendiri (ada di BotForegroundService).
 *
 * PERBAIKAN (audit 2026-11-10, temuan #3): dulu service AUTO-START tanpa kontrol
 * apa pun begitu app dibuka, dan tidak ada cara menghentikannya dari UI. Sekarang
 * ada tombol Start/Stop eksplisit -- service baru jalan kalau pengguna menekan Start.
 */
class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var dataView: TextView
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 500L
    // PERBAIKAN audit-mandiri: dulu status "Dihentikan" dideteksi dengan membandingkan
    // isi TextView (statusView.text != "Dihentikan") -- rapuh, karena getText()
    // mengembalikan CharSequence yang tidak selalu String secara internal, bisa gagal
    // cocok meski isinya sama. Sekarang pakai flag boolean biasa, tidak ambigu.
    private var userRequestedStop = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshStatusText()
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
            text = "Foreground Service -- tekan Start, lalu coba minimize app ini"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(48, 0, 48, 24)
        }

        statusView = TextView(this).apply {
            text = "Belum dimulai"
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
            setPadding(48, 0, 48, 24)
        }

        val startButton = Button(this).apply {
            text = "Start"
            setOnClickListener { onStartClicked() }
        }

        val stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener { onStopClicked() }
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(startButton)
            addView(stopButton)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            addView(titleView)
            addView(captionView)
            addView(statusView)
            addView(dataView)
            addView(buttonRow)
        }

        setContentView(root)

        // PERBAIKAN (audit 2026-11-13): dulu izin notifikasi diminta di dalam
        // onStartClicked(), TEPAT SEBELUM startForegroundService() -- padahal
        // requestPermissions() itu asinkron (dialog sistem, tidak menunggu jawaban),
        // jadi Service sempat jalan & memanggil startForeground() SEBELUM pengguna
        // sempat menjawab dialognya. Hasilnya: pertama kali pakai, notifikasi
        // kemungkinan tidak tampil (bukan crash, cuma membingungkan). Sekarang
        // diminta di sini (saat app dibuka), supaya ada jeda alami sebelum
        // pengguna sempat menekan Start.
        ensureNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        pollHandler.post(pollRunnable)
    }

    override fun onStop() {
        super.onStop()
        pollHandler.removeCallbacks(pollRunnable)
        // SENGAJA TIDAK stopService() di sini -- itu intinya Foreground Service:
        // tetap jalan walau Activity ditutup. Berhenti HANYA lewat tombol Stop.
    }

    private fun onStartClicked() {
        // Catatan: permintaan izin notifikasi sudah dipindah ke onCreate() (lihat
        // komentar di sana) -- tidak diulang di sini supaya tidak memunculkan
        // dialog dua kali kalau pengguna menekan Start berulang.
        userRequestedStop = false
        startForegroundService(Intent(this, BotForegroundService::class.java))
    }

    private fun onStopClicked() {
        // PERBAIKAN (audit 2026-11-13): dulu Stop ditekan saat Service belum pernah
        // dijalankan tetap mengirim Intent ke BotForegroundService -- Android akan
        // membuat instance Service baru dulu (onCreate() ikut terpanggil, yang
        // UNCONDITIONALLY connect ke WebSocket) baru SETELAH itu memproses ACTION_STOP.
        // Efeknya: Stop pada kondisi "Belum dimulai" sempat menyalakan koneksi
        // sesaat sebelum mematikannya lagi. Sekarang dicegah di sumbernya.
        if (!BotForegroundService.isRunning) return

        userRequestedStop = true
        startService(Intent(this, BotForegroundService::class.java).setAction(BotForegroundService.ACTION_STOP))
        statusView.text = "Dihentikan"
        dataView.text = ""
    }

    private fun refreshStatusText() {
        if (!BotForegroundService.isRunning) {
            statusView.text = if (userRequestedStop) "Dihentikan" else "Belum dimulai"
            return
        }
        statusView.text = describeStatus(BotForegroundService.latestStatus)
        dataView.text = BotForegroundService.latestMessage
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
