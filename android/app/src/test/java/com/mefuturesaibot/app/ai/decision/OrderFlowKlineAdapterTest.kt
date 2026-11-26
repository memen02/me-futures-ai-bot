package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.Timeframe
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrderFlowKlineAdapterTest {
    private fun row(buy: String = "70", close: Long = 119999L) =
        JSONArray(listOf(60000L,"100","101","99","100","1",close,"100",10,"0.7",buy,"0"))
    @Test fun validClosedKlinePreservesIdentity() {
        val result = OrderFlowKlineAdapter.parse("BTCUSDT",Timeframe.M1,row(),120000L)!!
        assertEquals("BTCUSDT",result.symbol)
        assertEquals(Timeframe.M1,result.timeframe)
        assertEquals(119999L,result.closedAtMs)
        assertEquals(70.0,result.takerBuyQuoteVolume,0.0)
    }
    @Test fun invalidOrOpenKlinesAreRejected() {
        assertNull(OrderFlowKlineAdapter.parse("BTCUSDT",Timeframe.M1,row(),119999L))
        assertNull(OrderFlowKlineAdapter.parse("BTCUSDT",Timeframe.M1,row("101"),120000L))
        assertNull(OrderFlowKlineAdapter.parse("BTCUSDT",Timeframe.M1,row("NaN"),120000L))
        assertNull(OrderFlowKlineAdapter.parse("BTCUSDT",Timeframe.M1,row(close=119998L),120000L))
        assertNull(OrderFlowKlineAdapter.parse("BTCUSDT",Timeframe.M5,row(),120000L))
    }
}
