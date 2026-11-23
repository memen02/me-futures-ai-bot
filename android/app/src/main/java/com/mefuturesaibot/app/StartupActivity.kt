package com.mefuturesaibot.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.mefuturesaibot.app.exchange.BookQuote
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.CandleHistorySync
import com.mefuturesaibot.app.exchange.ExchangeClock
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket.ConnectionStatus

class StartupActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val clock = ExchangeClock()
    private var connection = ConnectionStatus.CONNECTING
    private var quote: BookQuote? = null
    private var history = CandleHistory()
    private val rest by lazy { CandleHistorySync(clock = clock) { history = it } }
    private var socket: ReconnectingWebSocket? = null
    private var usingService = false
    private var checking = false
    private var startedAt = 0L
    private var allPassedAt: Long? = null

    private lateinit var summary: TextView
    private lateinit var progress: ProgressBar
    private lateinit var retry: Button
    private val marks = mutableListOf<TextView>()
    private val details = mutableListOf<TextView>()
    private val tick = object : Runnable {
        override fun run() {
            if (!checking) return
            val now = SystemClock.elapsedRealtime()
            val timedOut = now - startedAt >= 20_000L
            val rows = if (usingService) {
                startupRows(BotForegroundService.latestStatus, BotForegroundService.latestQuote,
                    BotForegroundService.latestHistory, BotForegroundService.exchangeClock, now, timedOut)
            } else startupRows(connection, quote, history, clock, now, timedOut)
            val complete = rows.count { it.state == CheckState.PASSED }
            rows.forEachIndexed { i, row ->
                details[i].text = row.detail
                val (mark, color) = when (row.state) {
                    CheckState.PASSED -> "✓" to R.color.me_buy
                    CheckState.FAILED -> "×" to R.color.me_sell
                    CheckState.PENDING -> "…" to R.color.me_muted
                }
                marks[i].text = mark
                marks[i].setTextColor(getColor(color))
            }
            progress.progress = complete
            val ready = complete == rows.size
            val title = when {
                ready -> "Semua koneksi siap"
                timedOut -> "Pemeriksaan belum lengkap · $complete/3"
                else -> "Memeriksa koneksi · $complete/3"
            }
            if (summary.text.toString() != title) summary.text = title
            retry.visibility = if (timedOut || rows.any { it.state == CheckState.FAILED }) View.VISIBLE else View.GONE
            if (ready) {
                val since = allPassedAt ?: now.also { allPassedAt = it }
                if (now - since >= 900L) {
                    openDashboard()
                    return
                }
            } else {
                allPassedAt = null
                if (timedOut) {
                    stopChecks()
                    return
                }
            }
            handler.postDelayed(this, 150L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startup)
        summary = findViewById(R.id.startup_summary)
        progress = findViewById(R.id.startup_progress)
        retry = findViewById(R.id.startup_retry)
        val rows = findViewById<LinearLayout>(R.id.startup_rows)
        for (title in listOf("Binance Futures", "Harga BTC/USDT", "Riwayat candle")) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(9), dp(2), dp(9))
            }
            val mark = TextView(this).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(mark, LinearLayout.LayoutParams(dp(38), -2))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.me_text))
            })
            val detail = TextView(this).apply {
                textSize = 14f
                setTextColor(getColor(R.color.me_muted))
            }
            labels.addView(detail)
            row.addView(labels)
            rows.addView(row)
            marks.add(mark)
            details.add(detail)
        }
        retry.setOnClickListener { beginChecks() }
        findViewById<Button>(R.id.startup_open).setOnClickListener { openDashboard() }
    }

    override fun onStart() {
        super.onStart()
        beginChecks()
    }

    override fun onStop() {
        stopChecks()
        super.onStop()
    }

    private fun beginChecks() {
        stopChecks()
        usingService = BotForegroundService.isRunning
        checking = true
        allPassedAt = null
        startedAt = SystemClock.elapsedRealtime()
        connection = ConnectionStatus.CONNECTING
        quote = null
        history = CandleHistory()
        if (!usingService) {
            rest.start()
            socket = ReconnectingWebSocket(
                url = ReconnectingWebSocket.BTC_BOOK_TICKER_URL,
                onStatusChange = {
                    connection = it
                    if (it != ConnectionStatus.CONNECTED) quote = null
                },
                onMessage = { message ->
                    BookQuote.parse(message, SystemClock.elapsedRealtime())?.let { next ->
                        if (next.eventTimeMs >= (quote?.eventTimeMs ?: 0L)) {
                            clock.observe(next.eventTimeMs, next.receivedElapsedMs)
                            quote = next
                        }
                    }
                },
            ).also { it.connect() }
        }
        tick.run()
    }

    private fun stopChecks() {
        handler.removeCallbacks(tick)
        if (!checking) return
        checking = false
        if (!usingService) {
            socket?.close()
            socket = null
            rest.stop()
        }
    }

    private fun openDashboard() {
        if (isFinishing) return
        stopChecks()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
