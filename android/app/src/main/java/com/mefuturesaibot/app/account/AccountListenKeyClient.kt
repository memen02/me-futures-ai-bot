package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class AccountListenKeyError(
    val explanation: String,
    val retryAfterMs: Long = 0L,
    val sessionExpired: Boolean = false
) : IOException(explanation)

/**
 * Private account stream authentication only.
 * No trading endpoints, signatures or automatic
 * deletion of shared listenKey sessions.
 *
 * Blocking operations must run on a worker thread.
 */
internal class AccountListenKeyClient(
    private val venue: FuturesVenue,
    private val apiKey: String,
    client: OkHttpClient =
        ReconnectingWebSocket.sharedHttpClient,
    private val wallClock: () -> Long =
        { System.currentTimeMillis() }
) : AutoCloseable {

    init {
        require(
            venue != FuturesVenue.REAL ||
                BuildConfig.MAINNET_ORDERS
        ) {
            "Mainnet tidak tersedia pada build ini"
        }

        require(
            apiKey.length in 1..512 &&
                apiKey.all { it.code in 33..126 }
        ) {
            "Format API Key tidak valid"
        }
    }

    private val http = client.newBuilder()
        .cache(null)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val lock = Any()

    private var closed = false
    private var busy = false
    private var pending: Call? = null

    fun start(): String {
        return execute("POST") { body ->
            val result = parseObject(body)

            validateKey(
                result.opt("listenKey")
            )
        }
    }

    fun keepAlive(expectedKey: String): String {

        validateKey(expectedKey)

        return execute("PUT") { body ->

            // Accept an empty successful response
            // or the active key returned by Binance.
            if (body.isBlank()) {
                expectedKey
            } else {
                val result = parseObject(body)

                if (result.has("listenKey")) {
                    validateKey(
                        result.opt("listenKey")
                    )
                } else if (result.length() == 0) {
                    expectedKey
                } else {
                    throw AccountListenKeyError(
                        "Respons keepalive tidak valid"
                    )
                }
            }
        }
    }

    private fun validateKey(value: Any?): String {
        val key = value as? String

        if (
            key == null ||
            key.length !in 1..512 ||
            !key.all {
                it in 'a'..'z' ||
                    it in 'A'..'Z' ||
                    it in '0'..'9' ||
                    it == '-' ||
                    it == '_'
            }
        ) {
            throw AccountListenKeyError(
                "Format sesi Binance tidak valid"
            )
        }

        return key
    }

    private fun parseObject(body: String): JSONObject {
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            throw AccountListenKeyError(
                "Respons sesi Binance tidak valid"
            )
        }
    }

    private fun <T> execute(
        method: String,
        decode: (String) -> T
    ): T {

        synchronized(lock) {
            check(!closed)
            check(!busy)
            busy = true
        }

        try {
            val request = Request.Builder()
                .url(
                    "https://${venue.host}/fapi/v1/listenKey"
                )
                .header("X-MBX-APIKEY", apiKey)
                .method(
                    method,
                    ByteArray(0).toRequestBody()
                )
                .build()

            val call = synchronized(lock) {
                check(!closed)

                http.newCall(request).also {
                    pending = it
                }
            }

            val result = call.execute().use { response ->

                // Respect HTTP rate limits before
                // reading an untrusted response body.
                if (
                    response.code == 418 ||
                    response.code == 429
                ) {
                    throw rateLimitError(
                        response.code,
                        response.header("Retry-After")
                    )
                }

                val source = response.body.source()

                if (source.request(MAX_BODY_BYTES + 1L)) {
                    throw AccountListenKeyError(
                        "Respons sesi Binance terlalu besar"
                    )
                }

                val body = source.readUtf8()

                val code = runCatching {
                    JSONObject(body).optInt("code", 0)
                }.getOrDefault(0)

                if (code == -1003) {
                    throw rateLimitError(
                        response.code,
                        response.header("Retry-After")
                    )
                }

                if (code == -1125) {
                    throw AccountListenKeyError(
                        "Sesi Binance kedaluwarsa",
                        sessionExpired = true
                    )
                }

                if (
                    response.code == 401 ||
                    response.code == 403 ||
                    code == -2014 ||
                    code == -2015
                ) {
                    throw AccountListenKeyError(
                        "Akses sesi Binance ditolak"
                    )
                }

                if (!response.isSuccessful || code < 0) {
                    throw AccountListenKeyError(
                        "Sesi Binance belum tersedia"
                    )
                }

                decode(body)
            }

            synchronized(lock) {
                check(!closed)
                return result
            }

        } catch (error: AccountListenKeyError) {
            throw error
        } catch (_: Exception) {
            throw AccountListenKeyError(
                "Koneksi sesi Binance belum tersedia"
            )
        } finally {
            synchronized(lock) {
                pending = null
                busy = false
            }
        }
    }

    private fun rateLimitError(
        status: Int,
        retryAfter: String?
    ): AccountListenKeyError {

        val minimum =
            if (status == 418) 300L else 120L

        val raw = retryAfter?.trim()

        val numeric = raw?.toLongOrNull()

        val delay = if (numeric != null) {
            numeric.coerceIn(
                minimum,
                Long.MAX_VALUE / 1000L
            ) * 1000L
        } else {
            val dateDelay = runCatching {
                val target = ZonedDateTime.parse(
                    raw ?: "",
                    DateTimeFormatter.RFC_1123_DATE_TIME
                ).toInstant().toEpochMilli()

                val now = wallClock()

                if (target <= now) {
                    0L
                } else {
                    Math.subtractExact(target, now)
                }
            }.getOrDefault(0L)

            maxOf(minimum * 1000L, dateDelay)
        }

        return AccountListenKeyError(
            "Batas permintaan sesi Binance",
            retryAfterMs = delay
        )
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            pending?.cancel()
            pending = null
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 16384L
    }
}
