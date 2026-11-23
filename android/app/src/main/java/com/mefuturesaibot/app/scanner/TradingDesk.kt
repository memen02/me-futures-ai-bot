package com.mefuturesaibot.app.scanner

import android.content.SharedPreferences
import android.widget.RadioGroup
import com.mefuturesaibot.app.demo.DemoControls
import com.mefuturesaibot.app.demo.FuturesVenue
import android.app.Activity
import com.mefuturesaibot.app.TokenDetailActivity
import com.mefuturesaibot.app.AssetLogo
import com.mefuturesaibot.app.exchange.TokenSignals
import com.mefuturesaibot.app.exchange.SmaSignal
import com.mefuturesaibot.app.exchange.SignalSide
import android.content.res.ColorStateList
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
internal class TradingDesk(
    private val activity: Activity, initialTab: DeskTab, private val start: () -> Unit,
) {
    private fun <T : View> view(id: Int): T = activity.findViewById(id)
    private val store = DeskStore(activity)
    private var demo = DemoControls(activity, start)
    private val accountPage = AccountPage(activity, credentialsChanged = { demo.render() })
    private val query = view<EditText>(R.id.scan_query)
    private val filter = view<Spinner>(R.id.scan_filter)
    private val sort = view<Spinner>(R.id.scan_sort)
    private val adapter = TokenAdapter()
    private var snapshot = Scan()
    private var watchMode = if (store.favorites.isEmpty()) 1 else 0
    private val modes = listOf("Favorit", "Volume", "Naik", "Turun", "Baru")
    private val signals = TokenSignals { symbol, signal ->
        visibleCells().filter { it.tag == symbol }.forEach { badge(it, signal) }
    }
    private var nextTerminalRefresh = Long.MAX_VALUE
    private var fresh = false
    private var running = false
    var onTabSelected: (() -> Unit)? = null
    var tab = DeskTab.HOME
        private set

    private var changingMode = false
    private val modeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "venue") renderMode()
    }

    init {
        DeskTab.values().forEach { dest -> view<TextView>(dest.buttonId).setOnClickListener { select(dest) } }
        view<Button>(R.id.desk_history).setOnClickListener { select(DeskTab.HISTORY) }
        view<Button>(R.id.scan_start).setOnClickListener { start() }
        view<Button>(R.id.home_market).setOnClickListener { openMarkets() }
        val filters = view<LinearLayout>(R.id.watch_filters)
        modes.forEachIndexed { index, name ->
            val button = Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
                text = name
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setOnClickListener {
                    watchMode = index
                    renderTerminal(SystemClock.elapsedRealtime())
                }
            }
            filters.addView(button)
        }
        val list = view<ListView>(R.id.scan_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ -> showToken(adapter.getItem(i)) }
        configure(filter, listOf("Semua", "Naik 24j", "Turun 24j", "Favorit", "Bias naik", "Bias turun"))
        configure(sort, listOf("Volume", "% tertinggi", "% terendah", "Simbol A–Z", "Baru terdaftar"))
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
        FuturesVenue.settings(activity).registerOnSharedPreferenceChangeListener(modeListener)
        view<View>(R.id.mode_mainnet).isEnabled = com.mefuturesaibot.app.BuildConfig.MAINNET_ORDERS
        view<RadioGroup>(R.id.venue_mode).setOnCheckedChangeListener { _, id ->
            if (!changingMode) {
                val target = if (id == R.id.mode_mainnet) FuturesVenue.REAL else FuturesVenue.DEMO
                val ok = FuturesVenue.select(activity,target)
                renderMode()
                if (!ok) view<TextView>(R.id.mode_message).apply {
                    text = "Nonaktifkan trading di mode aktif dan tunggu sampai posisi serta order bot selesai diperiksa."
                    visibility = View.VISIBLE
                }
            }
        }
        renderMode()
        select(initialTab)
    }

    private fun renderMode() {
        val mode = FuturesVenue.selected(activity)
        if (mode != demo.venue) {
            accountPage.show(false)
            demo.close()
            demo = DemoControls(activity,start)
            select(tab)
        }
        changingMode = true
        view<RadioGroup>(R.id.venue_mode).check(if (mode == FuturesVenue.REAL) R.id.mode_mainnet else R.id.mode_demo)
        changingMode = false
        view<TextView>(R.id.active_venue).apply {
            text = if (mode == FuturesVenue.REAL) "MAINNET · Uang asli" else "DEMO · Saldo virtual"
            setTextColor(if (mode == FuturesVenue.REAL) activity.getColor(R.color.me_accent) else activity.getColor(R.color.me_text))
        }
        view<View>(R.id.mode_message).visibility = View.GONE
    }

    fun close() {
        FuturesVenue.settings(activity).unregisterOnSharedPreferenceChangeListener(modeListener)
        signals.stop()
        demo.close()
        accountPage.close()
    }

    fun openMarkets() {
        query.setText("")
        filter.setSelection(0)
        select(DeskTab.MARKET)
        query.requestFocus()
    }

    fun select(destination: DeskTab) {
        if (tab == DeskTab.MARKET && destination != tab) clearTokenList()
        tab = destination
        accountPage.show(tab == DeskTab.ACCOUNT)
        DeskTab.values().forEach { dest ->
            view<View>(dest.pageId).visibility = if (dest == tab) View.VISIBLE else View.GONE
            val button = view<TextView>(dest.buttonId)
            val tint = if (dest == tab) activity.getColor(R.color.me_accent) else activity.getColor(R.color.me_muted)
            button.isSelected = dest == tab
            button.setTextColor(tint)
            button.compoundDrawableTintList = ColorStateList.valueOf(tint)
        }
        if (tab == DeskTab.ACCOUNT) {
            view<Switch>(R.id.account_autostart).isChecked = BotPreferences.isAutoStartEnabled(activity)
            view<TextView>(R.id.account_cache_status).text = CacheCleanupJob.status(activity)
        }
        update(BotForegroundService.latestScan, BotForegroundService.isRunning, force = true)
        onTabSelected?.invoke()
    }

    fun update(scan: Scan, active: Boolean, force: Boolean = false) {
        val elapsed = SystemClock.elapsedRealtime()
        val current = scan.fresh(elapsed)
        if (!force && snapshot === scan && current == fresh && running == active &&
            (tab != DeskTab.HOME || elapsed < nextTerminalRefresh)) return
        snapshot = scan
        fresh = current
        running = active
        val status = when {
            !active -> "Bot berhenti · ${scan.status}"
            scan.ok && !current -> "Data scanner kedaluwarsa"
            else -> scan.status
        }
        val last = if (scan.time > 0) " · ${time(scan.time)}" else ""
        view<TextView>(R.id.scan_state).text = status + last
        view<Button>(R.id.scan_start).visibility = if (active) View.GONE else View.VISIBLE
        view<Button>(R.id.scan_stop).visibility = if (active) View.VISIBLE else View.GONE
        if (tab == DeskTab.HOME) renderTerminal(elapsed)
        if (tab == DeskTab.MARKET) listTokens()
        if (tab == DeskTab.TRADING || tab == DeskTab.HISTORY || tab == DeskTab.ACCOUNT) renderAccount()
    }

    private fun renderTerminal(elapsed: Long) {
        val marketTime = snapshot.time + (elapsed - snapshot.received)
        val rows = if (fresh) snapshot.rows.filter { it.ticker?.fresh(marketTime) == true } else emptyList()
        nextTerminalRefresh = rows.minOfOrNull { elapsed + (it.ticker!!.time + 120_001 - marketTime) } ?: Long.MAX_VALUE
        val execution = demo.venue.prefs(activity)
        val state = when {
            execution.contains("intent") -> "Posisi/order dipantau"
            running && execution.getBoolean("enabled",false) -> "Trading aktif"
            else -> "Trading nonaktif"
        }
        view<TextView>(R.id.desk_bot).text = "${demo.venue.label} · $state"
        val rising = rows.count { it.ticker!!.change > 0 }
        val falling = rows.count { it.ticker!!.change < 0 }
        val flat = rows.size - rising - falling
        view<View>(R.id.breadth_bar).visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        listOf(R.id.breadth_up to rising, R.id.breadth_flat to flat, R.id.breadth_down to falling).forEach { (id, count) ->
            val bar = view<View>(id)
            val params = bar.layoutParams as LinearLayout.LayoutParams
            params.weight = count.toFloat()
            bar.layoutParams = params
        }
        view<TextView>(R.id.desk_coverage).text = if (rows.isEmpty()) "Menunggu data pasar" else
            "↑ $rising naik     ·     $flat datar     ·     ↓ $falling turun"
        renderWatch(rows)
    }

    private fun renderPerformance(account: PaperAccount) {
        val net = account.balance - PaperAccount.INITIAL_BALANCE
        view<TextView>(R.id.desk_profit).apply {
            text = "Perubahan saldo  ${money(net)} USDT"
            setTextColor(when {
                net > 0 -> activity.getColor(R.color.me_buy)
                net < 0 -> activity.getColor(R.color.me_sell)
                else -> activity.getColor(R.color.me_text)
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
        val selected = when (watchMode) {
            0 -> rows.filter { it.contract.symbol in stars }.sortedBy { it.contract.symbol }
            2 -> rows.filter { it.ticker!!.change > 0 }.sortedByDescending { it.ticker!!.change }
            3 -> rows.filter { it.ticker!!.change < 0 }.sortedBy { it.ticker!!.change }
            4 -> rows.filter { it.contract.listedAt > 0 }.sortedByDescending { it.contract.listedAt }
            else -> rows.filter { it.contract.quote == "USDT" }.sortedByDescending { it.ticker!!.volume }
        }.take(10)
        val filters = view<LinearLayout>(R.id.watch_filters)
        for (i in modes.indices) (filters.getChildAt(i) as Button).apply {
            isSelected = i == watchMode
            setTextColor(activity.getColor(if (isSelected) R.color.me_accent else R.color.me_muted))
        }
        view<TextView>(R.id.desk_watch_source).text = when {
            rows.isEmpty() -> snapshot.status
            selected.isEmpty() && watchMode == 0 -> "Buka token di Pasar lalu ketuk bintang."
            selected.isEmpty() -> "Belum ada kontrak yang sesuai"
            else -> "Kontrak / SMA 1m + 5m                         Harga / 24j"
        }
        val list = view<LinearLayout>(R.id.desk_watch_rows)
        list.removeAllViews()
        for (row in selected) {
            val cell = activity.layoutInflater.inflate(R.layout.market_row, list, false)
            bind(cell, row)
            cell.isFocusable = true
            cell.setOnClickListener { showToken(row) }
            list.addView(cell)
        }
    }

    private fun visibleCells(): List<View> {
        val parent: ViewGroup = when (tab) {
            DeskTab.HOME -> view(R.id.desk_watch_rows)
            DeskTab.MARKET -> view(R.id.scan_list)
            else -> return emptyList()
        }
        return (0 until parent.childCount).map(parent::getChildAt)
    }

    fun refreshSignals() {
        val cells = visibleCells()
        val symbols = cells.mapNotNull { it.tag as? String }
        signals.watch(if (fresh) symbols else emptyList())
        cells.forEach { cell -> (cell.tag as? String)?.let { badge(cell, signals.signal(it)) } }
    }

    private fun badge(cell: View, signal: SmaSignal) {
        val side = if (fresh && cell.isEnabled) signal.side else SignalSide.WAIT
        cell.findViewById<TextView>(R.id.row_model).apply {
            text = side.name
            setTextColor(activity.getColor(when (side) {
                SignalSide.BUY -> R.color.me_buy
                SignalSide.SELL -> R.color.me_sell
                SignalSide.WAIT -> R.color.me_muted
            }))
        }
    }

    private fun bind(cell: View, row: MarketRow) {
        val c = row.contract
        val t = row.ticker
        val current = fresh && t?.fresh(snapshot.time + SystemClock.elapsedRealtime() - snapshot.received) == true
        cell.tag = c.symbol.takeIf { current }
        AssetLogo.bind(cell, c.base)
        val star = if (c.symbol in store.favorites) "★ " else ""
        cell.findViewById<TextView>(R.id.row_symbol).text = "$star${c.base}/${c.quote}"
        badge(cell, if (current) signals.signal(c.symbol) else SmaSignal.waitFor("Menunggu ticker"))
        cell.findViewById<TextView>(R.id.row_price).text = t?.let { price(it.price) } ?: "—"
        cell.findViewById<TextView>(R.id.row_change).apply {
            text = if (!current) "Belum mutakhir" else t?.let { pct(it.change) }
            setTextColor(activity.getColor(when {
                !current || t == null || t.change == 0.0 -> R.color.me_muted
                t.change > 0 -> R.color.me_buy
                else -> R.color.me_sell
            }))
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
        signals.stop()
        view<LinearLayout>(R.id.desk_watch_rows).removeAllViews()
    }

    private fun clearTokenList() {
        if (adapter.rows.isEmpty()) return
        adapter.rows = emptyList()
        adapter.notifyDataSetChanged()
    }

    private fun listTokens() {
        if (tab != DeskTab.MARKET) return
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
            4 -> matches.sortedByDescending { it.contract.listedAt }
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
        activity.startActivityForResult(TokenDetailActivity.intent(activity, row.contract), TokenDetailActivity.OPEN)
    }

    private fun renderAccount() {
        val (model, account) = store.read()
        if (tab == DeskTab.ACCOUNT) renderPerformance(account)
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
            bind(cell, getItem(position))
            return cell
        }
    }
}
