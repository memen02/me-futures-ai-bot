package com.mefuturesaibot.app.scanner

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Eksekusi simulasi memakai harga dari scan setelah candle konfirmasi tersedia. */
internal data class EntryConfirmation(val symbol: String, val long: Boolean, val time: Long) {
    fun eligible(scan: Scan): MarketRow? {
        if (!scan.ok || scan.time - time !in 1L..120_000L) return null
        val row = scan.rows.firstOrNull { it.contract.symbol == symbol } ?: return null
        val ticker = row.ticker ?: return null
        val score = row.score ?: return null
        if (ticker.time <= time || !ticker.fresh(scan.time) || ticker.volume < 1_000_000) return null
        return row.takeIf { if (long) score > 0.57 else score < 0.43 }
    }
}

internal data class PaperPosition(val symbol: String, val side: Int, val entry: Double, val opened: Long) {
    fun gross(price: Double) = side * NOTIONAL * (price / entry - 1)
    fun exitFee(price: Double) = NOTIONAL * price / entry * FEE
    companion object {
        const val NOTIONAL = 100.0
        const val FEE = 0.0005
    }
}

internal data class PaperAccount(val balance: Double = 1000.0, val position: PaperPosition? = null,
    val journal: List<String> = emptyList(), val lastAction: Long = 0L, val trades: Int = 0) {

    fun advance(scan: Scan, candidate: MarketRow?, enabled: Boolean): PaperAccount {
        if (!scan.ok || scan.time <= lastAction) return this
        val held = position
        if (held != null) {
            val t = scan.rows.firstOrNull { it.contract.symbol == held.symbol }?.ticker ?: return this
            if (!t.fresh(scan.time) || t.time <= held.opened) return this
            val gross = held.gross(t.price)
            val reason = when {
                !enabled -> "simulasi dinonaktifkan"
                gross <= -1 -> "batas rugi"
                gross >= 2 -> "target laba"
                scan.time - held.opened >= 1_800_000L -> "30 menit"
                else -> return this
            }
            val net = gross - held.exitFee(t.price) - PaperPosition.NOTIONAL * PaperPosition.FEE
            val event = "${scan.time}|Tutup ${held.symbol} · $reason · ${String.format(Locale.US, "%+.3f", net)} USDT"
            return copy(balance = balance + gross - held.exitFee(t.price), position = null,
                journal = (listOf(event) + journal).take(75), trades = trades + 1, lastAction = scan.time)
        }
        if (!enabled || balance < PaperPosition.NOTIONAL * (1 + PaperPosition.FEE) || candidate == null) return this
        val t = candidate.ticker ?: return this
        val score = candidate.score ?: return this
        if (!t.fresh(scan.time) || candidate.contract.quote != "USDT" || score in 0.43..0.57) return this
        val side = if (score > 0.57) 1 else -1
        val pos = PaperPosition(candidate.contract.symbol, side, t.price, scan.time)
        val event = "${scan.time}|Buka ${if (side > 0) "LONG" else "SHORT"} ${pos.symbol} @ ${t.price} · model + SMA"
        return copy(balance = balance - PaperPosition.NOTIONAL * PaperPosition.FEE, position = pos,
            journal = (listOf(event) + journal).take(75), lastAction = scan.time)
    }

    fun json(): JSONObject {
        val out = JSONObject().put("balance", balance).put("last", lastAction).put("trades", trades).put("log", JSONArray(journal))
        position?.let { out.put("position", JSONObject().put("symbol", it.symbol).put("side", it.side).put("entry", it.entry).put("opened", it.opened)) }
        return out
    }

    companion object {
        fun read(data: JSONObject?): PaperAccount {
            if (data == null) return PaperAccount()
            val p = data.optJSONObject("position")
            val position = p?.let { PaperPosition(it.getString("symbol"), it.getInt("side"), it.getDouble("entry"), it.getLong("opened")) }
            val log = data.optJSONArray("log") ?: JSONArray()
            return PaperAccount(data.getDouble("balance"), position, (0 until log.length()).map { log.getString(it) },
                data.optLong("last"), data.optInt("trades"))
        }
    }
}
