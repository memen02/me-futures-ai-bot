package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.Signer
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.CacheControl
import org.json.JSONObject

internal class AccountCheckError(val explanation: String) : IOException()
internal data class AccountAccess(val canTrade: Boolean, val futuresKey: Boolean?, val withdrawals: Boolean?)

/** All private calls are GETs to fixed Binance endpoints. Signing happens on the device. */
internal class BinanceAccountCheck(client: OkHttpClient = ReconnectingWebSocket.sharedHttpClient) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(12, TimeUnit.SECONDS).cache(null).build()
    @Volatile private var active: Call? = null
    @Volatile private var closed = false

    fun verify(pair: BinanceCredentials): AccountAccess {
        val clock = get(Request.Builder().url("https://fapi.binance.com/fapi/v1/time")).getLong("serverTime")
        val observed = System.nanoTime()
        fun signed(url: String): JSONObject {
            val ts = clock + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - observed)
            val query = Signer.signQueryString(listOf("timestamp" to ts.toString(), "recvWindow" to "5000"), pair.secret)
            return get(Request.Builder().url("$url?$query").header("X-MBX-APIKEY", pair.key))
        }
        val config = signed("https://fapi.binance.com/fapi/v1/accountConfig")
        val canTrade = config.getBoolean("canTrade")
        // Some key types or regions only expose the Futures endpoint.
        val rights = try { signed("https://api.binance.com/sapi/v1/account/apiRestrictions") }
            catch (_: IOException) { null } catch (_: org.json.JSONException) { null }
        return AccountAccess(canTrade, rights?.opt("enableFutures") as? Boolean,
            rights?.opt("enableWithdrawals") as? Boolean)
    }

    private fun get(builder: Request.Builder): JSONObject {
        val request = builder.cacheControl(CacheControl.Builder().noCache().noStore().build()).get().build()
        val call = http.newCall(request)
        active = call
        if (closed) { call.cancel(); throw IOException() }
        return call.execute().use { response ->
            val body = response.body.string()
            if (response.isSuccessful) return@use JSONObject(body)
            val code = runCatching { JSONObject(body).optInt("code") }.getOrNull()
            val reason = when {
                response.code == 451 -> "Binance membatasi akses dari lokasi jaringan ini (HTTP 451)."
                response.code == 429 || response.code == 418 -> "Batas permintaan Binance tercapai. Coba lagi nanti."
                code == -1022 -> "Tanda tangan ditolak. Periksa pasangan API Key dan API Secret."
                code == -1021 -> "Waktu permintaan ditolak. Coba uji kembali."
                code == -2015 || code == -2014 || response.code == 401 || response.code == 403 ->
                    "Akses ditolak. Periksa API Key, izin Futures, dan pembatasan IP di Binance."
                else -> "Binance menolak pemeriksaan (HTTP ${response.code})."
            }
            throw AccountCheckError(reason)
        }
    }

    fun close() { closed = true; active?.cancel() }
}
