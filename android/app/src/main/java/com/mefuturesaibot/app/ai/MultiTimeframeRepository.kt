package com.mefuturesaibot.app.ai

import android.os.SystemClock
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class MultiTimeframeRepository(
    private val cache: MultiTimeframeCache =
        MultiTimeframeCache(),
    private val guard: AiRateLimitGuard =
        AiRateLimitGuard(),
    private val clock: AiExchangeClock =
        AiExchangeClock(),
    private val client: Call.Factory =
        ReconnectingWebSocket.sharedHttpClient
            .newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
    private val elapsed: () -> Long =
        { SystemClock.elapsedRealtime() }
) : AutoCloseable {

    enum class Status {
        UPDATED,
        CACHED,
        WAIT_CLOCK,
        WAIT_LIMIT,
        WAIT_DATA,
        FAILED,
        CLOSED
    }

    private val lifecycle = Any()

    @Volatile
    private var closed = false

    private var activeCall: Call? = null

    private val base =
        "https://fapi.binance.com/fapi/v1/"
            .toHttpUrl()

    private class Limited :
        IOException("AI rate limit")

    private fun request(
        endpoint: String,
        params: Map<String, String>,
        weight: Int
    ): String {
        if (closed) {
            throw IOException("Repository closed")
        }

        if (!guard.acquire(elapsed(), weight)) {
            throw Limited()
        }

        try {
            val url = base.newBuilder()
                .addPathSegment(endpoint)
                .apply {
                    params.forEach { (key, value) ->
                        addQueryParameter(key, value)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            val call = client.newCall(request)

            synchronized(lifecycle) {
                if (closed) {
                    call.cancel()
                    throw IOException("Repository closed")
                }

                activeCall = call
            }

            try {
                return call.execute().use { response ->
                    guard.onHttpResponse(
                        response.code,
                        response.header("Retry-After")
                            ?.toLongOrNull(),
                        elapsed()
                    )

                    if (response.code in listOf(418, 429)) {
                        throw Limited()
                    }

                    if (!response.isSuccessful) {
                        throw IOException(
                            "Binance HTTP ${response.code}"
                        )
                    }

                    val source = response.body.source()
                    val maxBytes = 512L * 1024L

                    if (source.request(maxBytes + 1L)) {
                        throw IOException(
                            "Response too large"
                        )
                    }

                    source.readUtf8()
                }
            } finally {
                synchronized(lifecycle) {
                    if (activeCall === call) {
                        activeCall = null
                    }
                }
            }
        } finally {
            guard.release()
        }
    }

    private fun syncClock(): Boolean {
        val sent = elapsed()

        val body = request(
            "time",
            emptyMap(),
            1
        )

        val received = elapsed()

        val serverTime =
            JSONObject(body).getLong("serverTime")

        synchronized(lifecycle) {
            if (closed) return false

            return clock.observe(
                serverTime,
                sent,
                received
            )
        }
    }

    private fun statusUnlessClosed(
        status: Status
    ): Status {
        synchronized(lifecycle) {
            return if (closed) Status.CLOSED
            else status
        }
    }

    @Synchronized
    fun refresh(
        symbol: String,
        timeframe: Timeframe
    ): Status {
        require(symbol.isNotBlank())
        require(symbol.length <= 64)

        if (closed) return Status.CLOSED

        return try {
            var bounds = clock.bounds(elapsed())

            if (bounds == null) {
                if (!syncClock()) {
                    return if (closed) Status.CLOSED
                    else Status.WAIT_CLOCK
                }

                // Beri kesempatan rate limiter
                // sebelum permintaan candle berikutnya.
                return statusUnlessClosed(
                    Status.WAIT_CLOCK
                )
            }

            val existing = cache.get(
                symbol,
                timeframe,
                bounds.latest
            )

            if (
                existing != null &&
                existing.fresh(bounds.earliest)
            ) {
                return statusUnlessClosed(
                    Status.CACHED
                )
            }

            bounds = clock.bounds(elapsed())
                ?: return statusUnlessClosed(
                    Status.WAIT_CLOCK
                )

            val cutoff = bounds.earliest

            val body = request(
                "klines",
                mapOf(
                    "symbol" to symbol,
                    "interval" to timeframe.api,
                    "limit" to
                        MultiTimeframeHistory.REQUEST_LIMIT
                            .toString(),
                    "endTime" to
                        (cutoff - 1L).toString()
                ),
                2
            )

            val history = MultiTimeframeHistory.parse(
                symbol,
                timeframe,
                body,
                cutoff
            )

            val current = clock.bounds(elapsed())
                ?: return statusUnlessClosed(
                    Status.WAIT_CLOCK
                )

            if (
                !history.fresh(current.earliest) ||
                !history.fresh(current.latest)
            ) {
                return statusUnlessClosed(
                    Status.WAIT_DATA
                )
            }

            synchronized(lifecycle) {
                if (closed) return Status.CLOSED

                if (cache.put(history)) {
                    Status.UPDATED
                } else {
                    Status.WAIT_DATA
                }
            }
        } catch (_: Limited) {
            if (closed) Status.CLOSED
            else Status.WAIT_LIMIT
        } catch (_: Exception) {
            if (closed) Status.CLOSED
            else Status.FAILED
        }
    }

    fun cooldownRemaining(): Long =
        guard.cooldownRemaining(elapsed())

    fun availableFrames(
        symbol: String
    ): Set<Timeframe> {
        synchronized(lifecycle) {
            if (closed) return emptySet()

            val bounds = clock.bounds(elapsed())
                ?: return emptySet()

            return cache.availableFrames(
                symbol,
                bounds.earliest,
                bounds.latest
            )
        }
    }

    fun snapshot(
        symbol: String
    ): Map<Timeframe, TimeframeHistory> {
        synchronized(lifecycle) {
            if (closed) return emptyMap()

            val bounds = clock.bounds(elapsed())
                ?: return emptyMap()

            return cache.snapshot(
                symbol,
                bounds.latest
            ).filterValues {
                it.fresh(bounds.earliest)
            }
        }
    }

    override fun close() {
        synchronized(lifecycle) {
            closed = true
            activeCall?.cancel()
            activeCall = null
            cache.clear()
            clock.clear()
        }
    }
}
