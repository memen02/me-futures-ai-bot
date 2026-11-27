package com.mefuturesaibot.app.ai.backtest

import org.json.JSONArray

// Offline adapter for Binance kline response format. Caller must authenticate source
// provenance and supply a trusted exchange-time cutoff. No network or credentials.
internal object HistoricalMarketDataIntegration {
    fun parse(symbol: String, intervalMs: Long, payload: String, cutoffMs: Long): HistoricalMarketData {
        require(payload.length in 1..1048576 && cutoffMs > 0)
        require(intervalMs in 60000L..604800000L)
        val rows = JSONArray(payload)
        require(rows.length() in 2..1500)
        val bars = ArrayList<HistoricalBar>(rows.length())
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            require(row.length() == 12)
            val start = row.getString(0).toLong()
            val end = row.getString(6).toLong()
            require(start >= 0 && start <= Long.MAX_VALUE-intervalMs)
            require(end == start+intervalMs-1 && end < cutoffMs)
            bars.add(HistoricalBar(start,row.getString(1).toDouble(),row.getString(2).toDouble(),
                row.getString(3).toDouble(),row.getString(4).toDouble(),row.getString(5).toDouble()))
        }
        return HistoricalMarketData(symbol,intervalMs,bars)
    }
}
