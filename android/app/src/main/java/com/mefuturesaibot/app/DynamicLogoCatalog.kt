package com.mefuturesaibot.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object DynamicLogoCatalog {

    private const val URL =
        "https://www.binance.com/bapi/asset/v2/public/asset/asset/get-all-asset"

    private const val REFRESH_MS = 3_600_000L
    private const val RETRY_MS = 300_000L
    private const val MAX_BYTES = 8_388_608L

    private val main = Handler(Looper.getMainLooper())

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private var paths = emptyMap<String, String>()
    private var loading = false
    private var nextRefresh = 0L

    fun path(base: String): String? =
        paths[base]

    fun refreshIfNeeded() {
        val now = SystemClock.elapsedRealtime()

        if (loading || now < nextRefresh) return

        loading = true
        nextRefresh = now + RETRY_MS

        val request = Request.Builder()
            .url(URL)
            .header("Accept", "application/json")
            .build()

        http.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                finish(null)
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                val result = try {
                    response.use {
                        require(it.isSuccessful)

                        val body = it.body
                        require(body.contentLength() <= MAX_BYTES)

                        val source = body.source()
                        require(!source.request(MAX_BYTES + 1))

                        parse(source.readUtf8())
                    }
                } catch (_: Exception) {
                    null
                }

                finish(result)
            }
        })
    }

    private fun finish(result: Map<String, String>?) {
        main.post {
            loading = false

            if (!result.isNullOrEmpty()) {
                val changed = paths != result
                paths = result
                if (changed) BinanceLogo.catalogUpdated()
                nextRefresh =
                    SystemClock.elapsedRealtime() + REFRESH_MS
            }
        }
    }

    internal fun parse(raw: String): Map<String, String> {
        val json = JSONObject(raw)

        require(json.optString("code") == "000000")

        val data = json.getJSONArray("data")
        require(data.length() in 1..10000)

        val result = mutableMapOf<String, String>()
        val ambiguous = mutableSetOf<String>()

        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue

            if (item.optBoolean("delisted")) continue

            val asset = item.optString("assetCode")

            if (asset.isBlank() || asset.length > 64) continue

            val url = item.optString("logoUrl")
                .toHttpUrlOrNull() ?: continue

            if (url.scheme != "https" ||
                url.host != "bin.bnbstatic.com" ||
                url.port != 443 ||
                url.username.isNotEmpty() ||
                url.password.isNotEmpty() ||
                url.query != null ||
                url.fragment != null
            ) continue

            val path = url.encodedPath

            if (!path.startsWith("/image/") &&
                !path.startsWith("/images/")
            ) continue

            val previous = result[asset]

            if (previous != null && previous != path) {
                ambiguous.add(asset)
            }

            result[asset] = path
        }

        ambiguous.forEach { result.remove(it) }

        return result.toMap()
    }
}
