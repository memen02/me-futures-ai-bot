package com.mefuturesaibot.app.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountStreamEventTest {

    private val timestamp = 1700000000000L

    private fun parse(raw: String) =
        AccountStreamEventParser.parse(raw)

    private fun expect(
        type: AccountStreamEventType,
        raw: String,
        time: Long = 0L
    ) {
        val result = parse(raw)

        assertEquals(type, result.type)
        assertEquals(time, result.eventTime)
    }

    private fun invalid(raw: String) {
        expect(AccountStreamEventType.INVALID, raw)
    }

    @Test
    fun recognizesAccountUpdate() {
        expect(
            AccountStreamEventType.ACCOUNT_CHANGED,
            """{"e":"ACCOUNT_UPDATE","E":$timestamp,"a":{"B":[],"P":[]}}""",
            timestamp
        )
    }

    @Test
    fun recognizesOrderUpdate() {
        expect(
            AccountStreamEventType.ORDER_CHANGED,
            """{"e":"ORDER_TRADE_UPDATE","E":$timestamp,"o":{"s":"BTCUSDT"}}""",
            timestamp
        )
    }

    @Test
    fun recognizesConfigUpdate() {
        expect(
            AccountStreamEventType.CONFIG_CHANGED,
            """{"e":"ACCOUNT_CONFIG_UPDATE","E":$timestamp,"ac":{"s":"BTCUSDT","l":20}}""",
            timestamp
        )
    }

    @Test
    fun recognizesExpiredSession() {
        expect(
            AccountStreamEventType.SESSION_EXPIRED,
            """{"e":"listenKeyExpired","E":$timestamp}""",
            timestamp
        )
    }

    @Test
    fun ignoresUnknownEvent() {
        expect(
            AccountStreamEventType.IGNORED,
            """{"e":"UNKNOWN_EVENT","E":$timestamp}"""
        )
    }

    @Test
    fun ignoresPublicMarketEvent() {
        expect(
            AccountStreamEventType.IGNORED,
            """{"e":"markPriceUpdate","E":$timestamp}"""
        )
    }

    @Test
    fun rejectsEmptyMessage() {
        invalid("")
    }

    @Test
    fun rejectsMalformedJson() {
        invalid("{broken")
    }

    @Test
    fun rejectsJsonArray() {
        invalid("[]")
    }

    @Test
    fun rejectsMissingEventType() {
        invalid("""{"E":$timestamp}""")
    }

    @Test
    fun rejectsNumericEventType() {
        invalid("""{"e":123,"E":$timestamp}""")
    }

    @Test
    fun rejectsMissingTimestamp() {
        invalid("""{"e":"ACCOUNT_UPDATE","a":{}}""")
    }

    @Test
    fun rejectsZeroTimestamp() {
        invalid("""{"e":"ACCOUNT_UPDATE","E":0,"a":{}}""")
    }

    @Test
    fun rejectsNegativeTimestamp() {
        invalid("""{"e":"ACCOUNT_UPDATE","E":-1,"a":{}}""")
    }

    @Test
    fun rejectsStringTimestamp() {
        invalid(
            """{"e":"ACCOUNT_UPDATE","E":"$timestamp","a":{}}"""
        )
    }

    @Test
    fun rejectsFractionalTimestamp() {
        invalid("""{"e":"ACCOUNT_UPDATE","E":1.5,"a":{}}""")
    }

    @Test
    fun rejectsMissingAccountPayload() {
        invalid(
            """{"e":"ACCOUNT_UPDATE","E":$timestamp}"""
        )
    }

    @Test
    fun rejectsInvalidAccountPayload() {
        invalid(
            """{"e":"ACCOUNT_UPDATE","E":$timestamp,"a":[]}"""
        )
    }

    @Test
    fun rejectsMissingOrderPayload() {
        invalid(
            """{"e":"ORDER_TRADE_UPDATE","E":$timestamp}"""
        )
    }

    @Test
    fun rejectsOversizedMessage() {
        val raw = """{"e":"UNKNOWN","data":"""" +
            "x".repeat(262144) + "\"}"

        invalid(raw)
    }
}
