package com.mefuturesaibot.app

import com.mefuturesaibot.app.security.UiAccess

import android.app.Activity
import com.mefuturesaibot.app.security.SecureActivity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import com.mefuturesaibot.app.exchange.*
import com.mefuturesaibot.app.scanner.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.util.Locale

class TokenDetailActivity : SecureActivity() {
    private lateinit var contract: Contract
    private lateinit var store: DeskStore
    private lateinit var chart: ClosePriceChart
    private lateinit var feed: PublicChartFeed
    private lateinit var depth: DashboardDepth
    private var lastTicker: Ticker? = null
    private var lastTickerFresh: Boolean? = null
    private var cachedModel = MarketModel()
    private var nextModelRefresh = 0L
    private val main = Handler(Looper.getMainLooper())
    private val signals = TokenSignals { _, signal -> renderSignal(signal) }
    private val clock = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
    private fun at(value: Long) = clock.format(Instant.ofEpochMilli(value))
    private fun label(id: Int) = findViewById<TextView>(id)
    private var active = false
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            renderLiveTicker()
            signals.watch(listOf(contract.symbol))
            renderSignal(signals.signal(contract.symbol))
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!requireDeviceAccess()) return
        val symbol = intent.getStringExtra("symbol").orEmpty()
        val quote = intent.getStringExtra("quote").orEmpty()
        val base = intent.getStringExtra("base").orEmpty()
        if (symbol.isBlank() || quote.isBlank() || base.isBlank()) { finish(); return }
        contract = Contract(symbol, quote, base)
        store = DeskStore(this)
        setContentView(R.layout.activity_token)
        AssetLogo.bind(findViewById(R.id.token_identity), base)
        chart = findViewById(R.id.dash_chart)
        chart.onSelect = ::candle
        feed = PublicChartFeed(symbol = symbol, publish = ::renderChart)
        depth = DashboardDepth(symbol, ::renderBook)
        label(R.id.token_switch).apply {
            text = "$base/$quote  ▾"
            contentDescription = "Ganti kontrak, saat ini $base/$quote"
            setOnClickListener { setResult(RESULT_OK); finish() }
        }
        findViewById<View>(R.id.token_back).setOnClickListener { finish() }
        findViewById<View>(R.id.token_star).setOnClickListener {
            val stars = store.favorites
            store.favorites = if (symbol in stars) stars - symbol else stars + symbol
            favorite()
        }
        favorite()
        findViewById<Switch>(R.id.token_overlay).apply {
            isChecked = state?.getBoolean("overlay", true) ?: true
            chart.overlays = isChecked
            setOnCheckedChangeListener { _, enabled -> chart.overlays = enabled }
        }
        findViewById<View>(R.id.dash_interval).setOnClickListener {
            if (!UiAccess.allowed(this)) return@setOnClickListener
            val dialog = AlertDialog.Builder(this).setTitle("Interval · $symbol").setSingleChoiceItems(
                chartIntervals.toTypedArray(), chartIntervals.indexOf(feed.state.interval)) { dialog, i ->
                feed.select(chartIntervals[i]); dialog.dismiss()
            }.show()
            UiAccess.protect(this, dialog)
        }
        findViewById<View>(R.id.token_candle_picker).setOnClickListener {
            val bars = feed.state.bars
            val options = bars.map { "${at(it.openTime)} · ${it.close.plain()}" }.reversed().toTypedArray()
            if (!UiAccess.allowed(this)) return@setOnClickListener
            val dialog = AlertDialog.Builder(this).setTitle("Candle · $symbol").setItems(options) { _, i ->
                val selectedTime = bars[bars.lastIndex - i].openTime
                val current = feed.state.bars.indexOfFirst { it.openTime == selectedTime }
                chart.selectCandle(current)
            }.show()
            UiAccess.protect(this, dialog)
        }
        state?.getString("interval")?.takeIf { it in chartIntervals }?.let(feed::select)
    }
    private fun favorite() {
        val saved = contract.symbol in store.favorites
        label(R.id.token_star).apply {
            text = if (saved) "★" else "☆"
            contentDescription = if (saved) "Hapus dari favorit" else "Tambah ke favorit"
        }
    }

    override fun onStart() {
        super.onStart()
        if (isFinishing) return
        if (!::feed.isInitialized) return
        active = true
        feed.setVisible(true)
        depth.start()
        lastTickerFresh = null
        nextModelRefresh = 0L
        main.removeCallbacks(tick)
        tick.run()
    }
    override fun onStop() {
        active = false
        if (::feed.isInitialized) {
            feed.setVisible(false)
            depth.stop()
            signals.stop()
            main.removeCallbacksAndMessages(null)
        }
        super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        if (::feed.isInitialized) { out.putString("interval", feed.state.interval); out.putBoolean("overlay", chart.overlays) }
    }

    private fun renderLiveTicker() {
        val snapshot = ScreeningService.displayLatest
        val elapsed = SystemClock.elapsedRealtime()
        val current = detailTicker(
            snapshot,
            contract.symbol,
            elapsed,
            lastTicker
        )

        val fresh = current != null
        if (current != null) lastTicker = current

        val t = lastTicker
        val priceText = t?.price?.let {
            BigDecimal.valueOf(it).plain()
        } ?: "—"

        val statsText = if (t == null) {
            "Menunggu data Binance"
        } else {
            val status = if (fresh) {
                "Data pasar"
            } else {
                "STALE · Harga terakhir"
            }
            String.format(
                Locale.US,
                "%+.2f%% · 24j   H %s   L %s\nVolume %,.0f %s · %s\n%s",
                t.change,
                BigDecimal.valueOf(t.high).plain(),
                BigDecimal.valueOf(t.low).plain(),
                t.volume,
                contract.quote,
                at(t.time),
                status
            )
        }

        val priceView = label(R.id.token_price)
        if (priceView.text.toString() != priceText) {
            priceView.text = priceText
        }

        if (lastTickerFresh != fresh) {
            priceView.setTextColor(getColor(
                if (fresh) R.color.me_text
                else R.color.me_muted
            ))
            lastTickerFresh = fresh
        }

        val statsView = label(R.id.token_stats)
        if (statsView.text.toString() != statsText) {
            statsView.text = statsText
        }

        if (elapsed >= nextModelRefresh) {
            cachedModel = store.read().first
            nextModelRefresh = elapsed + 30_000L
        }

        val model = cachedModel
        val score = current?.let(model::score)

        val modelText = when {
            model.rounds < MarketModel.WARMUP ->
                "Regresi logistik · Pemanasan ${model.rounds}/${MarketModel.WARMUP} ronde scanner"
            score == null ->
                "Regresi logistik · Menunggu ticker valid"
            else -> String.format(
                Locale.US,
                "Regresi logistik · Skor arah naik %.1f/100\nInput: perubahan 24j, posisi dalam rentang, harga relatif terhadap rata-rata. Skor belum dikalibrasi.",
                score * 100
            )
        }

        val modelView = label(R.id.token_model)
        if (modelView.text.toString() != modelText) {
            modelView.text = modelText
        }
    }

    private fun renderSignal(signal: SmaSignal) {
        chart.mark(signal)
        label(R.id.dash_signal).apply {
            text = signal.side.name
            setTextColor(getColor(when (signal.side) {
                SignalSide.BUY -> R.color.me_buy
                SignalSide.SELL -> R.color.me_sell
                SignalSide.WAIT -> R.color.me_accent
            }))
        }
        label(R.id.token_strategy).text = buildString {
            append("SMA 20 · ${signal.reason}")
            signal.intervals.forEach { append("\n${it.minutes}m · ${at(it.closedAtMs)} · Tutup ${it.close.plain()} / SMA ${it.sma.plain()}") }
            if (signal.side != SignalSide.WAIT) append("\nBatal saat penutupan salah satu interval tidak lagi searah.")
        }
    }

    private fun renderChart(state: PublicChart) {
        chart.show(state.bars, state.interval)
        label(R.id.dash_interval).text = "${state.interval} ▾"
        label(R.id.dash_history_status).text = listOfNotNull(state.updatedAt?.let(::at), state.status).joinToString(" · ")
        findViewById<Button>(R.id.token_candle_picker).isEnabled = state.bars.isNotEmpty()
        candle(chart.selected)
    }
    private fun candle(i: Int) {
        val bars = feed.state.bars
        val bar = bars.getOrNull(i)
        label(R.id.token_candle).text = if (bar == null) "Menunggu candle" else buildString {
            append("${at(bar.openTime)} · ${feed.state.interval}\nO ${bar.open.plain()}   H ${bar.high.plain()}   L ${bar.low.plain()}   C ${bar.close.plain()}\nVolume ${bar.volume.plain()} ${contract.base}")
            val reading = CandleReading.at(bars, i)
            if (reading == null) append(if (i == bars.lastIndex) "\nCandle berjalan · menunggu penutupan" else "\nBelum ada 20 candle sebelumnya.")
            else {
                append("\n${reading.label} · S ${reading.support.plain()} / R ${reading.resistance.plain()}")
                reading.fib?.let { append("\nFib 61,8% · ${it.plain()} (swing rentang 20 candle sebelumnya)") }
                append("\n${reading.invalidation}")
            }
        }
    }
    private fun renderBook(book: DepthBook?, status: String) {
        fun levels(side: Map<BigDecimal, BigDecimal>?) = side?.entries?.take(5)?.joinToString("\n") {
            "${it.key.plain()} × ${it.value.plain()}"
        } ?: "—"
        label(R.id.book_bids).text = levels(book?.bids)
        label(R.id.book_asks).text = levels(book?.asks)
        label(R.id.book_status).text = if (book == null) status else
            "${at(book.time)} · Spread ${(book.asks.firstKey() - book.bids.firstKey()).plain()} ${contract.quote} · Jumlah ${contract.base}"
    }

    companion object {
        const val OPEN = 30
        internal fun intent(context: Context, c: Contract) = Intent(context, TokenDetailActivity::class.java)
            .putExtra("symbol", c.symbol).putExtra("base", c.base).putExtra("quote", c.quote)
    }
}
