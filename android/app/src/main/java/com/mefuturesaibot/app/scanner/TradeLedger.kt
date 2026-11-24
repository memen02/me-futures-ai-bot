package com.mefuturesaibot.app.scanner

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

internal data class TradeClose(val at: Long, val symbol: String, val net: Double?, val reason: String) {
    fun json() = JSONObject().put("at", at).put("symbol", symbol).put("net", net).put("reason", reason)
}
internal data class MonthResult(val gain: Double = 0.0, val loss: Double = 0.0, val count: Int = 0, val unknown: Int = 0)
internal data class TradeLedger(val months: Map<String, MonthResult> = emptyMap(),
    val recent: List<TradeClose> = emptyList(), val incomplete: Boolean = false) {
    fun add(close: TradeClose): TradeLedger {
        val key = month(close.at)
        val m = months[key] ?: MonthResult()
        val n = close.net ?: 0.0
        val updated = MonthResult(m.gain + maxOf(n, 0.0), m.loss + maxOf(-n, 0.0), m.count + 1,
            m.unknown + if (close.net == null) 1 else 0)
        return copy(months = months + (key to updated), recent = (listOf(close) + recent).take(150))
    }
    fun json(): JSONObject {
        val monthly = JSONObject()
        months.forEach { (k,v) -> monthly.put(k, JSONArray(listOf(v.gain,v.loss,v.count,v.unknown))) }
        return JSONObject().put("months", monthly).put("recent", JSONArray(recent.map { it.json() })).put("incomplete", incomplete)
    }
    companion object {
        fun month(at: Long): String = YearMonth.from(Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC)).toString()
        fun read(data: JSONObject): TradeLedger {
            val obj = data.getJSONObject("months")
            val months = obj.keys().asSequence().associateWith { key ->
                val a = obj.getJSONArray(key)
                MonthResult(a.getDouble(0),a.getDouble(1),a.getInt(2),a.getInt(3))
            }
            val r = data.getJSONArray("recent")
            return TradeLedger(months, List(r.length()) {
                val c = r.getJSONObject(it)
                TradeClose(c.getLong("at"),c.getString("symbol"),c.optDouble("net").takeIf(Double::isFinite),c.getString("reason"))
            }, data.optBoolean("incomplete"))
        }
        fun legacy(log: List<String>, trades: Int): TradeLedger {
            val pattern = Regex("""(\d+)\|Tutup (\S+) · (.+) · net ([+-]?[\d.]+) USDT""")
            val rows = log.asReversed().mapNotNull { entry ->
                pattern.matchEntire(entry)?.destructured?.let { (stamp,symbol,reason,net) ->
                    TradeClose(stamp.toLong(),symbol,net.toDouble(),reason)
                }
            }
            return rows.fold(TradeLedger(incomplete = rows.size != trades)) { ledger,row -> ledger.add(row) }
        }
    }
}
