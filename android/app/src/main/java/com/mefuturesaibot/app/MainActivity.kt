package com.mefuturesaibot.app

import com.mefuturesaibot.app.scanner.TradingDesk
import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Switch
import com.mefuturesaibot.app.exchange.BookQuote
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.ExchangeClock
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.SmaSignal
import com.mefuturesaibot.app.exchange.SignalSide

/**
 * MURNI UI: tidak memegang ReconnectingWebSocket sendiri (ada di BotForegroundService).
 *
 * PERBAIKAN (audit 2026-11-10, temuan #3): dulu service AUTO-START tanpa kontrol
 * apa pun begitu app dibuka, dan tidak ada cara menghentikannya dari UI. Sekarang
 * ada tombol Start/Stop eksplisit -- service baru jalan kalau pengguna menekan Start.
 */
class MainActivity : Activity() {
    private lateinit var desk: TradingDesk
    private lateinit var signalView: TextView
    private lateinit var signalReason: TextView
    private lateinit var signalDetails: TextView
    private lateinit var statusView: TextView
    private lateinit var historyButton: Button
    private lateinit var bidView: TextView
    private lateinit var askView: TextView
    private lateinit var quoteTimeView: TextView
    private lateinit var historyStatusView: TextView
    private lateinit var chart: ClosePriceChart
    private lateinit var chartKey: TextView
    private lateinit var smaPanel: View
    private val pollHandler = Handler(Looper.getMainLooper())
    private val quoteTime = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
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

        setContentView(R.layout.activity_trading_desk)
        signalView = findViewById(R.id.dash_signal)
        signalReason = findViewById(R.id.dash_reason)
        signalDetails = findViewById(R.id.dash_sma_details)
        smaPanel = findViewById(R.id.dash_sma_panel)
        statusView = findViewById(R.id.dash_status)
        bidView = findViewById(R.id.dash_bid)
        askView = findViewById(R.id.dash_ask)
        quoteTimeView = findViewById(R.id.dash_quote_time)
        historyStatusView = findViewById(R.id.dash_history_status)
        chart = findViewById(R.id.dash_chart)
        chartKey = findViewById(R.id.dash_chart_key)
        historyButton = findViewById(R.id.dash_history)
        historyButton.setOnClickListener { showCandleHistory() }
        findViewById<Button>(R.id.dash_start).setOnClickListener { onStartClicked() }
        findViewById<Button>(R.id.dash_stop).setOnClickListener { onStopClicked() }
        findViewById<Switch>(R.id.dash_auto_start).apply {
            isChecked = BotPreferences.isAutoStartEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, enabled -> BotPreferences.setAutoStartEnabled(this@MainActivity, enabled) }
        }
        desk = TradingDesk(this, savedInstanceState?.getInt("deskTab") ?: 0, ::onStartClicked)
        userRequestedStop = savedInstanceState?.getBoolean("userRequestedStop") ?: false
        refreshStatusText()
        ensureNotificationPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("userRequestedStop", userRequestedStop)
        outState.putInt("deskTab", desk.tab)
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
        desk.update(BotForegroundService.latestScan, !userRequestedStop && BotForegroundService.isRunning)
        if (userRequestedStop || !BotForegroundService.isRunning) {
            renderSignal(SmaSignal.waitFor(if (userRequestedStop) "Pemantauan dihentikan" else "Tekan Start untuk memantau BTC/USDT"))
            statusView.text = if (userRequestedStop) "Pemantauan dihentikan" else "Pemantauan belum dimulai"
            bidView.text = "—"
            askView.text = "—"
            quoteTimeView.text = "Belum ada pembaruan harga"
            historyStatusView.text = "Belum ada data candle"
            historyButton.visibility = View.GONE
            chart.show(emptyList())
            chart.visibility = View.GONE
            chartKey.visibility = View.GONE
            return
        }
        renderMarket(BotForegroundService.latestStatus, BotForegroundService.latestQuote,
            BotForegroundService.latestHistory, BotForegroundService.exchangeClock, SystemClock.elapsedRealtime())
    }

    internal fun renderMarket(
        connection: ReconnectingWebSocket.ConnectionStatus,
        quote: BookQuote?,
        history: CandleHistory,
        clock: ExchangeClock,
        elapsed: Long,
    ) {
        historyButton.visibility = View.VISIBLE
        historyButton.isEnabled = history.candles.isNotEmpty()
        val stale = quote?.isStale(elapsed, clock) ?: true
        val exchangeTime = if (connection == ReconnectingWebSocket.ConnectionStatus.CONNECTED && !stale)
            clock.at(elapsed) else null
        val historyStatus = history.statusAt(exchangeTime)
        val lastOpen = history.candles.lastOrNull()?.openTime
        historyStatusView.text = if (lastOpen == null) historyStatus else {
            val start = quoteTime.format(Instant.ofEpochMilli(history.candles.first().openTime))
            val end = quoteTime.format(Instant.ofEpochMilli(lastOpen))
            "$historyStatus · ${history.candles.size} × 1 menit\n$start — $end"
        }
        chart.show(history.candles)
        chart.visibility = if (history.candles.isEmpty()) View.GONE else View.VISIBLE
        chartKey.visibility = chart.visibility
        chartKey.text = chart.rangeLabel
        statusView.text = describeStatus(connection)
        val signal = when {
            connection != ReconnectingWebSocket.ConnectionStatus.CONNECTED -> SmaSignal.waitFor("Menunggu koneksi pasar")
            quote == null -> SmaSignal.waitFor("Menunggu harga BTC/USDT")
            stale -> SmaSignal.waitFor("Harga pasar kedaluwarsa")
            else -> history.signalAt(exchangeTime)
        }
        renderSignal(signal)
        bidView.text = quote?.bid ?: "—"
        askView.text = quote?.ask ?: "—"
        quoteTimeView.text = if (quote == null) {
            "Menunggu harga BTC/USDT"
        } else {
            val updated = quoteTime.format(Instant.ofEpochMilli(quote.eventTimeMs))
            if (stale) "Data kedaluwarsa · $updated" else "Pembaruan bursa · $updated"
        }
    }

    private fun renderSignal(signal: SmaSignal) {
        signalView.text = signal.side.name
        signalReason.text = signal.reason
        smaPanel.visibility = if (signal.intervals.isEmpty()) View.GONE else View.VISIBLE
        signalDetails.text = signal.intervals.joinToString("\n\n") { interval ->
            val position = when {
                interval.direction > 0 -> "di atas SMA"
                interval.direction < 0 -> "di bawah SMA"
                else -> "sama dengan SMA"
            }
            val time = quoteTime.format(Instant.ofEpochMilli(interval.closedAtMs))
            "${interval.minutes} menit · $position · $time\n" +
                "Tutup ${interval.close.stripTrailingZeros().toPlainString()}   SMA 20 ${interval.sma.stripTrailingZeros().toPlainString()}"
        }
        val color = when (signal.side) {
            SignalSide.WAIT -> "#FFD19C"
            SignalSide.BUY -> "#82E5BE"
            SignalSide.SELL -> "#FF9B8F"
        }
        signalView.setTextColor(Color.parseColor(color))
    }

    private fun showCandleHistory() {
        val candles = BotForegroundService.latestHistory.candles.asReversed()
        val rows = candles.map { candle ->
            val time = quoteTime.format(Instant.ofEpochMilli(candle.openTime))
            "$time  ·  Tutup ${candle.close}"
        }
        AlertDialog.Builder(this)
            .setTitle("BTC/USDT · Candle tertutup 1 menit")
            .setItems(rows.toTypedArray()) { _, index ->
                val candle = candles[index]
                val time = quoteTime.format(Instant.ofEpochMilli(candle.openTime))
                AlertDialog.Builder(this)
                    .setTitle("BTC/USDT · $time")
                    .setMessage("Buka ${candle.open}\nTinggi ${candle.high}\nRendah ${candle.low}\n" +
                        "Tutup ${candle.close}\nVolume ${candle.volume} BTC")
                    .setPositiveButton("Tutup", null)
                    .show()
            }
            .setPositiveButton("Tutup", null)
            .show()
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
        ReconnectingWebSocket.ConnectionStatus.CONNECTED -> "WebSocket tersambung"
        ReconnectingWebSocket.ConnectionStatus.DISCONNECTED -> "Terputus, mencoba lagi..."
        ReconnectingWebSocket.ConnectionStatus.RECONNECTING -> "Menyambung ulang..."
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }
}
