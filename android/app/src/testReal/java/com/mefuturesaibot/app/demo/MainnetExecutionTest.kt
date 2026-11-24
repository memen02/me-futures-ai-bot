package com.mefuturesaibot.app.demo

import android.content.Context
import com.mefuturesaibot.app.account.BinanceCredentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainnetExecutionTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs = FuturesVenue.REAL.prefs(context)
    private val now = 9_000_000L
    private val credentials = BinanceCredentials("fixture","unused-secret")
    private val guards = mutableMapOf<String,JSONObject>()
    private var entry = ""
    private var qty = 0.0
    private var rejectedStop = false
    private var entries = 0
    private var closes = 0
    private var calls = 0
    private var lookupQty: Double? = null
    private val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        calls++
        val req = chain.request()
        assertEquals("fapi.binance.com",req.url.host)
        fun param(key: String) = requireNotNull(req.url.queryParameter(key))
        var code = 200
        val data: Any = when (req.url.encodedPath.substringAfterLast('/')) {
            "time" -> JSONObject().put("serverTime",now)
            "dual" -> JSONObject().put("dualSidePosition",false)
            "positionRisk" -> JSONArray().apply { if (qty > 0) put(JSONObject().put("symbol","BTCUSDT")
                .put("positionAmt",qty).put("entryPrice",100).put("liquidationPrice","0")) }
            "openOrders" -> JSONArray()
            "openAlgoOrders" -> JSONArray(guards.values.toList())
            "account" -> JSONObject().put("assets",JSONArray().put(JSONObject().put("asset","USDT")
                .put("marginBalance","10000").put("availableBalance","10000")))
            "premiumIndex" -> JSONObject().put("nextFundingTime",now + 7_200_000)
            "commissionRate" -> JSONObject().put("takerCommissionRate","0.0005")
            "exchangeInfo" -> JSONObject("""{"symbols":[{"symbol":"BTCUSDT","filters":[{"filterType":"PRICE_FILTER","tickSize":"0.01"},{"filterType":"MARKET_LOT_SIZE","minQty":"0.001","maxQty":"1000","stepSize":"0.001"},{"filterType":"MIN_NOTIONAL","notional":"5"}]}]}""")
            "ticker" , "bookTicker" -> JSONObject().put("askPrice",100.0).put("bidPrice",99.99).put("time",now)
            "klines" -> JSONArray().apply {
                for (i in 0..120) {
                    val open = now - (120-i)*60_000L
                    val close = if (i>=115) 100 else 99
                    put(JSONArray(listOf(open,"99","110","98",close.toString(),"10",open+59_999)))
                }
            }
            "order" -> {
                if (req.method == "POST") {
                    if (param("reduceOnly") == "true") { closes++; qty=0.0 }
                    else {
                        assertTrue(prefs.contains("intent"))
                        entries++
                        qty=param("quantity").toDouble()
                        entry=param("newClientOrderId")
                        assertTrue(entry.startsWith("meR"))
                        assertTrue(qty*100 <= 250)
                    }
                }
                JSONObject().put("clientOrderId",entry).put("side","BUY").put("status","FILLED")
                    .put("executedQty",lookupQty ?: qty)
            }
            "algoOrder" -> {
                if (rejectedStop) { code=400; JSONObject().put("code",-2021) }
                else {
                    assertEquals("true",param("closePosition"))
                    val id=param("clientAlgoId")
                    val order=JSONObject().put("clientAlgoId",id).put("orderType",param("type"))
                        .put("workingType",param("workingType")).put("side",param("side"))
                        .put("triggerPrice",param("triggerPrice")).put("closePosition",true)
                    guards[id]=order
                    order
                }
            }
            else -> error(req.url.encodedPath)
        }
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(data.toString().toResponseBody()).build()
    }).build()
    private val api = DemoExchange(credentials,http,FuturesVenue.REAL)
    private fun approve() {
        assertTrue(FuturesVenue.select(context,FuturesVenue.REAL))
        prefs.edit().clear().putBoolean("enabled",true).putString("capital","250")
            .putString("consentKey",api.accountId).commit()
    }
    @Test fun demoPrefsOrDifferentKeyCannotArmReal() {
        assertTrue(FuturesVenue.select(context,FuturesVenue.REAL))
        FuturesVenue.DEMO.prefs(context).edit().putBoolean("enabled",true).commit()
        prefs.edit().clear().commit()
        val runner=DemoRunner(context)
        runner.cycle(api)
        approve()
        prefs.edit().putString("consentKey","old").commit()
        runner.cycle(api)
        assertEquals(0,calls)
    }
    @Test fun priorApprovalCannotOpenAnotherRealPosition() {
        approve()
        assertTrue(DemoRunner(context).cycle(api).contains("terkunci"))
        assertEquals(0,calls)
        assertFalse(prefs.getBoolean("enabled",true))
        assertThrows(IllegalStateException::class.java) { api.market("BUY","0.001","blocked") }
        assertEquals(0,calls)
    }

    private fun legacyPosition() {
        approve()
        entry = "meR1"
        qty = 0.01
        val state = JSONObject().put("id",entry).put("side",1).put("qty","0.01").put("created",now-20_000)
            .put("budget",5.0).put("stop","99").put("target","105").put("accountId",api.accountId)
        prefs.edit().putString("intent",state.toString()).commit()
    }
    @Test fun legacyGuardsSurviveRestartAndStopRemainsReduceOnly() {
        legacyPosition()
        assertTrue(DemoRunner(context).cycle(api).contains("LONG"))
        assertEquals(setOf("STOP_MARKET","TAKE_PROFIT_MARKET"),guards.values.map { it.getString("orderType") }.toSet())
        DemoRunner(context).cycle(api)
        assertEquals(0,entries)
        assertEquals(2,guards.size)
        prefs.edit().putBoolean("enabled",false).commit()
        DemoRunner(context).cycle(api)
        assertEquals(1,closes)
    }
    @Test fun stopRejectionPausesAndAttemptsOnlyReduceOnlyExit() {
        legacyPosition()
        rejectedStop=true
        DemoRunner(context).cycle(api)
        assertFalse(prefs.getBoolean("enabled",true))
        assertEquals(1,closes)
        assertTrue(prefs.contains("intent"))
    }
    @Test fun unrelatedPositionCannotBeClosedByRecovery() {
        legacyPosition()
        lookupQty=0.0
        prefs.edit().putBoolean("enabled",false).commit()
        assertTrue(DemoRunner(context).cycle(api).contains("kepemilikan"))
        assertEquals(0,closes)
        assertTrue(guards.isEmpty())
    }
}
