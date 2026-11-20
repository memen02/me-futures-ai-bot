package com.mefuturesaibot.app.scanner

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.abs

internal data class Contract(val symbol: String, val quote: String)
internal data class Ticker(
    val price: Double, val change: Double, val high: Double, val low: Double,
    val volume: Double, val average: Double, val time: Long,
) {
    fun features() = doubleArrayOf(1.0, (change / 10).coerceIn(-1.0, 1.0),
        if (high == low) 0.0 else (2 * (price - low) / (high - low) - 1).coerceIn(-1.0, 1.0),
        ((price / average - 1) * 20).coerceIn(-1.0, 1.0))

    fun fresh(now: Long) = now - time in -5000L..120_000L
}

internal data class MarketRow(val contract: Contract, val ticker: Ticker?, val score: Double?)
internal data class Scan(
    val rows: List<MarketRow> = emptyList(), val time: Long = 0, val received: Long = 0,
    val status: String = "Tekan Start untuk memindai pasar", val ok: Boolean = false,
) {
    fun fresh(elapsed: Long) = ok && elapsed - received in 0L..120_000L
}

internal object MarketData {
    fun contracts(body: String): List<Contract> {
        val entries = JSONObject(body).getJSONArray("symbols")
        val result = mutableListOf<Contract>()
        for (i in 0 until entries.length()) {
            val item = entries.getJSONObject(i)
            if (item.optString("contractType") == "PERPETUAL" && item.optString("status") == "TRADING") {
                result.add(Contract(item.getString("symbol"), item.getString("quoteAsset")))
            }
        }
        require(result.isNotEmpty()) { "Daftar kontrak kosong" }
        return result.distinctBy { it.symbol }.sortedBy { it.symbol }
    }

    fun tickers(body: String): Map<String, Ticker> {
        val entries = JSONArray(body)
        val result = mutableMapOf<String, Ticker>()
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            val t = Ticker(item.optDouble("lastPrice"), item.optDouble("priceChangePercent"),
                item.optDouble("highPrice"), item.optDouble("lowPrice"), item.optDouble("quoteVolume"),
                item.optDouble("weightedAvgPrice"), item.optLong("closeTime"))
            if (t.price.isFinite() && t.price > 0 && t.change.isFinite() && t.high.isFinite() &&
                t.low > 0 && t.price in t.low..t.high && t.volume.isFinite() && t.volume >= 0 &&
                t.average.isFinite() && t.average > 0 && t.time > 0) result[item.getString("symbol")] = t
        }
        require(result.isNotEmpty()) { "Data ticker kosong" }
        return result
    }
}

/** Regresi logistik lokal. Satu batch memakai fitur scan sebelumnya dan label dari scan berikutnya. */
internal data class MarketModel(val weights: List<Double> = listOf(0.0, 0.0, 0.0, 0.0), val rounds: Int = 0) {
    private fun probability(x: DoubleArray): Double =
        1.0 / (1.0 + exp(-weights.indices.sumOf { weights[it] * x[it] }))

    fun score(ticker: Ticker): Double? = if (rounds >= WARMUP) probability(ticker.features()) else null

    fun learn(previous: Scan, current: Map<String, Ticker>, now: Long): MarketModel {
        if (!previous.ok || now - previous.time !in 30_000L..120_000L) return this
        val gradient = DoubleArray(4)
        var count = 0
        for (row in previous.rows) {
            val before = row.ticker ?: continue
            val after = current[row.contract.symbol] ?: continue
            if (!before.fresh(previous.time) || !after.fresh(now) || after.time - before.time !in 30_000L..120_000L ||
                before.price == after.price) continue
            val x = before.features()
            val error = (if (after.price > before.price) 1.0 else 0.0) - probability(x)
            for (i in x.indices) gradient[i] += error * x[i]
            count++
        }
        if (count == 0) return this
        return MarketModel(weights.mapIndexed { i, w -> (w * 0.999 + 0.15 * gradient[i] / count).coerceIn(-8.0, 8.0) }, rounds + 1)
    }

    fun json() = JSONObject().put("w", JSONArray(weights)).put("rounds", rounds)

    companion object {
        const val WARMUP = 30
        fun read(data: JSONObject?): MarketModel {
            val array = data?.optJSONArray("w") ?: return MarketModel()
            if (array.length() != 4) return MarketModel()
            val w = (0..3).map { array.optDouble(it) }
            return if (w.all { it.isFinite() && abs(it) <= 8 }) MarketModel(w, data.optInt("rounds").coerceAtLeast(0)) else MarketModel()
        }
    }
}
