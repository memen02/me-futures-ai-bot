package com.mefuturesaibot.app.demo

import com.mefuturesaibot.app.account.BinanceCredentials
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.Signer
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class Rejection(val code: Int) : IOException("Binance Futures $code")

internal class DemoExchange(private val keys: BinanceCredentials, client: okhttp3.OkHttpClient = ReconnectingWebSocket.sharedHttpClient,
    val venue: FuturesVenue = FuturesVenue.DEMO) {
    init { require(venue != FuturesVenue.REAL || com.mefuturesaibot.app.BuildConfig.MAINNET_ORDERS) }
    val accountId = FuturesVenue.keyId(keys.key)
    private val http = client.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).callTimeout(8, TimeUnit.SECONDS).build()
    private var server = 0L
    private var observed = 0L
    fun now() = server + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - observed)
    fun sync() {
        server = JSONObject(public("time")).getLong("serverTime")
        observed = System.nanoTime()
    }
    private fun request(method: String, path: String, params: List<Pair<String, String>>, signed: Boolean): String {
        val query = if (signed) Signer.signQueryString(params + listOf("recvWindow" to "5000", "timestamp" to now().toString()), keys.secret)
            else Signer.buildQueryString(params)
        val req = Request.Builder().url("https://${venue.host}/fapi/$path" + if (query.isEmpty()) "" else "?$query")
        if (signed) req.header("X-MBX-APIKEY", keys.key)
        req.method(method, if (method == "POST") ByteArray(0).toRequestBody() else null)
        return http.newCall(req.build()).execute().use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) throw Rejection(runCatching { JSONObject(body).getInt("code") }.getOrDefault(r.code))
            body
        }
    }
    fun public(path: String, vararg params: Pair<String, String>) = request("GET", "v1/$path", params.toList(), false)
    fun commission() = JSONObject(request("GET","v1/commissionRate",listOf("symbol" to SYMBOL),true)).getDouble("takerCommissionRate")
    fun account() = JSONObject(request("GET","v3/account", emptyList(),true))
    fun positions() = JSONArray(request("GET","v3/positionRisk", emptyList(),true))
    fun oneWay() = !JSONObject(request("GET","v1/positionSide/dual",emptyList(),true)).getBoolean("dualSidePosition")
    fun orders() = JSONArray(request("GET","v1/openOrders",listOf("symbol" to SYMBOL),true))
    fun algos() = JSONArray(request("GET","v1/openAlgoOrders",listOf("symbol" to SYMBOL),true))
    fun tradeHistory(start: Long, end: Long) = JSONArray(request("GET","v1/userTrades",listOf(
        "symbol" to SYMBOL, "startTime" to start.toString(),"endTime" to end.toString(),"limit" to "1000"),true))
    fun fundingIncome(start: Long, end: Long) = JSONArray(request("GET","v1/income",listOf(
        "symbol" to SYMBOL,"incomeType" to "FUNDING_FEE","startTime" to start.toString(),"endTime" to end.toString(),"limit" to "1000"),true))
    fun query(id: String) = JSONObject(request("GET","v1/order",listOf("symbol" to SYMBOL,"origClientOrderId" to id),true))
    fun cancelEntry(id: String) { request("DELETE","v1/order",listOf("symbol" to SYMBOL,"origClientOrderId" to id),true) }
    fun market(side: String, qty: String, id: String, reduce: Boolean = false): JSONObject {
        check(venue != FuturesVenue.REAL || reduce) { "Mainnet entry terkunci selama Fase 1" }
        return JSONObject(request("POST", "v1/order",
        listOf("symbol" to SYMBOL,"type" to "MARKET","side" to side,"quantity" to qty,"newClientOrderId" to id,
            "newOrderRespType" to "RESULT","reduceOnly" to reduce.toString()), true))
    }
    fun protect(side: String, trigger: String, id: String, type: String) = JSONObject(request("POST","v1/algoOrder",
        listOf("algoType" to "CONDITIONAL","symbol" to SYMBOL,"side" to side,"type" to type,"triggerPrice" to trigger,
            "workingType" to "MARK_PRICE","closePosition" to "true","clientAlgoId" to id),true))
    fun cancel(id: String) { request("DELETE","v1/algoOrder",listOf("clientAlgoId" to id),true) }
    companion object { const val SYMBOL = "BTCUSDT" }
}
