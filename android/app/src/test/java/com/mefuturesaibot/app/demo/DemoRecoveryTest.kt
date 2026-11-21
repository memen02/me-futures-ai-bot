package com.mefuturesaibot.app.demo

import android.content.Context
import com.mefuturesaibot.app.account.BinanceCredentials
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
class DemoRecoveryTest {
    private val context = RuntimeEnvironment.getApplication() as Context
    private val prefs = context.getSharedPreferences(DemoRunner.PREFS, Context.MODE_PRIVATE)
    private val active = mutableMapOf<String,JSONObject>()
    private val writes = mutableListOf<String>()
    private var amount = 0.01
    private var rejectStop = false
    private val now = 100_000L
    private fun setup(): DemoExchange {
        val state = JSONObject().put("id","meD1").put("side",1).put("qty","0.01").put("created",now-20_000)
            .put("budget",5.0).put("stop","99").put("target","105")
        prefs.edit().clear().putBoolean("enabled",true).putString("intent",state.toString()).commit()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            assertEquals("demo-fapi.binance.com",req.url.host)
            var code = 200
            val body: Any = when (req.url.encodedPath.substringAfterLast('/')) {
                "time" -> JSONObject().put("serverTime",now)
                "positionRisk" -> JSONArray().put(JSONObject().put("symbol","BTCUSDT").put("positionAmt",amount).put("entryPrice",100.0))
                "openAlgoOrders" -> JSONArray(active.values.toList())
                "algoOrder" -> {
                    val id = req.url.queryParameter("clientAlgoId")!!
                    writes.add(req.method + id)
                    if (req.method == "DELETE") { active.remove(id); JSONObject() }
                    else if (rejectStop) { code = 400; JSONObject().put("code",-2021) }
                    else {
                        val order = JSONObject().put("clientAlgoId",id).put("side",req.url.queryParameter("side"))
                            .put("orderType",req.url.queryParameter("type")).put("workingType",req.url.queryParameter("workingType"))
                            .put("triggerPrice",req.url.queryParameter("triggerPrice")).put("closePosition",req.url.queryParameter("closePosition"))
                        active[id] = order
                        order
                    }
                }
                "order" -> {
                    if (req.method == "POST") { assertEquals("true",req.url.queryParameter("reduceOnly")); writes.add("exit"); amount=0.0 }
                    JSONObject().put("status","FILLED")
                }
                else -> error("Unexpected request")
            }
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("Demo").body(body.toString().toResponseBody()).build()
        }.build()
        return DemoExchange(BinanceCredentials("demo","demo"),http)
    }
    @Test fun installationRecoveryAndOfflineTargetDoNotDuplicateOrders() {
        val api = setup()
        val runner = DemoRunner(context)
        assertTrue(runner.cycle(api).contains("LONG"))
        DemoRunner(context).cycle(api)
        assertEquals(listOf("POSTmeD1s","POSTmeD1t"),writes)
        amount = 0.0
        active.remove("meD1t")
        runner.cycle(api)
        assertEquals("DELETEmeD1s",writes.last())
        assertFalse(prefs.contains("intent"))
    }
    @Test fun rejectedStopClosesReduceOnlyAndKeepsJournalUntilFlat() {
        val api = setup()
        rejectStop = true
        val runner = DemoRunner(context)
        runner.cycle(api)
        assertEquals(listOf("POSTmeD1s","exit"),writes)
        assertTrue(prefs.contains("intent"))
        runner.cycle(api)
        assertFalse(prefs.contains("intent"))
    }
}
