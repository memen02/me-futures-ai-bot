package com.mefuturesaibot.app.scanner

import android.app.Activity
import android.view.View
import android.widget.*
import com.mefuturesaibot.app.R
import com.mefuturesaibot.app.demo.FuturesVenue
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class TradingReports(private val activity: Activity, private val store: DeskStore) {
    private val mode = activity.findViewById<Spinner>(R.id.report_mode)
    private var offset = 0L
    private var lastKey: List<Any?>? = null
    private fun label(id: Int) = activity.findViewById<TextView>(id)
    private fun money(n: Double) = String.format(Locale.US,"%,.3f",n)
    init {
        mode.adapter = ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,
            listOf("Paper · Simulasi lokal","Demo · Riwayat bot di HP","Mainnet · Riwayat bot di HP"))
        mode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, p: Int, id: Long) { offset = 0; render() }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        label(R.id.previous_month).setOnClickListener { offset--; render() }
        label(R.id.next_month).setOnClickListener { if (offset < 0) offset++; render() }
    }

    fun render() {
        val venue = FuturesVenue.selected(activity)
        val exchange = venue.prefs(activity)
        val position = exchange.getString("intent",null)
        val reportVenue = when(mode.selectedItemPosition) { 1 -> FuturesVenue.DEMO; 2 -> FuturesVenue.REAL; else -> null }
        val prefs = reportVenue?.prefs(activity)
        val saved = prefs?.getString("closedLedger",null)
        val past = (prefs?.getInt("n",0) ?: 0) + (prefs?.getInt("unrated",0) ?: 0)
        val month = YearMonth.now(ZoneOffset.UTC).plusMonths(offset)
        val paper = if (reportVenue == null) store.read().second.ledger else null
        val state = position?.let { JSONObject(it) }
        val mark = com.mefuturesaibot.app.BotForegroundService.exchangeMark?.takeIf {
            it.first == (venue.name to state?.optString("id")) && it.second.fresh(android.os.SystemClock.elapsedRealtime())
        }?.second
        val key = listOf(venue,position,mark,month,reportVenue,saved,past,paper)
        if (lastKey == key) return
        lastKey = key
        label(R.id.exchange_position).text = when {
            state == null -> "${venue.label} · Belum ada posisi bot yang dipantau"
            !state.has("entry") -> "${venue.label} · BTCUSDT · Menunggu konfirmasi fill"
            else -> "${venue.label} · BTCUSDT · ${if(state.getInt("side")>0) "LONG" else "SHORT"}\n" +
                "Entry ${state.getDouble("entry")} · Jumlah ${state.getDouble("heldQty")} BTC\n" +
                "SL ${state.getString("stop")} · TP ${state.getString("target")}\nPosisi terakhir yang dikonfirmasi bot"
        }
        if (state?.has("entry") == true) {
            val liquidation = state.optDouble("liquidation",0.0)
            val pnl = mark?.let { (it.price - state.getDouble("entry")) * state.getDouble("heldQty") * state.getInt("side") }
            label(R.id.exchange_position).append("\nMark ${mark?.price ?: "—"} · PnL laten ${pnl?.let(::money) ?: "—"} USDT")
            if (liquidation > 0) label(R.id.exchange_position).append("\nLikuidasi menurut snapshot bursa $liquidation")
        }
        val ledger = paper ?: saved?.let { TradeLedger.read(JSONObject(it)) } ?: TradeLedger(incomplete = past > 0)
        val m = ledger.months[month.toString()] ?: MonthResult()
        label(R.id.report_month).text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("id-ID"))) + " · UTC"
        label(R.id.next_month).isEnabled = offset < 0
        label(R.id.report_gain).text = "+${money(m.gain)}"
        label(R.id.report_loss).text = "−${money(m.loss)}"
        label(R.id.report_net).apply {
            text = money(m.gain - m.loss)
            setTextColor(activity.getColor(when {
                m.gain > m.loss -> R.color.me_buy
                m.gain < m.loss -> R.color.me_sell
                else -> R.color.me_text
            }))
        }
        label(R.id.report_totals).text = buildString {
            append("USDT · ${m.count} posisi ditutup")
            if (m.unknown > 0) append(" · ${m.unknown} belum dinilai")
            if (ledger.incomplete) append("\nRiwayat lama tidak lengkap. Total hanya mencakup hasil tercatat.")
        }
        val rows = ledger.recent.filter { TradeLedger.month(it.at) == month.toString() }
        label(R.id.report_closed).text = rows.joinToString("\n\n") {
            val stamp = Instant.ofEpochMilli(it.at).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
            val pnl = it.net?.let { n -> (if(n>0) "Profit +" else if(n<0) "Loss " else "Impas ") + money(n) + " USDT" } ?: "Belum dinilai"
            "${it.symbol} · $pnl\n$stamp UTC · ${it.reason}"
        }.ifEmpty { if (m.count == 0) "Belum ada penutupan tercatat bulan ini." else "Detail di luar 150 penutupan terakhir." }
    }
}
