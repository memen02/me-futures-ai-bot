package com.mefuturesaibot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.ExchangeClock
import com.mefuturesaibot.app.exchange.CandleHistorySync
import com.mefuturesaibot.app.exchange.BookQuote
import android.os.IBinder
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket

/**
 * Foreground Service -- memegang ReconnectingWebSocket supaya koneksi bertahan
 * walau Activity ditutup/layar mati.
 *
 * PERBAIKAN (audit 2026-11-10):
 *  #1 -- foregroundServiceType diganti dataSync -> specialUse (lihat AndroidManifest.xml
 *        untuk alasan lengkap: dataSync kena batas keras 6 jam/24 jam di Android 15+).
 *  #3 -- sekarang punya ACTION_STOP yang benar-benar menghentikan service dengan
 *        bersih (dipanggil dari tombol Stop MainActivity), bukan auto-start tanpa
 *        kontrol seperti sebelumnya.
 *  (minor) -- notifikasi sekarang punya setContentIntent, ketuk notifikasi membuka app.
 *
 * PENTING -- masih berlaku dari increment sebelumnya: CI hijau TIDAK CUKUP untuk
 * file ini. Harus dites di device nyata: start, minimize, tunggu, stop lewat tombol,
 * pastikan notifikasi benar-benar hilang setelah Stop (bukan cuma UI Activity yang
 * bilang berhenti padahal Service masih jalan di background).
 */
class BotForegroundService : Service() {

    private var socket: ReconnectingWebSocket? = null

    private val candleSync by lazy { CandleHistorySync(clock = exchangeClock) { latestHistory = it } }
    private var reconnecting = false
    private var demo: com.mefuturesaibot.app.demo.DemoRunner? = null
    private val modeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "venue" && demo != null) {
            demo?.close()
            demo = com.mefuturesaibot.app.demo.DemoRunner(this).also { it.start() }
        }
    }
    private val drain = android.os.Handler(android.os.Looper.getMainLooper())
    private var draining = false


    override fun onCreate() {
        super.onCreate()
        com.mefuturesaibot.app.demo.FuturesVenue.settings(this).registerOnSharedPreferenceChangeListener(modeListener)
        exchangeClock = ExchangeClock()
        latestHistory = CandleHistory()
        latestQuote = null
        latestStatus = ReconnectingWebSocket.ConnectionStatus.CONNECTING
        isRunning = true
        ensureNotificationChannel()
        startForegroundCompat(buildNotification(ReconnectingWebSocket.ConnectionStatus.CONNECTING))
        if (BotPreferences.shouldRun(this) || demoPrefs().contains("intent")) {
            connectSocket()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BotPreferences.setShouldRun(this, false)
            stopEverything()
            return if (demoPrefs().contains("intent")) START_STICKY else START_NOT_STICKY
        }

        if (!BotPreferences.shouldRun(this)) {
            stopEverything()
            return if (demoPrefs().contains("intent")) START_STICKY else START_NOT_STICKY
        }

        if (socket == null) {
            connectSocket()
        }

        // START_STICKY: Android boleh mencoba membuat ulang Service
        // jika proses dihentikan OS, selama pengguna belum menekan Stop.
        return START_STICKY
    }

    override fun onDestroy() {
        com.mefuturesaibot.app.demo.FuturesVenue.settings(this).unregisterOnSharedPreferenceChangeListener(modeListener)
        drain.removeCallbacksAndMessages(null)
        demo?.close()
        demo = null
        isRunning = false
        candleSync.stop()
        socket?.close()
        socket = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null // tidak dipakai, MainActivity poll companion object

    private fun demoPrefs() = com.mefuturesaibot.app.demo.FuturesVenue.selected(this).prefs(this)

    private fun stopEverything() {
        BotPreferences.setShouldRun(this, false)
        val pending = synchronized(com.mefuturesaibot.app.demo.DemoRunner.entryLock) {
            demoPrefs().edit().putBoolean("enabled", false).apply()
            demoPrefs().contains("intent")
        }
        if (pending) {
            if (demo == null) demo = com.mefuturesaibot.app.demo.DemoRunner(this).also { it.start() }
            if (!draining) {
                draining = true
                drain.postDelayed({ draining = false; if (!BotPreferences.shouldRun(this)) stopEverything() }, 5000)
            }
            return
        }
        candleSync.stop()
        socket?.close()
        socket = null
        isRunning = false
        stopForegroundCompat()
        stopSelf()
    }

    private fun connectSocket() {
        if (demo == null) demo = com.mefuturesaibot.app.demo.DemoRunner(this).also { it.start() }
        startForegroundService(Intent(this, ScreeningService::class.java))
        candleSync.start()
        socket = ReconnectingWebSocket(
            url = ReconnectingWebSocket.BTC_BOOK_TICKER_URL,
            onStatusChange = { status ->
                if (status != ReconnectingWebSocket.ConnectionStatus.CONNECTED) {
                    latestQuote = latestQuote?.copy(disconnected = true)
                }
                if (status == ReconnectingWebSocket.ConnectionStatus.DISCONNECTED) reconnecting = true
                if (reconnecting && status == ReconnectingWebSocket.ConnectionStatus.CONNECTED) {
                    reconnecting = false
                    candleSync.refresh()
                }
                latestStatus = status
                updateNotification(status)
            },
            onMessage = { message ->
                BookQuote.parse(message, SystemClock.elapsedRealtime())?.let { quote ->
                    if (quote.eventTimeMs >= (latestQuote?.eventTimeMs ?: 0L)) {
                        exchangeClock.observe(quote.eventTimeMs, quote.receivedElapsedMs)
                        latestQuote = quote
                    }
                }
            },
        ).also { it.connect() }
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_bot_status),
            NotificationManager.IMPORTANCE_LOW, // LOW: tidak bersuara/mengganggu, tetap persisten terlihat
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: ReconnectingWebSocket.ConnectionStatus): Notification {
        val text = when (status) {
            ReconnectingWebSocket.ConnectionStatus.CONNECTING -> "Menyambungkan..."
            ReconnectingWebSocket.ConnectionStatus.CONNECTED -> "Tersambung"
            ReconnectingWebSocket.ConnectionStatus.DISCONNECTED -> "Terputus, mencoba lagi..."
            ReconnectingWebSocket.ConnectionStatus.RECONNECTING -> "Menyambung ulang..."
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: ReconnectingWebSocket.ConnectionStatus) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // API 26-28: overload dengan parameter tipe belum ada.
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "bot_status"
        private const val NOTIFICATION_ID = 1

        /** Dikirim lewat Intent.setAction() dari tombol Stop MainActivity. */
        const val ACTION_STOP = "com.mefuturesaibot.app.action.STOP_SERVICE"

        /** Dibaca MainActivity lewat polling sederhana. @Volatile supaya pembaruan
         * dari thread Service langsung terlihat thread UI tanpa sinkronisasi tambahan. */
        @Volatile var isRunning: Boolean = false
            private set

        @Volatile var latestStatus: ReconnectingWebSocket.ConnectionStatus = ReconnectingWebSocket.ConnectionStatus.CONNECTING
            private set

        var exchangeClock = ExchangeClock()
            private set

        @Volatile var latestHistory = CandleHistory()
            private set



        @Volatile var latestQuote: BookQuote? = null
            private set
    }
}
