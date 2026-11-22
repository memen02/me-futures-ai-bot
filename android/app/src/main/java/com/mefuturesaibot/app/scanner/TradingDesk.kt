package com.mefuturesaibot.app.scanner

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ListView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.mefuturesaibot.app.maintenance.CacheCleanupJob
import com.mefuturesaibot.app.account.AccountPage
import com.mefuturesaibot.app.BotPreferences
import com.mefuturesaibot.app.BotForegroundService
import com.mefuturesaibot.app.R
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun price(v: Double) = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)
private fun pct(v: Double) = String.format(Locale.US, "%+.2f%%", v)
private val clockFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
private fun time(v: Long) = clockFormat.format(Instant.ofEpochMilli(v))
private fun modelLabel(score: Double?) = when {
    score == null -> "Menunggu model"
    score > 0.57 -> "Bias naik"
    score < 0.43 -> "Bias turun"
    else -> "Netral"
}

internal class TradingDesk(
    private val activity: Activity, initialTab: Int, private val start: () -> Unit,
) {
    private fun <T : View> view(id: Int): T = activity.findViewById(id)
    private val store = DeskStore(activity)
    private val demo = com.mefuturesaibot.app.demo.DemoControls(activity, start)
    private val accountPage = AccountPage(activity, credentialsChanged = demo::render)
    private val nav = listOf(R.id.nav_home, R.id.nav_scanner, R.id.nav_paper, R.id.nav_account)
    private val pages = listOf(R.id.page_home, R.id.page_scanner, R.id.page_paper, R.id.page_account)
    private val query = view<EditText>(R.id.scan_query)
    private val filter = view<Spinner>(R.id.scan_filter)
    private val sort = view<Spinner>(R.id.scan_sort)
    private val adapter = TokenAdapter()
    private var snapshot = Scan()
    private var btcTicker: Ticker? = null
    private var btcFresh = false
    private var nextTerminalRefresh = Long.MAX_VALUE
    private var fresh = false
    private var running = false
    var onTabSelected: (() -> Unit)? = null
    var tab = 0
        private set

    init {
        nav.forEachIndexed { index, id -> view<TextView>(id).setOnClickListener { select(index) } }
        view<Button>(R.id.desk_history).setOnClickListener { select(2) }
        view<Button>(R.id.scan_start).setOnClickListener { start() }
        val list = view<ListView>(R.id.scan_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ -> showToken(adapter.getItem(i)) }
        configure(filter, listOf("Semua", "Naik 24j", "Turun 24j", "Favorit", "Bias naik", "Bias turun"))
        configure(sort, listOf("Volume", "% tertinggi", "% terendah", "Simbol A–Z"))
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { listTokens() }
            override fun afterTextChanged(s: Editable?) {}
        })
        view<Switch>(R.id.paper_enabled).apply {
            isChecked = store.simulation
            setOnCheckedChangeListener { _, enabled ->
                store.simulation = enabled
                if (enabled && !BotForegroundService.isRunning) start()
                renderAccount()
            }
        }
        view<Switch>(R.id.account_autostart).setOnCheckedChangeListener { _, enabled ->
            BotPreferences.setAutoStartEnabled(activity, enabled)
        }
        select(initialTab)
    }

    fun close() { demo.close(); accountPage.close() }

    fun select(index: Int) {
        if (tab == 1 && index != tab) clearTokenList()
        tab = index.coerceIn(0, pages.lastIndex)
        accountPage.show(tab == 3)
        pages.forEachIndexed { i, id -> view<View>(id).visibility = if (i == tab) View.VISIBLE else View.GONE }
        nav.forEachIndexed { i, id ->
            val button = view<TextView>(id)
            val tint = if (i == tab) Color.rgb(102, 202, 255) else Color.rgb(175, 185, 198)
            button.isSelected = i == tab
            button.setTextColor(tint)
            button.compoundDrawableTintList = ColorStateList.valueOf(tint)
        }
        if (tab == 3) {
            view<Switch>(R.id.account_autostart).isChecked = BotPreferences.isAutoStartEnabled(activity)
            view<TextView>(R.id.account_cache_status).text = CacheCleanupJob.status(activity)
        }
        update(BotForegroundService.latestScan, BotForegroundService.isRunning, force = true)
        onTabSelected?.invoke()
    }

    fun update(scan: Scan, active: Boolean, force: Boolean = false) {
        val elapsed = SystemClock.elapsedRealtime()
        val current = active && scan.fresh(elapsed)
        if (snapshot !== scan) btcTicker = scan.rows.firstOrNull { it.contract.symbol == "BTCUSDT" }?.ticker
        val tickerCurrent = current && btcTicker?.fresh(scan.time + (elapsed - scan.received)) == true
        if (!force && snapshot === scan && current == fresh && running == active && btcFresh == tickerCurrent &&
            (tab != 0 || elapsed < nextTerminalRefresh)) return
        btcFresh = tickerCurrent
        snapshot = scan
        fresh = current
        running = active
        val status = when {
            !active -> "Pemantauan berhenti"
            scan.ok && !current -> "Data scanner kedaluwarsa"
            else -> scan.status
        }
        val last = if (scan.time > 0) " · ${time(scan.time)}" else ""
        view<TextView>(R.id.scan_state).text = status + last
        view<Button>(R.id.scan_start).visibility = if (active) View.GONE else View.VISIBLE
        view<Button>(R.id.scan_stop).visibility = if (active) View.VISIBLE else View.GONE
        renderBtcTicker(btcTicker.takeIf { btcFresh })
        if (tab == 0) renderTerminal(elapsed)
        if (tab == 1) listTokens()
        if (tab == 2 || tab == 3) renderAccount()
    }

    private fun renderBtcTicker(ticker: Ticker?) {
        view<TextView>(R.id.dash_last_price).text = ticker?.let { money(it.price) } ?: "—"
        view<TextView>(R.id.dash_high).text = ticker?.let { money(it.high) } ?: "—"
        view<TextView>(R.id.dash_low).text = ticker?.let { money(it.low) } ?: "—"
        view<TextView>(R.id.dash_change).apply {
            text = ticker?.let { pct(it.change) + "  ·  24 jam" } ?: "—  ·  24 jam"
            setTextColor(when {
                ticker == null || ticker.change == 0.0 -> Color.LTGRAY
                ticker.change > 0 -> Color.rgb(130, 229, 190)
                else -> Color.rgb(255, 155, 143)
            })
        }
        view<TextView>(R.id.dash_ticker_time).text = when {
            ticker != null -> "Ticker · ${time(ticker.time)}"
            !running -> "Pemantauan nonaktif"
            btcTicker == null -> "Menunggu ticker BTC/USDT"
            else -> "Menunggu pembaruan ticker"
        }
    }

    private fun renderTerminal(elapsed: Long) {
        val marketTime = snapshot.time + (elapsed - snapshot.received)
        val rows = if (fresh) snapshot.rows.filter { it.ticker?.fresh(marketTime) == true } else emptyList()
        nextTerminalRefresh = rows.minOfOrNull { elapsed + (it.ticker!!.time + 120_001 - marketTime) } ?: Long.MAX_VALUE
        val btc = rows.firstOrNull { it.contract.symbol == "BTCUSDT" }
        val (model, account) = store.read()
        view<TextView>(R.id.desk_balance).text = money(account.balance)
        view<TextView>(R.id.desk_bot).text = if (com.mefuturesaibot.app.BuildConfig.MAINNET_ORDERS) {
            val execution = com.mefuturesaibot.app.demo.FuturesVenue.REAL.prefs(activity)
            when {
                execution.contains("intent") -> "Real · posisi/order dipantau"
                running && execution.getBoolean("enabled",false) -> "Real · aktif"
                else -> "Real · nonaktif"
            }
        } else if (running) "Aktif" else "Berhenti"
        view<TextView>(R.id.desk_volume).text = "Volume 24j · " + (btc?.ticker?.let { money(it.volume) + " USDT" } ?: "—")
        val score = btc?.score
        view<TextView>(R.id.desk_score).text = score?.let { "Skor arah naik  ${money(it * 100)}/100" } ?: "Skor arah naik  —"
        view<ProgressBar>(R.id.desk_meter).apply {
            visibility = if (score == null) View.GONE else View.VISIBLE
            progress = ((score ?: 0.0) * 100).toInt()
        }
        view<TextView>(R.id.desk_model).text = if (model.rounds < MarketModel.WARMUP)
            "Pemanasan model · ${model.rounds}/${MarketModel.WARMUP} ronde"
        else "${modelLabel(score)} · ${model.rounds} ronde belajar"
        val rising = rows.count { it.ticker!!.change > 0 }
        val falling = rows.count { it.ticker!!.change < 0 }
        view<TextView>(R.id.desk_breadth).text = when {
            rows.isEmpty() -> "Menunggu data pasar"
            rising > falling -> "Lebih banyak kontrak naik"
            falling > rising -> "Lebih banyak kontrak turun"
            else -> "Kenaikan dan penurunan seimbang"
        }
        view<TextView>(R.id.desk_coverage).text = if (rows.isEmpty()) "Belum ada ticker mutakhir" else
            "$rising naik · $falling turun · ${rows.size - rising - falling} datar"
        renderPerformance(account)
        renderWatch(rows)
    }

    private fun renderPerformance(account: PaperAccount) {
        val net = account.balance - PaperAccount.INITIAL_BALANCE
        view<TextView>(R.id.desk_profit).apply {
            text = "Perubahan saldo  ${money(net)} USDT"
            setTextColor(when {
                net > 0 -> Color.rgb(98, 225, 173)
                net < 0 -> Color.rgb(255, 130, 152)
                else -> Color.WHITE
            })
        }
        view<TextView>(R.id.desk_trades).text = "${account.trades} transaksi selesai · Sejak awal"
        view<TextView>(R.id.desk_winrate).text = if (account.ratedTrades == 0) "Rasio menang —" else
            "Rasio menang ${money(100.0 * account.wins / account.ratedTrades)}% · ${account.wins}/${account.ratedTrades} tercatat"
        val position = account.position
        view<TextView>(R.id.desk_position).text = position?.let {
            "${if (it.side > 0) "LONG" else "SHORT"} ${it.symbol} · Entry ${price(it.entry)}"
        } ?: "Belum ada posisi terbuka"
        view<TextView>(R.id.desk_risk).text = "Risiko 0,5% · 1 posisi · batas rugi harian 1,5% (UTC)"
        val status = when {
            !running -> "WAIT · pemantauan berhenti"
            !fresh && snapshot.ok -> "WAIT · data kedaluwarsa"
            else -> snapshot.status
        }
        view<TextView>(R.id.desk_targets).text = listOfNotNull(status,
            position?.let { "SL ${price(it.stop)} · TP ${price(it.target)}" }).joinToString("\n")
        if (account.measured > 0) {
            val gain = if (account.measuredWins > 0) money(account.profits / account.measuredWins) else "—"
            val loss = if (account.measuredLosses > 0) money(account.losses / account.measuredLosses) else "—"
            view<TextView>(R.id.desk_winrate).append("\nModel + SMA · ${account.measured} transaksi terukur" +
                "\nNet/trade ${money((account.profits - account.losses) / account.measured)} USDT · drawdown ${money(100 * account.drawdown)}%" +
                "\nRata-rata laba $gain / rugi $loss USDT")
        }
    }

    private fun renderWatch(rows: List<MarketRow>) {
        val stars = store.favorites
        val selected = if (stars.isEmpty()) rows.filter { it.contract.quote == "USDT" }
            .sortedByDescending { it.ticker!!.volume }.take(5)
        else rows.filter { it.contract.symbol in stars }.sortedBy { it.contract.symbol }.take(5)
        val source = if (stars.isEmpty()) "Top volume USDT · 24 jam" else "Kontrak favorit"
        view<TextView>(R.id.desk_watch_source).text = if (selected.isEmpty()) "$source · Menunggu ticker" else source
        val list = view<LinearLayout>(R.id.desk_watch_rows)
        list.removeAllViews()
        for (row in selected) {
            val ticker = requireNotNull(row.ticker)
            val cell = activity.layoutInflater.inflate(R.layout.market_row, list, false)
            cell.findViewById<TextView>(R.id.row_symbol).text = row.contract.symbol
            cell.findViewById<TextView>(R.id.row_price).text = price(ticker.price)
            cell.findViewById<TextView>(R.id.row_model).text = modelLabel(row.score)
            cell.findViewById<TextView>(R.id.row_change).apply {
                text = pct(ticker.change)
                setTextColor(when {
                    ticker.change > 0 -> Color.rgb(98, 225, 173)
                    ticker.change < 0 -> Color.rgb(255, 130, 152)
                    else -> Color.LTGRAY
                })
            }
            cell.isFocusable = true
            cell.setOnClickListener { showToken(row) }
            list.addView(cell)
        }
    }

    private fun configure(spinner: Spinner, options: List<String>) {
        spinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) { listTokens() }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    fun releaseDisplayData() {
        clearTokenList()
        snapshot = Scan()
        btcTicker = null
        view<LinearLayout>(R.id.desk_watch_rows).removeAllViews()
    }

    private fun clearTokenList() {
        if (adapter.rows.isEmpty()) return
        adapter.rows = emptyList()
        adapter.notifyDataSetChanged()
    }

    private fun listTokens() {
        if (tab != 1) return
        val search = query.text.toString().trim()
        val stars = store.favorites
        val matches = snapshot.rows.filter { row ->
            row.contract.symbol.contains(search, ignoreCase = true) && when (filter.selectedItemPosition) {
                1 -> (row.ticker?.change ?: 0.0) > 0
                2 -> (row.ticker?.change ?: 0.0) < 0
                3 -> row.contract.symbol in stars
                4 -> fresh && (row.score ?: 0.5) > 0.57
                5 -> fresh && (row.score ?: 0.5) < 0.43
                else -> true
            }
        }
        adapter.rows = when (sort.selectedItemPosition) {
            1 -> matches.sortedByDescending { it.ticker?.change ?: Double.NEGATIVE_INFINITY }
            2 -> matches.sortedBy { it.ticker?.change ?: Double.POSITIVE_INFINITY }
            3 -> matches.sortedBy { it.contract.symbol }
            else -> matches.sortedByDescending { it.ticker?.volume ?: -1.0 }
        }
        adapter.stars = stars
        adapter.notifyDataSetChanged()
        view<TextView>(R.id.scan_count).text = when {
            snapshot.rows.isEmpty() -> "Menunggu daftar kontrak dari bursa"
            matches.isEmpty() -> "Tidak ada kontrak yang cocok"
            else -> "${matches.size} dari ${snapshot.rows.size} kontrak · perubahan 24 jam"
        }
    }

    private fun showToken(row: MarketRow) {
        val t = row.ticker
        val symbol = row.contract.symbol
        val starred = symbol in store.favorites
        val info = if (t == null) "Ticker belum tersedia dari bursa." else buildString {
            if (!fresh || !t.fresh(snapshot.time)) append("Data terakhir · kedaluwarsa\n")
            append("Harga   ${price(t.price)} ${row.contract.quote}\nPerubahan 24j   ${pct(t.change)}\n")
            append("Terendah   ${price(t.low)}\nTertinggi   ${price(t.high)}\n")
            append("Volume 24j   ${money(t.volume)} ${row.contract.quote}\n")
            append("Pembaruan   ${time(t.time)}\n\n")
            if (fresh && t.fresh(snapshot.time) && row.score != null) {
                append("Model lokal · ${modelLabel(row.score)}\nSkor arah naik   ${money(100 * row.score)}/100")
            } else append("Skor model menunggu data mutakhir dan pelatihan.")
        }
        AlertDialog.Builder(activity).setTitle(symbol).setMessage(info)
            .setPositiveButton("Tutup", null)
            .setNeutralButton(if (starred) "Hapus favorit" else "Tambah favorit") { _, _ ->
                store.favorites = if (starred) store.favorites - symbol else store.favorites + symbol
                if (tab == 0) renderTerminal(SystemClock.elapsedRealtime()) else listTokens()
            }.show()
    }

    private fun renderAccount() {
        val (model, account) = store.read()
        val position = account.position
        val t = snapshot.rows.firstOrNull { it.contract.symbol == position?.symbol }?.ticker
        val mark = t?.takeIf { fresh && it.fresh(snapshot.time) }?.price
        val unrealized = if (position != null && mark != null) position.gross(mark) else null
        view<TextView>(R.id.paper_model).text = buildString {
            append("Regresi logistik · ${model.rounds} ronde belajar\n")
            append(if (model.rounds < MarketModel.WARMUP) "Pemanasan ${model.rounds}/${MarketModel.WARMUP}. " else "Model siap menilai arah. ")
            append("Entry virtual memakai skor model dan konfirmasi SMA 20 pada 1m dan 5m.\n")
            append("Risiko 0,5% · fee 0,05% dan slippage 0,02% per sisi · funding menurut bursa.")
        }
        view<TextView>(R.id.paper_open).text = if (position == null) "Belum ada posisi terbuka" else buildString {
            append("${if (position.side > 0) "LONG" else "SHORT"}  ${position.symbol} · ${money(position.quantity * position.entry)} USDT\nEntry   ${price(position.entry)}\n")
            append("SL ${price(position.stop)} · TP ${price(position.target)}\n")
            append("PnL berjalan   ${unrealized?.let(::money) ?: "menunggu harga"} USDT")
            if (!store.simulation) append("\nMenunggu harga valid untuk menutup posisi.")
        }
        view<TextView>(R.id.paper_journal).text = account.journal.joinToString("\n\n") { event ->
            val date = event.substringBefore('|').toLongOrNull()
            if (date == null) event else time(date) + "\n" + event.substringAfter('|')
        }.ifEmpty { "Transaksi simulasi akan tercatat di sini." }
        val equity = if (position == null) money(account.balance) else unrealized?.let { money(account.balance + it) }
        view<TextView>(R.id.account_wallet).text = "Saldo virtual   ${money(account.balance)} USDT\n" +
            "Ekuitas virtual   ${equity ?: "menunggu harga"}\nModal awal   1.000 USDT\nTransaksi selesai   ${account.trades}"
        view<TextView>(R.id.account_build).text = activity.getString(R.string.build_identity)
    }

    private inner class TokenAdapter : BaseAdapter() {
        var rows = emptyList<MarketRow>()
        var stars = emptySet<String>()
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell = convertView ?: activity.layoutInflater.inflate(R.layout.market_row, parent, false)
            val row = getItem(position)
            val t = row.ticker
            cell.findViewById<TextView>(R.id.row_symbol).text = (if (row.contract.symbol in stars) "★ " else "") + row.contract.symbol
            cell.findViewById<TextView>(R.id.row_model).text = when {
                t == null -> "Menunggu ticker"
                !fresh || !t.fresh(snapshot.time) -> "Data kedaluwarsa"
                else -> modelLabel(row.score)
            }
            cell.findViewById<TextView>(R.id.row_price).text = t?.let { price(it.price) } ?: "—"
            cell.findViewById<TextView>(R.id.row_change).apply {
                text = t?.let { pct(it.change) } ?: "—"
                setTextColor(when {
                    t == null || t.change == 0.0 -> Color.LTGRAY
                    t.change > 0 -> Color.rgb(126, 226, 182)
                    else -> Color.rgb(255, 154, 140)
                })
            }
            return cell
        }
    }
}
