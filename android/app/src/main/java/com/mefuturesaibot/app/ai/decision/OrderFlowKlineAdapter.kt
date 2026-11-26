package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.Timeframe
import org.json.JSONArray

internal object OrderFlowKlineAdapter {
    // Caller must bind the REST response to its original symbol and timeframe request.
    fun parse(symbol: String, frame: Timeframe, row: JSONArray, nowMs: Long): OrderFlowSnapshot? {
        if (symbol.isBlank() || row.length() < 11 || nowMs <= 0) return null
        return try {
            val open = row.getLong(0)
            val close = row.getLong(6)
            if (!frame.validOpen(open) || frame.nextOpen(open)-1 != close || close >= nowMs) return null
            val total = row.getString(7).toDouble()
            val buy = row.getString(10).toDouble()
            if (!total.isFinite() || !buy.isFinite() || total <= 0 || buy < 0 || buy > total) return null
            OrderFlowSnapshot(symbol,frame,close,total,buy)
        } catch (_: Exception) { null }
    }
}
