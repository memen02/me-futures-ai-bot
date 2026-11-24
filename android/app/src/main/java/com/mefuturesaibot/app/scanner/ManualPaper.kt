package com.mefuturesaibot.app.scanner

import android.app.Activity
import com.mefuturesaibot.app.security.UiAccess
import android.app.AlertDialog
import android.os.SystemClock
import android.widget.*
import android.text.InputType
import com.mefuturesaibot.app.exchange.PublicReader
import org.json.JSONObject

/** Local simulated fills only. The public quote and risk budget are checked again before committing. */
internal class ManualPaper(private val activity: Activity, private val store: DeskStore,
    private val market: () -> Scan, private val updated: () -> Unit) {
    private val reader = PublicReader()
    private var dialog: AlertDialog? = null
    private fun message(s: String) { Toast.makeText(activity,s,Toast.LENGTH_LONG).show() }
    fun open() {
        if (!UiAccess.allowed(activity)) return
        val session = UiAccess.session()
        close()
        val scan = market()
        if (!scan.fresh(SystemClock.elapsedRealtime())) { message("Menunggu daftar kontrak mutakhir"); return }
        if (store.read().second.position != null) { message("Masih ada satu posisi Paper terbuka"); return }
        val symbols = scan.rows.filter { it.contract.quote == "USDT" }.map { it.contract.symbol }.sorted()
        val form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        val token = AutoCompleteTextView(activity).apply {
            hint = "Simbol, mis. ETHUSDT"; threshold = 1; isSingleLine = true
            setAdapter(ArrayAdapter(activity,android.R.layout.simple_dropdown_item_1line,symbols))
            setOnClickListener { showDropDown() }
        }
        val side = Spinner(activity).apply { adapter = ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,listOf("LONG","SHORT")) }
        fun field(title: String) = EditText(activity).apply { hint = title; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val sl = field("Stop loss"); val tp = field("Take profit")
        listOf(token,side,sl,tp).forEach { form.addView(it) }
        dialog = AlertDialog.Builder(activity).setTitle("Buka posisi Paper").setView(form).setNegativeButton("Batal",null)
            .setPositiveButton("Periksa harga",null).show().apply {
                UiAccess.protect(activity, this)
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (!UiAccess.allowed(activity, session)) {
                        dismiss()
                        return@setOnClickListener
                    }
                    val symbol = token.text.toString().trim().uppercase()
                    val stop = sl.text.toString().replace(',','.').toDoubleOrNull()
                    val target = tp.text.toString().replace(',','.').toDoubleOrNull()
                    if (symbol !in symbols) token.error = "Pilih kontrak USDT aktif"
                    else if (stop == null || stop <= 0) sl.error = "Masukkan harga SL"
                    else if (target == null || target <= 0) tp.error = "Masukkan harga TP"
                    else {
                        val direction = if (side.selectedItemPosition == 0) 1 else -1
                        dismiss()
                        prepare(symbol,direction,if(direction>0) Levels(stop,target) else Levels(target,stop),session)
                    }
                }
            }
    }
    private fun prepare(symbol: String, side: Int, levels: Levels, session: Long) {
        if (!UiAccess.allowed(activity, session)) return
        val escaped = PublicReader.encode(symbol)
        reader.read("premiumIndex?symbol=$escaped") funding@{ raw, _, error ->
            if (!UiAccess.allowed(activity, session)) return@funding
            val premium = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (premium?.optString("symbol") != symbol) { message(error.ifBlank { "Funding belum tersedia" }); return@funding }
            val nextFunding = premium.optLong("nextFundingTime")
            reader.read("ticker/bookTicker?symbol=$escaped") quote@{ body, time, problem ->
                if (!UiAccess.allowed(activity, session)) return@quote
                if (body == null || time == null) { message(problem.ifBlank { "Kuotasi belum tersedia" }); return@quote }
                val result = runCatching {
                    val data = JSONObject(body); require(data.getString("symbol")==symbol)
                    val q = Quote(data.getDouble("bidPrice"),data.getDouble("askPrice"),data.getLong("time"))
                    require(nextFunding > time + 31 * 60_000L) { "Mendekati funding" }
                    val account = store.read().second
                    require(account.position == null && !account.locked(time)) { "Batas posisi atau rugi harian tercapai" }
                    q to RiskPlan.build(side,q,levels,account.balance,time)
                }
                val pair = result.getOrElse { message(it.message ?: "Rencana tidak valid"); return@quote }
                val (q,plan) = pair
                val received = SystemClock.elapsedRealtime()
                dialog = AlertDialog.Builder(activity).setTitle("Konfirmasi Paper · $symbol").setMessage(
                    "${if(side>0) "LONG" else "SHORT"} · Entry ${plan.entry}\nSL ${plan.stop} · TP ${plan.target}\nJumlah ${plan.quantity}\nRisiko maksimal 0,5% · Kuotasi berlaku 5 detik.")
                    .setNegativeButton("Batal",null).setPositiveButton("Buka Paper") { _,_ ->
                        if (!UiAccess.allowed(activity, session)) {
                            message("Sesi berakhir. Ulangi transaksi.")
                            return@setPositiveButton
                        }
                        val now = time + SystemClock.elapsedRealtime() - received
                        val (model,current) = store.read()
                        val live = market()
                        if (q.problem(now) != null || current.position != null || current.locked(now) ||
                            !live.fresh(SystemClock.elapsedRealtime()) || live.rows.none { it.contract.symbol == symbol }) {
                            message("Harga atau status akun berubah. Periksa ulang."); return@setPositiveButton
                        }
                        val confirmed = runCatching { RiskPlan.build(side,q,levels,current.balance,now) }.getOrNull()
                        if (confirmed != plan) { message("Rencana berubah. Periksa ulang."); return@setPositiveButton }
                        val account = current.advance(live.copy(time = now),symbol,plan,true,q,
                            trailing = store.trailing,manual = true)
                        if (account.position == null) message("Menunggu waktu bursa berikutnya") else {
                            store.save(model,account)
                            updated()
                        }
                    }.show().also {
                        UiAccess.protect(activity, it)
                    }
            }
        }
    }
    fun close() { reader.cancel(); dialog?.dismiss(); dialog = null }
}
