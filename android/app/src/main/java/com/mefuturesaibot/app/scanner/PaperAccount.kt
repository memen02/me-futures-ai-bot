package com.mefuturesaibot.app.scanner

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Eksekusi simulasi memakai harga dari scan setelah candle konfirmasi tersedia. */
internal data class EntryConfirmation(val symbol: String, val long: Boolean, val time: Long, val levels: Levels? = null) {
    fun eligible(scan: Scan): MarketRow? {
        if (!scan.ok || scan.time - time !in 1L..120_000L) return null
        val row = scan.rows.firstOrNull { it.contract.symbol == symbol } ?: return null
        val ticker = row.ticker ?: return null
        val score = row.score ?: return null
        if (ticker.time <= time || !ticker.fresh(scan.time) || ticker.volume < 1_000_000) return null
        return row.takeIf { if (long) score > 0.57 else score < 0.43 }
    }
}

internal data class PaperPosition(val symbol: String, val side: Int, val entry: Double, val opened: Long,
    val quantity: Double = 100.0 / entry, val stop: Double = entry * (1 - side * 0.01),
    val target: Double = entry * (1 + side * 0.02), val funding: Double = 0.0, val fundingAt: Long = opened) {
    fun gross(price: Double) = side * quantity * (price - entry)
    fun exitFee(price: Double) = quantity * price * RiskPlan.FEE
}

internal data class Funding(val time: Long, val rate: Double, val mark: Double)

internal data class PaperAccount(val balance: Double = INITIAL_BALANCE, val position: PaperPosition? = null,
    val journal: List<String> = emptyList(), val lastAction: Long = 0L, val trades: Int = 0,
    val ratedTrades: Int = 0, val wins: Int = 0, val day: Long = -1, val dayStart: Double = balance,
    val peak: Double = balance, val drawdown: Double = 0.0, val profits: Double = 0.0,
    val losses: Double = 0.0, val measured: Int = 0, val measuredWins: Int = 0, val measuredLosses: Int = 0) {

    fun locked(now: Long) = day == now / 86_400_000 && balance <= dayStart * (1 - RiskPlan.DAY_LIMIT)

    fun advance(scan: Scan, symbol: String?, plan: RiskPlan?, enabled: Boolean, quote: Quote?,
        funding: List<Funding> = emptyList()): PaperAccount {
        if (!scan.ok || scan.time <= lastAction) return this
        val today = scan.time / 86_400_000
        val anchor = if (day == today) dayStart else balance
        val held = position
        if (held != null) {
            if (quote == null || quote.problem(scan.time) == "kuotasi kedaluwarsa" ||
                !quote.bid.isFinite() || !quote.ask.isFinite() || quote.bid <= 0 || quote.ask < quote.bid ||
                quote.at <= held.opened) return this
            val events = funding.filter { it.time in (held.fundingAt + 1)..scan.time }.distinctBy { it.time }
            val paid = events.sumOf { held.side * held.quantity * it.mark * it.rate }
            val pos = held.copy(funding = held.funding + paid, fundingAt = events.maxOfOrNull { it.time } ?: held.fundingAt)
            val mark = if (pos.side > 0) quote.bid else quote.ask
            val price = mark * (1 - pos.side * RiskPlan.SLIP)
            val equity = balance - paid + pos.gross(price) - pos.exitFee(price)
            val high = maxOf(peak, equity)
            val dd = maxOf(drawdown, (high - equity) / high)
            val reason = when {
                pos.side * (mark - pos.stop) <= 0 -> "stop-loss"
                pos.side * (mark - pos.target) >= 0 -> "take-profit"
                !enabled -> "simulasi dinonaktifkan"
                scan.time - pos.opened >= 30 * 60_000L -> "30 menit"
                else -> null
            }
            val updated = copy(day = today, dayStart = anchor, balance = balance - paid, position = pos,
                peak = high, drawdown = dd, lastAction = scan.time)
            if (reason == null) return updated
            val net = pos.gross(price) - pos.exitFee(price) - pos.exitFee(pos.entry) - pos.funding
            val event = "${scan.time}|Tutup ${pos.symbol} · $reason · net ${String.format(Locale.US, "%+.3f", net)} USDT"
            return updated.copy(balance = equity, position = null, journal = (listOf(event) + journal).take(75),
                trades = trades + 1, ratedTrades = ratedTrades + 1, wins = wins + (if (net > 0) 1 else 0),
                profits = profits + maxOf(net, 0.0), losses = losses + maxOf(-net, 0.0), measured = measured + 1,
                measuredWins = measuredWins + (if (net > 0) 1 else 0), measuredLosses = measuredLosses + (if (net < 0) 1 else 0))
        }
        if (!enabled || locked(scan.time) || plan == null || symbol == null) return this
        val pos = PaperPosition(symbol, plan.side, plan.entry, scan.time, plan.quantity, plan.stop, plan.target)
        val event = "${scan.time}|Buka ${if (pos.side > 0) "LONG" else "SHORT"} $symbol · SL ${pos.stop} · TP ${pos.target}"
        return copy(balance = balance - pos.exitFee(pos.entry), position = pos, day = today, dayStart = anchor,
            journal = (listOf(event) + journal).take(75), lastAction = scan.time)
    }

    fun json(): JSONObject {
        val out = JSONObject().put("balance", balance).put("last", lastAction).put("trades", trades).put("log", JSONArray(journal))
            .put("rated", ratedTrades).put("wins", wins).put("day", day).put("anchor", dayStart).put("peak", peak)
            .put("dd", drawdown).put("profits", profits).put("losses", losses).put("measured", measured)
            .put("mw", measuredWins).put("ml", measuredLosses)
        position?.let { out.put("position", JSONObject().put("symbol", it.symbol).put("side", it.side).put("entry", it.entry)
            .put("opened", it.opened).put("qty", it.quantity).put("stop", it.stop).put("target", it.target)
            .put("funding", it.funding).put("fundingAt", it.fundingAt)) }
        return out
    }

    companion object {
        const val INITIAL_BALANCE = 1000.0
        fun read(data: JSONObject?): PaperAccount {
            if (data == null) return PaperAccount()
            val position = data.optJSONObject("position")?.let { p ->
                val legacy = PaperPosition(p.getString("symbol"), p.getInt("side"), p.getDouble("entry"), p.getLong("opened"))
                legacy.copy(quantity = p.optDouble("qty", legacy.quantity), stop = p.optDouble("stop", legacy.stop),
                    target = p.optDouble("target", legacy.target), funding = p.optDouble("funding", 0.0),
                    fundingAt = p.optLong("fundingAt", legacy.opened))
            }
            val log = data.optJSONArray("log") ?: JSONArray()
            val bal = data.getDouble("balance")
            return PaperAccount(bal, position, (0 until log.length()).map { log.getString(it) },
                data.optLong("last"), data.optInt("trades"), data.optInt("rated"), data.optInt("wins"),
                data.optLong("day", -1), data.optDouble("anchor", bal), data.optDouble("peak", bal),
                data.optDouble("dd", 0.0), data.optDouble("profits", 0.0), data.optDouble("losses", 0.0),
                data.optInt("measured"), data.optInt("mw"), data.optInt("ml"))
        }
    }
}
