package com.mefuturesaibot.app.demo

import android.content.Context
import com.mefuturesaibot.app.account.CredentialVault
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.SignalSide
import com.mefuturesaibot.app.scanner.BaselineDecision
import com.mefuturesaibot.app.scanner.Contract
import com.mefuturesaibot.app.scanner.MarketData
import com.mefuturesaibot.app.scanner.MarketRow
import com.mefuturesaibot.app.scanner.DeskStore
import com.mefuturesaibot.app.scanner.Levels
import com.mefuturesaibot.app.scanner.Quote
import com.mefuturesaibot.app.scanner.TradeLedger
import com.mefuturesaibot.app.scanner.TradeClose
import com.mefuturesaibot.app.scanner.RiskPlan
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
internal fun rounded(value: Double, step: BigDecimal, mode: RoundingMode): String =
    BigDecimal.valueOf(value).divide(step, 0, mode).multiply(step).stripTrailingZeros().toPlainString()

internal class DemoRunner(private val context: Context, private val venue: FuturesVenue = FuturesVenue.selected(context)) {
    private val live = venue == FuturesVenue.REAL
    private val mode = venue.label
    private val vault = CredentialVault(context, !live)
    private val prefs = venue.prefs(context)
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var lastBar = 0L
    private var retryAt = 0L
    @Volatile private var closing = false

    fun start() { worker.scheduleWithFixedDelay({ synchronized(lock) {
        if (!closing && System.currentTimeMillis() >= retryAt && (prefs.getBoolean("enabled", false) || prefs.contains("intent"))) {
            try { status(cycle()) } catch (e: Exception) {
                if (e is Rejection && e.code in listOf(-1003, 418, 429)) retryAt = System.currentTimeMillis() + 120_000
                status(if (e is Rejection) "WAIT · Binance $mode ${e.code}" else "WAIT · periksa koneksi, key $mode dan status order")
            }
        }
    } }, 0, 10, TimeUnit.SECONDS) }

    fun close() { closing = true; worker.shutdown() }
    private fun status(s: String) { prefs.edit().putString("status",s).apply() }
    private fun persist(intent: JSONObject) { check(prefs.edit().putString("intent", intent.toString()).commit()) }
    internal fun cycle(api: DemoExchange = DemoExchange(vault.read(), venue = venue)): String {
        if (FuturesVenue.selected(context) != venue) return "WAIT · mode telah berubah"
        require(api.venue == venue)
        val pending = prefs.getString("intent", null)?.let(::JSONObject)
        if (live && pending == null) {
            if (prefs.getBoolean("enabled",false)) prefs.edit().putBoolean("enabled",false).remove("consentKey").apply()
            return "Mainnet terkunci untuk entry baru selama riset V3"
        }
        if (live) {
            // Recovery must work after Stop, but only with the key that opened the position.
            val authorizedKey = pending?.getString("accountId") ?: prefs.getString("consentKey",null)
            if (authorizedKey != api.accountId) return "WAIT · akun belum disetujui atau key telah berubah"
            if (pending == null && !prefs.getBoolean("enabled",false)) return "Trading real nonaktif"
        }
        api.sync()
        if (pending != null) return reconcile(api, pending)
        val minute = api.now() / 60_000
        if (minute == prefs.getLong("entryMinute",-1)) return "WAIT · candle ini telah diproses"
        if (minute == lastBar) return prefs.getString("status","WAIT").orEmpty()
        lastBar = minute
        if (!api.oneWay()) return "WAIT · pilih One-way Mode di akun $mode khusus"
        if (api.positions().objects().any { it.getDouble("positionAmt") != 0.0 } ||
            api.orders().length() > 0 || api.algos().length() > 0) return "WAIT · ada posisi atau order lain"
        val account = api.account()
        val wallet = if (live) account.getJSONArray("assets").objects().single { it.getString("asset") == "USDT" } else account
        val equity = wallet.getDouble(if (live) "marginBalance" else "totalMarginBalance")
        val cap = if (live) prefs.getString("capital",null)?.toDoubleOrNull() ?: 0.0 else equity
        if (!cap.isFinite() || cap <= 0) return "WAIT · alokasi modal belum tersedia"
        val available = minOf(cap,equity,wallet.getDouble("availableBalance"))
        val today = api.now() / 86_400_000
        if (prefs.getLong("day",-1) != today) {
            check(prefs.edit().putLong("day",today).putString("start",equity.toString()).commit())
        }
        val anchor = prefs.getString("start",null)!!.toDouble()
        val remaining = equity - anchor + minOf(cap,anchor) * RiskPlan.DAY_LIMIT
        if (remaining <= 0 || prefs.getLong("haltDay",-1) == today) {
            check(prefs.edit().putLong("haltDay",today).commit())
            return "WAIT · batas rugi harian UTC"
        }
        val bars = CandleHistory(CandleHistory.parseClosedCandles(api.public("klines","symbol" to SYMBOL,
            "interval" to "1m","limit" to "121")), "Siap", true)
        val ticker = MarketData.tickers(JSONArray().put(JSONObject(api.public("ticker/24hr","symbol" to SYMBOL))).toString())[SYMBOL]
        val score = ticker?.let(DeskStore(context).read().first::score)
        val row = MarketRow(Contract(SYMBOL,"USDT"),ticker,score)
        fun decision() = BaselineDecision.evaluate(row, api.now(), bars.signalAt(api.now()))
        val signal = decision()
        if (signal.side == SignalSide.WAIT) return "WAIT · ${signal.reason}"
        val side = if (signal.side == SignalSide.BUY) 1 else -1
        val premium = JSONObject(api.public("premiumIndex","symbol" to SYMBOL))
        if (premium.getLong("nextFundingTime") - api.now() < 31 * 60_000) return "WAIT · mendekati funding"
        val filters = JSONObject(api.public("exchangeInfo")).getJSONArray("symbols").objects()
            .single { it.getString("symbol") == SYMBOL }.getJSONArray("filters").objects().associateBy { it.getString("filterType") }
        val tick = filters.getValue("PRICE_FILTER").getString("tickSize").toBigDecimal()
        val lot = filters.getValue("MARKET_LOT_SIZE")
        val fee = if (live) maxOf(RiskPlan.FEE,api.commission()) else RiskPlan.FEE
        val book = JSONObject(api.public("ticker/bookTicker","symbol" to SYMBOL))
        val quote = Quote(book.getDouble("bidPrice"),book.getDouble("askPrice"),book.getLong("time"))
        val structure = Levels.fromClosed(bars.candles)
        val levels = Levels(rounded(structure.support,tick,RoundingMode.FLOOR).toDouble(),
            rounded(structure.resistance,tick,RoundingMode.CEILING).toDouble())
        val plan = try { RiskPlan.build(side,quote,levels,minOf(available,remaining / RiskPlan.RISK),api.now(),fee) }
            catch (e: IllegalArgumentException) { return "WAIT · ${e.message}" }
        val qty = rounded(minOf(plan.quantity,lot.getDouble("maxQty")),lot.getString("stepSize").toBigDecimal(),RoundingMode.DOWN)
        if (qty.toDouble() < lot.getDouble("minQty") || qty.toDouble() * plan.entry < filters.getValue("MIN_NOTIONAL").getDouble("notional"))
            return "WAIT · ukuran di bawah minimum kontrak"
        if (closing || !prefs.getBoolean("enabled",false) || decision().side != signal.side || quote.problem(api.now()) != null)
            return "WAIT · konfirmasi kedaluwarsa atau $mode dimatikan"
        val id = (if (live) "meR" else "meD") + api.now()
        val intent = JSONObject().put("id",id).put("side",side).put("created",api.now())
            .put("stop",BigDecimal.valueOf(plan.stop).toPlainString()).put("target",BigDecimal.valueOf(plan.target).toPlainString())
            .put("qty",qty).put("budget",minOf(available * RiskPlan.RISK,remaining)).put("fee",fee)
        if (live) intent.put("accountId",api.accountId)
        synchronized(entryLock) {
            if (closing || FuturesVenue.selected(context) != venue || !prefs.getBoolean("enabled",false) ||
                live && (prefs.getString("consentKey",null) != api.accountId || prefs.getString("capital",null)?.toDoubleOrNull() != cap)) return "$mode nonaktif"
            check(prefs.edit().putString("intent",intent.toString()).putLong("entryMinute",minute).commit())
        }
        api.market(if (side == 1) "BUY" else "SELL",qty,id)
        return reconcile(api,intent)
    }

    private fun reconcile(api: DemoExchange, state: JSONObject): String {
        val id = state.getString("id")
        val side = state.getInt("side")
        val exitSide = if (side == 1) "SELL" else "BUY"
        val holdings = api.positions().objects().filter { it.getString("symbol") == SYMBOL && it.getDouble("positionAmt") != 0.0 }
        if (holdings.size > 1) return "WAIT · periksa posisi BTC pada akun khusus"
        val holding = holdings.singleOrNull()
        val amount = holding?.getDouble("positionAmt") ?: 0.0
        val algos = api.algos().objects()
        val owned = algos.filter { it.optString("clientAlgoId") in setOf(id+"s",id+"t") }
        if (amount == 0.0) {
            val order = try { api.query(id) } catch (e: Rejection) {
                if (e.code != -2013) throw e
                null
            }
            if (order == null) return "WAIT · status entry belum terbukti, ID dipertahankan"
            if (order.getString("status") !in terminal) {
                if (state.optBoolean("exit") || !prefs.getBoolean("enabled",false) || live)
                    api.cancelEntry(id)
                return "WAIT · menunggu status terminal entry"
            }
            if (order.optString("status") == "FILLED" && api.now() - state.getLong("created") < 10_000)
                return "WAIT · sinkronisasi posisi"
            owned.forEach { api.cancel(it.getString("clientAlgoId")) }
            if (owned.isNotEmpty() && api.algos().objects().any { it.optString("clientAlgoId") in setOf(id+"s",id+"t") })
                return "WAIT · pembatalan proteksi belum terverifikasi"
            if (order.getDouble("executedQty") > 0) return settle(api,state,order)
            check(prefs.edit().remove("intent").commit())
            return "$mode · posisi kosong dan proteksi diperiksa"
        }
        if (amount * side <= 0 || kotlin.math.abs(amount) > state.getString("qty").toDouble() * 1.0000001)
            return "WAIT · posisi $mode berubah di luar aplikasi, periksa akun"
        val fill = api.query(id)
        if (fill.getString("clientOrderId") != id || fill.getString("side") != (if (side == 1) "BUY" else "SELL") ||
            kotlin.math.abs(amount) > fill.getDouble("executedQty") * 1.0000001)
            return "WAIT · kepemilikan posisi BTC belum terverifikasi"
        val partial = fill.getString("status") !in terminal
        val entry = holding!!.getDouble("entryPrice")
        if (state.optDouble("entry") != entry || state.optDouble("heldQty") != kotlin.math.abs(amount)) {
            state.put("entry", entry).put("heldQty", kotlin.math.abs(amount))
            persist(state)
        }
        val stopFill = state.getDouble("stop") * (1 - side * RiskPlan.SLIP)
        val targetFill = state.getDouble("target") * (1 - side * RiskPlan.SLIP)
        val fee = state.optDouble("fee",RiskPlan.FEE)
        val loss = side * (entry - stopFill) + fee * (entry + stopFill)
        val gain = side * (targetFill - entry) - fee * (entry + targetFill)
        val liquidation = holding.optDouble("liquidationPrice",0.0)
        val liquidationBeforeStop = live && liquidation > 0 && side * (stopFill - liquidation) <= 0
        val fillOutsidePlan = liquidationBeforeStop || loss <= 0 || gain < 2 * loss || loss * kotlin.math.abs(amount) > state.getDouble("budget")
        if (fillOutsidePlan) return stopAfterFailure(api,state,amount,exitSide)
        val emergency = state.optBoolean("exit") || !prefs.getBoolean("enabled",false) ||
            api.now() - state.getLong("created") >= 30 * 60_000
        if (!emergency || partial) {
            for ((suffix, field, type) in listOf(Triple("s","stop","STOP_MARKET"), Triple("t","target","TAKE_PROFIT_MARKET"))) {
                val already = owned.firstOrNull { it.optString("clientAlgoId") == id+suffix }
                if (already != null && already.optString("side") == exitSide && already.optString("orderType") == type &&
                    already.optBoolean("closePosition") && already.optString("workingType") == "MARK_PRICE" &&
                    already.optDouble("triggerPrice") == state.getString(field).toDouble()) continue
                if (already != null || state.optBoolean("sent"+suffix)) {
                    // An acknowledged trigger disappeared while a position remains.
                    return stopAfterFailure(api,state,amount,exitSide)
                }
                try {
                    state.put("sent"+suffix,true)
                    persist(state)
                    api.protect(exitSide,state.getString(field),id+suffix,type)
                } catch (e: Rejection) {
                    return stopAfterFailure(api,state,amount,exitSide)
                }
            }
            val verified = api.algos().objects().filter { it.optString("clientAlgoId") in setOf(id+"s",id+"t") }
            val protected = listOf(Triple("s","stop","STOP_MARKET"), Triple("t","target","TAKE_PROFIT_MARKET")).all { (suffix,field,type) ->
                verified.any { it.optString("clientAlgoId") == id+suffix && it.optString("orderType") == type &&
                    it.optString("side") == exitSide && it.optBoolean("closePosition") &&
                    it.optString("workingType") == "MARK_PRICE" && it.optDouble("triggerPrice") == state.getDouble(field) }
            }
            if (!protected) return stopAfterFailure(api,state,amount,exitSide)
            if (partial) {
                api.cancelEntry(id)
                return "WAIT · fill parsial diproteksi, memeriksa sisa entry"
            }
            return "$mode · ${if (side == 1) "LONG" else "SHORT"} BTC · SL ${state.getString("stop")} · TP ${state.getString("target")}"
        }
        return flatten(api,state,amount,exitSide)
    }

    private fun settle(api: DemoExchange, state: JSONObject, order: JSONObject): String {
        val from = state.getLong("created")
        val end = api.now()
        val edit = prefs.edit().remove("intent")
        fun record(at: Long, net: Double?, reason: String) {
            val ledger = prefs.getString("closedLedger",null)?.let { TradeLedger.read(JSONObject(it)) }
                ?: TradeLedger(incomplete = prefs.getInt("n",0) + prefs.getInt("unrated",0) > 0)
            edit.putString("closedLedger",ledger.add(TradeClose(at,SYMBOL,net,reason)).json().toString())
        }
        if (end - from > 6 * 86_400_000L) {
            record(end, null, "Rekonsiliasi tertunda · waktu pemeriksaan")
            check(edit.putInt("unrated",prefs.getInt("unrated",0)+1).commit())
            return "$mode · posisi ditutup, riwayat panjang belum dinilai"
        }
        val fills = api.tradeHistory(from,end).objects()
        val funding = api.fundingIncome(from,end).objects()
        val buy = fills.filter { it.getString("side") == "BUY" }.sumOf { it.getDouble("qty") }
        val sell = fills.filter { it.getString("side") == "SELL" }.sumOf { it.getDouble("qty") }
        val quantity = order.getDouble("executedQty")
        if (fills.isEmpty() || buy < quantity - 1e-10 || sell < quantity - 1e-10)
            return "WAIT · menunggu riwayat fill Binance $mode"
        if (kotlin.math.abs(buy-quantity) > 1e-10 || kotlin.math.abs(sell-quantity) > 1e-10 ||
            fills.size == 1000 || funding.size == 1000 || fills.any { it.getString("commissionAsset") != "USDT" } ||
            funding.any { it.getString("asset") != "USDT" }) {
            record(end, null, "Rekonsiliasi tertunda · waktu pemeriksaan")
            check(edit.putInt("unrated",prefs.getInt("unrated",0)+1).commit())
            return "$mode · hasil transaksi perlu rekonsiliasi manual"
        }
        val net = fills.sumOf { it.getDouble("realizedPnl") - it.getDouble("commission") } + funding.sumOf { it.getDouble("income") }
        val closeSide = if (state.getInt("side") == 1) "SELL" else "BUY"
        val closedAt = fills.filter { it.getString("side") == closeSide }.maxOfOrNull { it.optLong("time") } ?: 0
        record(closedAt.takeIf { it > 0 } ?: end, net, if (closedAt > 0) "Fill bursa" else "Waktu pemeriksaan")
        val pnl = prefs.getString("net","0")!!.toDouble() + net
        val profit = prefs.getString("profit","0")!!.toDouble() + maxOf(net,0.0)
        val loss = prefs.getString("loss","0")!!.toDouble() + maxOf(-net,0.0)
        val peak = maxOf(prefs.getString("peak","0")!!.toDouble(),pnl)
        val dd = maxOf(prefs.getString("maxDD","0")!!.toDouble(),peak-pnl)
        val n = prefs.getInt("n",0)+1
        val wins = prefs.getInt("wins",0)+(if (net > 0) 1 else 0)
        val losing = prefs.getInt("losing",0)+(if (net < 0) 1 else 0)
        check(edit.putInt("n",n).putInt("wins",wins).putInt("losing",losing).putString("net",pnl.toString())
            .putString("profit",profit.toString()).putString("loss",loss.toString()).putString("peak",peak.toString())
            .putString("maxDD",dd.toString()).commit())
        return "$mode · posisi ditutup, hasil bersih tercatat"
    }

    private fun stopAfterFailure(api: DemoExchange, state: JSONObject, amount: Double, exitSide: String): String {
        // Latch persists across app restarts. Re-arming is possible only once the position is reconciled.
        state.put("exit",true)
        check(prefs.edit().putBoolean("enabled",false).putString("intent",state.toString()).commit())
        flatten(api,state,amount,exitSide)
        return "$mode dijeda · proteksi atau fill di luar rencana · menunggu konfirmasi penutupan"
    }

    private fun flatten(api: DemoExchange, state: JSONObject, amount: Double, exitSide: String): String {
        val entry = api.query(state.getString("id"))
        if (entry.getString("status") !in terminal) {
            api.cancelEntry(state.getString("id"))
            return "WAIT · entry dibatalkan sebelum menutup sisa posisi"
        }
        val previous = state.optString("exitId")
        if (previous.isNotEmpty()) {
            val old = try { api.query(previous) } catch (e: Rejection) {
                if (e.code != -2013) throw e
                return "WAIT · status exit ambigu, periksa ID exit di bursa"
            }
            if (old.getString("clientOrderId") != previous || old.getString("status") !in terminal)
                return "WAIT · exit sebelumnya belum terminal"
            val before = state.getDouble("exitAmount")
            val filled = old.getDouble("executedQty")
            if (!filled.isFinite() || filled < 0 || kotlin.math.abs(amount) > before - filled + 1e-10)
                return "WAIT · menunggu saldo posisi setelah exit"
        }
        val attempt = state.optInt("exitAttempt") + 1
        val exitId = state.getString("id") + "x" + attempt
        state.put("exit",true).put("exitId",exitId).put("exitAttempt",attempt).put("exitAmount",kotlin.math.abs(amount))
        persist(state)
        api.market(exitSide,BigDecimal.valueOf(kotlin.math.abs(amount)).stripTrailingZeros().toPlainString(),exitId,true)
        return "$mode · menutup posisi, menunggu konfirmasi bursa"
    }

    companion object {
        private val terminal = setOf("FILLED","CANCELED","EXPIRED","REJECTED","EXPIRED_IN_MATCH")
        internal val entryLock = Any()
        private val lock = Any()
        const val PREFS = "me_project_futures_demo_v1"
        private const val SYMBOL = DemoExchange.SYMBOL
    }
}
