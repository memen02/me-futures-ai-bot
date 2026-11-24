package com.mefuturesaibot.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mefuturesaibot.app.scanner.*

/** Owns public screening and the local paper ledger independently of the exchange executor. */
class ScreeningService : Service() {
    private lateinit var monitor: PaperPositionMonitor
    private lateinit var scanner: MarketScanner
    private lateinit var liveFeed: LiveMarketFeed
    private val liveHandler = Handler(Looper.getMainLooper())
    private var baseline = Scan()

    private var marketBase = Scan()

    private val liveTick = object : Runnable {
        override fun run() {
            val base = marketBase
            val elapsed = SystemClock.elapsedRealtime()
            val marketTime = base.time + elapsed - base.received
            val restFresh = base.fresh(elapsed)
            var liveCount = 0

            val rows = base.rows.map { row ->
                val ticker = liveFeed.ticker(row.contract.symbol)

                if (ticker != null &&
                    (marketTime - ticker.time) in -5000L..15_000L &&
                    ticker.time >= (row.ticker?.time ?: 0L)
                ) {
                    liveCount++
                    row.copy(ticker = ticker)
                } else {
                    row.copy(ticker = row.ticker?.takeIf {
                        restFresh && it.fresh(marketTime)
                    })
                }
            }

            marketStatus = if (liveCount > 0) {
                "LIVE $liveCount/${rows.size}"
            } else {
                val state = liveFeed.status()
                if (state == "LIVE") "WAITING DATA" else state
            }

            val available = rows.any { it.ticker != null }

            val next = baseline.copy(
                rows = rows,
                time = base.time,
                received = base.received,
                ok = available
            )

            // Waktu tampilan diperbarui tanpa mengubah
            // snapshot asli untuk analisis dan trading.
            val display = if (liveCount > 0) {
                next.copy(
                    time = marketTime,
                    received = elapsed
                )
            } else {
                next
            }

            if (displayLatest != display) {
                displayLatest = display
            }

            liveHandler.postDelayed(this, 1000L)
        }
    }
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
        liveFeed = LiveMarketFeed()
        liveFeed.start()

        scanner = MarketScanner(store, enabled) {
            baseline = it
            latest = it
            if (it.ok && it.rows.isNotEmpty()) {
                marketBase = it
            }
        }

        scanner.start()
        liveHandler.post(liveTick)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        liveHandler.removeCallbacksAndMessages(null)
        liveFeed.close()
        marketStatus = "OFFLINE"
        monitor.close()
        scanner.close()
        baseline = Scan()
        marketBase = Scan()
        latest = Scan()
        displayLatest = Scan()
        super.onDestroy()
    }
    companion object {
        @Volatile internal var latest = Scan()
            private set

        @Volatile internal var marketStatus = "OFFLINE"
            private set

        @Volatile internal var displayLatest = Scan()
            private set
    }
}
