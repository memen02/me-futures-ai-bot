package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import java.math.BigDecimal
import org.json.JSONObject

/**
 * Display-only account snapshot.
 * Never used for order execution or risk sizing.
 */
internal data class AccountBalance(
    val venue: FuturesVenue,
    val wallet: BigDecimal,
    val equity: BigDecimal,
    val available: BigDecimal,
    val unrealizedPnl: BigDecimal,
    val receivedElapsed: Long
) {
    fun fresh(now: Long): Boolean =
        receivedElapsed >= 0L &&
        now >= receivedElapsed &&
        now - receivedElapsed <= MAX_AGE_MS

    companion object {
        const val MAX_AGE_MS = 30_000L
    }
}

internal object AccountBalanceParser {

    fun parse(
        body: JSONObject,
        venue: FuturesVenue,
        receivedElapsed: Long
    ): AccountBalance {

        require(receivedElapsed >= 0L)

        val assets = body.getJSONArray("assets")

        val matches = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .filter { it.getString("asset") == "USDT" }

        require(matches.size == 1) {
            "USDT balance unavailable"
        }

        val asset = matches.single()

        fun amount(name: String): BigDecimal {
            val raw = asset.getString(name)

            require(raw.length in 1..64)

            val value = raw.toBigDecimal()

            require(value.precision() <= 40)
            require(value.scale() in -18..18)

            return value
        }

        return AccountBalance(
            venue = venue,
            wallet = amount("walletBalance"),
            equity = amount("marginBalance"),
            available = amount("availableBalance"),
            unrealizedPnl = amount("unrealizedProfit"),
            receivedElapsed = receivedElapsed
        )
    }
}
