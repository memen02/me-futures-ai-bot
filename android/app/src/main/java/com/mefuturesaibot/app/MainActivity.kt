package com.mefuturesaibot.app

import com.mefuturesaibot.app.scanner.DeskTab
import com.mefuturesaibot.app.scanner.TradingDesk
import android.Manifest
import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import com.mefuturesaibot.app.exchange.PublicChart
import com.mefuturesaibot.app.exchange.PublicChartFeed
import com.mefuturesaibot.app.exchange.chartIntervals
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
    private lateinit var depth: DashboardDepth
    private lateinit var chart: ClosePriceChart
    internal var chartFeed = PublicChartFeed(publish = ::renderChart)
    private var visibleActivity = false
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
        depth = DashboardDepth(this)
        historyButton = findViewById(R.id.dash_history)
        val intervalButton = findViewById<Button>(R.id.dash_interval)
        intervalButton.setOnClickListener {
            val labels = chartIntervals.map { if (it == "1M") "1M · 1 bulan" else it }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Interval grafik BTC/USDT")
                .setSingleChoiceItems(labels, chartIntervals.indexOf(chartFeed.state.interval)) { dialog, index ->
                    chartFeed.select(chartIntervals[index])
                    dialog.dismiss()
                }.setNegativeButton("Tutup", null).show()
        }
        savedInstanceState?.getString("chartInterval")?.takeIf { it in chartIntervals }?.let(chartFeed::select)
        val home = findViewById<android.widget.ScrollView>(R.id.page_home)
        for ((link, section) in listOf(R.id.jump_market to R.id.section_market,
            R.id.jump_strategy to R.id.section_strategy, R.id.jump_watch to R.id.section_watch,
            R.id.jump_risk to R.id.section_risk)) {
            findViewById<View>(link).setOnClickListener {
                val dest = findViewById<View>(section)
                val bounds = android.graphics.Rect()
                dest.getDrawingRect(bounds)
                home.offsetDescendantRectToMyCoords(dest, bounds)
                home.smoothScrollTo(0, bounds.top)
            }
        }
        historyButton.setOnClickListener { showCandleHistory() }
        findViewById<Button>(R.id.scan_stop).setOnClickListener { onStopClicked() }
        desk = TradingDesk(this, DeskTab.restore(savedInstanceState), ::onStartClicked)
        desk.onTabSelected = {
            if (desk.tab != DeskTab.HOME) depth.clear()
            chartFeed.setVisible(visibleActivity && desk.tab == DeskTab.HOME)
            refreshStatusText()
        }
        userRequestedStop = savedInstanceState?.getBoolean("userRequestedStop") ?: false
        refreshStatusText()
        ensureNotificationPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("userRequestedStop", userRequestedStop)
        outState.putString(DeskTab.STATE_KEY, desk.tab.name)
        outState.putString("chartInterval", chartFeed.state.interval)
    }

    override fun onStart() {
        super.onStart()
        visibleActivity = true
        chartFeed.setVisible(desk.tab == DeskTab.HOME)
        pollRunnable.run()
    }

    override fun onStop() {
        super.onStop()
        pollHandler.removeCallbacks(pollRunnable)
        visibleActivity = false
        chartFeed.setVisible(false)
        depth.clear()
        desk.releaseDisplayData()
        chart.show(emptyList(), chartFeed.state.interval)
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
        if (desk.tab != DeskTab.HOME) return
        if (userRequestedStop || !BotForegroundService.isRunning) {
            renderSignal(SmaSignal.waitFor(if (userRequestedStop) "Pemantauan dihentikan" else "Mulai pemantauan dari tab Pasar"))
            statusView.text = if (userRequestedStop) "Pemantauan dihentikan" else "Pemantauan belum dimulai"
            bidView.text = "—"
            askView.text = "—"
            findViewById<TextView>(R.id.desk_spread).text = "Spread —"
            quoteTimeView.text = "Belum ada pembaruan harga"
            renderChartPrice(chartFeed.state)
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
        val stale = quote?.isStale(elapsed, clock) ?: true
        val exchangeTime = if (connection == ReconnectingWebSocket.ConnectionStatus.CONNECTED && !stale)
            clock.at(elapsed) else null
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
        findViewById<TextView>(R.id.desk_spread).text = if (quote == null) "Spread —" else {
            val spread = quote.ask.toBigDecimal() - quote.bid.toBigDecimal()
            "Spread ${spread.toPlainString()} USDT\nVolume bid ${quote.bidQuantity ?: "—"} · ask ${quote.askQuantity ?: "—"} BTC"
        }
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
            SignalSide.WAIT -> R.color.me_accent
            SignalSide.BUY -> R.color.me_buy
            SignalSide.SELL -> R.color.me_sell
        }
        val tint = getColor(color)
        signalView.setTextColor(tint)
        findViewById<TextView>(R.id.dash_signal_tag).apply {
            text = signal.side.name
            setTextColor(tint)
        }
    }

    internal fun renderChart(state: PublicChart) {
        findViewById<Button>(R.id.dash_interval).text = "Interval grafik · ${state.interval} ▾"
        chart.show(state.bars, state.interval)
        chart.visibility = if (state.bars.isEmpty()) View.GONE else View.VISIBLE
        chartKey.visibility = chart.visibility
        chartKey.text = chart.rangeLabel
        historyButton.isEnabled = state.bars.size > 1
        val at = state.updatedAt?.let { quoteTime.format(Instant.ofEpochMilli(it)) }
        historyStatusView.text = if (at == null) state.status else "Data $at · ${state.status}"
        if (!BotForegroundService.isRunning || userRequestedStop) renderChartPrice(state)
    }

    private fun renderChartPrice(state: PublicChart) {
        val last = state.bars.lastOrNull()
        findViewById<TextView>(R.id.dash_last_price).text = last?.close?.stripTrailingZeros()?.toPlainString() ?: "—"
        findViewById<TextView>(R.id.dash_ticker_time).text = when {
            last == null -> "Menunggu harga candle"
            state.fresh -> "Harga candle · ${state.interval}"
            else -> "Harga candle terakhir · ${state.interval} · belum diperbarui"
        }
    }

    private fun showCandleHistory() {
        val window = chartFeed.state
        val chartTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())
        val candles = window.bars.dropLast(1).asReversed()
        val rows = candles.map { candle ->
            val time = chartTime.format(Instant.ofEpochMilli(candle.openTime))
            "$time  ·  Tutup ${candle.close}"
        }
        AlertDialog.Builder(this)
            .setTitle("BTC/USDT · Candle tertutup ${window.interval}")
            .setItems(rows.toTypedArray()) { _, index ->
                val candle = candles[index]
                val time = chartTime.format(Instant.ofEpochMilli(candle.openTime))
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
