package com.mefuturesaibot.app.demo

import android.content.Context
import com.mefuturesaibot.app.account.CredentialVault
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.SignalSide
import com.mefuturesaibot.app.scanner.Levels
import com.mefuturesaibot.app.scanner.Quote
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

internal class DemoRunner(context: Context) {
    private val vault = CredentialVault(context, true)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var lastBar = 0L
    private var retryAt = 0L
    @Volatile private var closing = false

    fun start() { worker.scheduleWithFixedDelay({ synchronized(lock) {
        if (!closing && System.currentTimeMillis() >= retryAt && (prefs.getBoolean("enabled", false) || prefs.contains("intent"))) {
            try { status(cycle()) } catch (e: Exception) {
                if (e is Rejection && e.code in listOf(-1003, 418, 429)) retryAt = System.currentTimeMillis() + 120_000
                status(if (e is Rejection) "WAIT · Binance Demo ${e.code}" else "WAIT · periksa koneksi, key demo dan status order")
            }
        }
    } }, 0, 10, TimeUnit.SECONDS) }

    fun close() { closing = true; worker.shutdown() }
    private fun status(s: String) { prefs.edit().putString("status",s).apply() }
    private fun persist(intent: JSONObject) { check(prefs.edit().putString("intent", intent.toString()).commit()) }
    internal fun cycle(api: DemoExchange = DemoExchange(vault.read())): String {
        api.sync()
        val existing = prefs.getString("intent", null)?.let(::JSONObject)
        if (existing != null) return reconcile(api, existing)
        val minute = api.now() / 60_000
        if (minute == lastBar) return prefs.getString("status","WAIT").orEmpty()
        lastBar = minute
        if (!api.oneWay()) return "WAIT · pilih One-way Mode di akun demo khusus"
        if (api.positions().objects().any { it.getDouble("positionAmt") != 0.0 } ||
            api.orders().length() > 0 || api.algos().length() > 0) return "WAIT · ada posisi atau order lain"
        val account = api.account()
        val equity = account.getDouble("totalMarginBalance")
        val today = api.now() / 86_400_000
        if (prefs.getLong("day",-1) != today) {
            check(prefs.edit().putLong("day",today).putString("start",equity.toString()).commit())
        }
        val anchor = prefs.getString("start",null)!!.toDouble()
        if (equity <= anchor * (1 - RiskPlan.DAY_LIMIT)) return "WAIT · batas rugi harian demo"
        val bars = CandleHistory(CandleHistory.parseClosedCandles(api.public("klines","symbol" to SYMBOL,
            "interval" to "1m","limit" to "121")), "Siap", true)
        val signal = bars.signalAt(api.now())
        if (signal.side == SignalSide.WAIT) return "WAIT · ${signal.reason}"
        val side = if (signal.side == SignalSide.BUY) 1 else -1
        val premium = JSONObject(api.public("premiumIndex","symbol" to SYMBOL))
        if (premium.getLong("nextFundingTime") - api.now() < 31 * 60_000) return "WAIT · mendekati funding"
        val filters = JSONObject(api.public("exchangeInfo")).getJSONArray("symbols").objects()
            .single { it.getString("symbol") == SYMBOL }.getJSONArray("filters").objects().associateBy { it.getString("filterType") }
        val tick = filters.getValue("PRICE_FILTER").getString("tickSize").toBigDecimal()
        val lot = filters.getValue("MARKET_LOT_SIZE")
        val book = JSONObject(api.public("ticker/bookTicker","symbol" to SYMBOL))
        val quote = Quote(book.getDouble("bidPrice"),book.getDouble("askPrice"),book.getLong("time"))
        val structure = Levels.fromClosed(bars.candles)
        val levels = Levels(rounded(structure.support,tick,RoundingMode.FLOOR).toDouble(),
            rounded(structure.resistance,tick,RoundingMode.CEILING).toDouble())
        val plan = try { RiskPlan.build(side,quote,levels,minOf(equity,account.getDouble("availableBalance")),api.now()) }
            catch (e: IllegalArgumentException) { return "WAIT · ${e.message}" }
        val qty = rounded(minOf(plan.quantity,lot.getDouble("maxQty")),lot.getString("stepSize").toBigDecimal(),RoundingMode.DOWN)
        if (qty.toDouble() < lot.getDouble("minQty") || qty.toDouble() * plan.entry < filters.getValue("MIN_NOTIONAL").getDouble("notional"))
            return "WAIT · ukuran di bawah minimum kontrak"
        if (closing || !prefs.getBoolean("enabled",false) || bars.signalAt(api.now()).side != signal.side || quote.problem(api.now()) != null)
            return "WAIT · konfirmasi kedaluwarsa atau demo dimatikan"
        val id = "meD${api.now()}"
        val intent = JSONObject().put("id",id).put("side",side).put("created",api.now())
            .put("stop",BigDecimal.valueOf(plan.stop).toPlainString()).put("target",BigDecimal.valueOf(plan.target).toPlainString())
            .put("qty",qty).put("budget",equity * RiskPlan.RISK)
        synchronized(entryLock) {
            if (closing || !prefs.getBoolean("enabled",false)) return "Demo nonaktif"
            persist(intent)
        }
        api.market(if (side == 1) "BUY" else "SELL",qty,id)
        return reconcile(api,intent)
    }

    private fun reconcile(api: DemoExchange, state: JSONObject): String {
        val id = state.getString("id")
        val side = state.getInt("side")
        val exitSide = if (side == 1) "SELL" else "BUY"
        val holding = api.positions().objects().singleOrNull { it.getString("symbol") == SYMBOL && it.getDouble("positionAmt") != 0.0 }
        val amount = holding?.getDouble("positionAmt") ?: 0.0
        val algos = api.algos().objects()
        val owned = algos.filter { it.optString("clientAlgoId") in setOf(id+"s",id+"t") }
        if (amount == 0.0) {
            val order = try { api.query(id) } catch (e: Rejection) {
                if (e.code != -2013) throw e
                null
            }
            if (order == null && api.now() - state.getLong("created") < 60_000 ||
                order != null && order.getString("status") !in setOf("FILLED","CANCELED","EXPIRED","REJECTED","EXPIRED_IN_MATCH"))
                return "WAIT · konfirmasi order masuk"
            if (order?.optString("status") == "FILLED" && api.now() - state.getLong("created") < 10_000)
                return "WAIT · sinkronisasi posisi"
            owned.forEach { api.cancel(it.getString("clientAlgoId")) }
            check(prefs.edit().remove("intent").commit())
            return "Demo · posisi kosong dan proteksi diperiksa"
        }
        if (amount * side <= 0 || kotlin.math.abs(amount) > state.getString("qty").toDouble() * 1.0000001)
            return "WAIT · posisi demo berubah di luar aplikasi, periksa akun"
        val entry = holding!!.getDouble("entryPrice")
        val stopFill = state.getDouble("stop") * (1 - side * RiskPlan.SLIP)
        val targetFill = state.getDouble("target") * (1 - side * RiskPlan.SLIP)
        val loss = side * (entry - stopFill) + RiskPlan.FEE * (entry + stopFill)
        val gain = side * (targetFill - entry) - RiskPlan.FEE * (entry + targetFill)
        val fillOutsidePlan = loss <= 0 || gain < 2 * loss || loss * kotlin.math.abs(amount) > state.getDouble("budget")
        val emergency = fillOutsidePlan || state.optBoolean("exit") || !prefs.getBoolean("enabled",false) ||
            api.now() - state.getLong("created") >= 30 * 60_000
        if (!emergency) {
            for ((suffix, field, type) in listOf(Triple("s","stop","STOP_MARKET"), Triple("t","target","TAKE_PROFIT_MARKET"))) {
                val already = owned.firstOrNull { it.optString("clientAlgoId") == id+suffix }
                if (already != null && already.optString("side") == exitSide && already.optString("orderType") == type &&
                    already.optBoolean("closePosition") && already.optString("workingType") == "MARK_PRICE" &&
                    already.optDouble("triggerPrice") == state.getString(field).toDouble()) continue
                if (already != null || state.optBoolean("sent"+suffix)) {
                    // An acknowledged trigger disappeared while a position remains.
                    return flatten(api,state,amount,exitSide)
                }
                try {
                    api.protect(exitSide,state.getString(field),id+suffix,type)
                    state.put("sent"+suffix,true)
                    persist(state)
                } catch (e: Rejection) {
                    return flatten(api,state,amount,exitSide)
                }
            }
            val verified = api.algos().objects().filter { it.optString("clientAlgoId") in setOf(id+"s",id+"t") }
            val protected = listOf(Triple("s","stop","STOP_MARKET"), Triple("t","target","TAKE_PROFIT_MARKET")).all { (suffix,field,type) ->
                verified.any { it.optString("clientAlgoId") == id+suffix && it.optString("orderType") == type &&
                    it.optString("side") == exitSide && it.optBoolean("closePosition") &&
                    it.optString("workingType") == "MARK_PRICE" && it.optDouble("triggerPrice") == state.getDouble(field) }
            }
            if (!protected) return flatten(api,state,amount,exitSide)
            return "Demo · ${if (side == 1) "LONG" else "SHORT"} BTC · SL ${state.getString("stop")} · TP ${state.getString("target")}"
        }
        return flatten(api,state,amount,exitSide)
    }

    private fun flatten(api: DemoExchange, state: JSONObject, amount: Double, exitSide: String): String {
        state.put("exit",true)
        persist(state)
        // A fresh position snapshot plus reduceOnly caps any retried exit at the remaining position.
        api.market(exitSide,BigDecimal.valueOf(kotlin.math.abs(amount)).stripTrailingZeros().toPlainString(),
            state.getString("id")+"x"+api.now(),true)
        return "Demo · menutup posisi, menunggu konfirmasi bursa"
    }

    companion object {
        internal val entryLock = Any()
        private val lock = Any()
        const val PREFS = "me_project_futures_demo_v1"
        private const val SYMBOL = DemoExchange.SYMBOL
    }
}
