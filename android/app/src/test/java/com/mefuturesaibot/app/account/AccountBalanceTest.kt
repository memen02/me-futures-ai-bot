package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountBalanceTest {

    private fun asset() = JSONObject()
        .put("asset", "USDT")
        .put("walletBalance", "100.00")
        .put("marginBalance", "105.00")
        .put("availableBalance", "80.00")
        .put("unrealizedProfit", "5.00")

    private fun body(
        value: JSONObject = asset()
    ) = JSONObject().put(
        "assets", JSONArray().put(value)
    )

    private fun parse(
        value: JSONObject = body(),
        venue: FuturesVenue = FuturesVenue.DEMO,
        received: Long = 1000L
    ) = AccountBalanceParser.parse(
        value, venue, received
    )

    private fun rejected(action: () -> Unit) {
        val error = runCatching(action)
            .exceptionOrNull()
        assertTrue(
            "Invalid data must be rejected",
            error is IllegalArgumentException ||
                error is org.json.JSONException
        )
    }

    @Test
    fun parsesValidBalance() {
        val result = parse()

        assertEquals(
            BigDecimal("100.00"), result.wallet
        )
        assertEquals(
            BigDecimal("105.00"), result.equity
        )
        assertEquals(
            BigDecimal("80.00"), result.available
        )
        assertEquals(
            BigDecimal("5.00"), result.unrealizedPnl
        )
    }

    @Test
    fun preservesVenueIdentity() {
        assertEquals(
            FuturesVenue.DEMO,
            parse().venue
        )
        assertEquals(
            FuturesVenue.REAL,
            parse(venue = FuturesVenue.REAL).venue
        )
    }

    @Test
    fun acceptsZeroBalance() {
        val value = asset()

        listOf(
            "walletBalance",
            "marginBalance",
            "availableBalance",
            "unrealizedProfit"
        ).forEach { value.put(it, "0") }

        val result = parse(body(value))

        assertEquals(BigDecimal.ZERO, result.wallet)
        assertEquals(BigDecimal.ZERO, result.equity)
        assertEquals(BigDecimal.ZERO, result.available)
        assertEquals(
            BigDecimal.ZERO, result.unrealizedPnl
        )
    }

    @Test
    fun acceptsNegativeUnrealizedPnl() {
        val value = asset()
            .put("unrealizedProfit", "-5.00")
            .put("marginBalance", "95.00")

        assertEquals(
            BigDecimal("-5.00"),
            parse(body(value)).unrealizedPnl
        )
    }

    @Test
    fun selectsOnlyUsdt() {
        val assets = JSONArray()
            .put(asset().put("asset", "USDC"))
            .put(asset())

        val result = parse(
            JSONObject().put("assets", assets)
        )

        assertEquals(
            BigDecimal("100.00"), result.wallet
        )
    }

    @Test
    fun rejectsMissingUsdt() {
        rejected {
            parse(body(asset().put("asset", "BTC")))
        }
    }

    @Test
    fun rejectsDuplicateUsdt() {
        rejected {
            parse(JSONObject().put(
                "assets",
                JSONArray().put(asset()).put(asset())
            ))
        }
    }

    @Test
    fun rejectsMissingAmount() {
        val value = asset()
        value.remove("walletBalance")

        rejected { parse(body(value)) }
    }

    @Test
    fun rejectsInvalidNumbers() {
        listOf(
            "NaN",
            "Infinity",
            "-Infinity",
            "",
            "invalid",
            "1E100",
            "1E-100",
            "9".repeat(65)
        ).forEach { invalid ->
            rejected {
                parse(body(
                    asset().put(
                        "walletBalance", invalid
                    )
                ))
            }
        }
    }

    @Test
    fun rejectsNegativeTimestamp() {
        rejected { parse(received = -1L) }
    }

    @Test
    fun validatesFreshnessBoundary() {
        val result = parse(received = 1000L)

        assertTrue(result.fresh(1000L))
        assertTrue(result.fresh(31000L))
        assertFalse(result.fresh(31001L))
        assertFalse(result.fresh(999L))
    }

    @Test
    fun preservesDecimalPrecision() {
        val value = "123456789.12345678"

        assertEquals(
            BigDecimal(value),
            parse(body(
                asset().put("walletBalance", value)
            )).wallet
        )
    }
}
