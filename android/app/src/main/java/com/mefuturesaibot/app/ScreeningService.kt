package com.mefuturesaibot.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mefuturesaibot.app.scanner.*

/** Owns public screening and the local paper ledger independently of the exchange executor. */
class ScreeningService : Service() {
    private lateinit var monitor: PaperPositionMonitor
    private lateinit var scanner: MarketScanner
    override fun onCreate() {
        super.onCreate()
        val channel = "public_market_scanner"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "Pemindaian pasar Futures", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ME Project · Scanner Futures").setContentText("Memantau kontrak aktif Binance")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(2, notification)
        val store = DeskStore(this)
        val enabled = { BotPreferences.shouldRun(this) && BotForegroundService.isRunning }
        monitor = PaperPositionMonitor(store,enabled).also { it.start() }
        scanner = MarketScanner(store, enabled) { latest = it }
        scanner.start()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { monitor.close(); scanner.close(); latest = Scan(); super.onDestroy() }
    companion object {
        @Volatile internal var latest = Scan()
            private set
    }
}
