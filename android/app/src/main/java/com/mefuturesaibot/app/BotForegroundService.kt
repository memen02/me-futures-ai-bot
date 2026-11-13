package com.mefuturesaibot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ensureNotificationChannel()
        startForegroundCompat(buildNotification(ReconnectingWebSocket.ConnectionStatus.CONNECTING))
        connectSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        // START_STICKY: kalau OS membunuh proses karena tekanan RAM (BUKAN karena
        // pengguna menekan Stop), Android akan coba buat ulang Service ini.
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        socket?.close()
        socket = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null // tidak dipakai, MainActivity poll companion object

    private fun stopEverything() {
        socket?.close()
        socket = null
        isRunning = false
        stopForegroundCompat()
        stopSelf()
    }

    private fun connectSocket() {
        socket = ReconnectingWebSocket(
            url = "wss://fstream.binance.com/public/stream?streams=btcusdt@bookTicker",
            onStatusChange = { status ->
                latestStatus = status
                updateNotification(status)
            },
            onMessage = { text -> latestMessage = text.take(140) },
        ).also { it.connect() }
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Status ME Futures AI Bot",
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
            .setContentTitle("ME Futures AI Bot")
            .setContentText(text)
            // Placeholder: ikon stok platform, BUKAN ic_launcher_foreground (ikon
            // berwarna penuh biasanya tampil aneh di status bar). Sengaja TIDAK
            // dibuatkan ikon kustom dulu -- menunggu Anda, lihat TODO.md.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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

        @Volatile var latestMessage: String = ""
            private set
    }
}
