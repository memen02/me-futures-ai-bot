package com.mefuturesaibot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket

/**
 * FASE 10 -- Increment 3: Foreground Service. Memindahkan kepemilikan
 * ReconnectingWebSocket dari MainActivity ke sini, supaya koneksi bertahan
 * walau Activity ditutup/layar mati -- ini yang membuat "auto reconnect"
 * benar-benar berarti "jalan di background", bukan cuma "jalan selama app
 * dibuka" seperti Increment 2.
 *
 * PENTING -- berbeda dari increment sebelumnya: CI hijau (compile+unit test)
 * TIDAK CUKUP untuk Service ini. Foreground service di Android modern (target
 * SDK 36) py aturan ketat yang HANYA gagal saat runtime, bukan saat compile:
 * tipe service wajib cocok antara manifest & kode, start_foreground harus
 * dipanggil cepat setelah startForegroundService(), izin notifikasi runtime
 * (API 33+) bisa ditolak pengguna. Semua ini WAJIB dites di device nyata.
 *
 * Desain komunikasi ke MainActivity: SENGAJA pakai companion object + polling
 * sederhana (bukan Binder/LiveData/Flow) supaya tidak menambah dependency baru
 * dan lebih mudah dinalar kebenarannya tanpa emulator. Ini trade-off sadar:
 * kurang "rapi" secara arsitektur Android, tapi risiko lebih kecil untuk
 * tahap ini -- bisa dirapikan nanti (lihat TODO.md).
 */
class BotForegroundService : Service() {

    private var socket: ReconnectingWebSocket? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // startForeground HARUS dipanggil secepat mungkin setelah Service dibuat,
        // terlepas dari apakah WebSocket sudah tersambung -- dua hal ini sengaja
        // dipisah supaya tidak kena ForegroundServiceDidNotStartInTimeException.
        startForegroundCompat(buildNotification(ReconnectingWebSocket.ConnectionStatus.CONNECTING))
        connectSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: kalau OS membunuh proses karena tekanan RAM, Android akan
        // coba membuat ulang Service ini (intent null) -- sesuai permintaan "auto
        // reconnect" supaya tidak perlu pengguna buka app manual lagi.
        return START_STICKY
    }

    override fun onDestroy() {
        socket?.close()
        socket = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null // tidak dipakai, MainActivity poll companion object

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
            NotificationManager.IMPORTANCE_LOW, // LOW = tidak bersuara/mengganggu, tetap persisten terlihat
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
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ME Futures AI Bot")
            .setContentText(text)
            // Placeholder: ikon stok platform, BUKAN ic_launcher_foreground (ikon
            // berwarna penuh biasanya tampil aneh di status bar yang mewajibkan
            // ikon monokrom). Ganti dengan ikon notifikasi khusus nanti.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: ReconnectingWebSocket.ConnectionStatus) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            // API 26-28: overload dengan parameter tipe belum ada.
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "bot_status"
        private const val NOTIFICATION_ID = 1

        /**
         * Dibaca MainActivity lewat polling sederhana (lihat MainActivity.kt).
         * @Volatile supaya pembaruan dari thread Service langsung terlihat oleh
         * thread UI tanpa perlu mekanisme sinkronisasi tambahan.
         */
        @Volatile var latestStatus: ReconnectingWebSocket.ConnectionStatus = ReconnectingWebSocket.ConnectionStatus.CONNECTING
            private set

        @Volatile var latestMessage: String = ""
            private set
    }
}
