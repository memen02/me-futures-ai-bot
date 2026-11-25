package com.mefuturesaibot.app.account

import android.os.SystemClock
import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.Signer
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal class AccountBalanceError(
    val explanation: String,
    val retryAfterMs: Long = 0L
) : IOException(explanation)

internal class AccountBalanceClient(
    client: OkHttpClient =
        ReconnectingWebSocket.sharedHttpClient,
    private val elapsed: () -> Long =
        { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long =
        { System.currentTimeMillis() }
) : AutoCloseable {

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

    fun read(
        keys: BinanceCredentials,
        venue: FuturesVenue
    ): AccountBalance {

        require(
            venue != FuturesVenue.REAL ||
                BuildConfig.MAINNET_ORDERS
        ) {
            "Mainnet tidak tersedia pada build ini"
        }

        synchronized(lock) {
            check(!closed)
            check(!busy)
            busy = true
        }

        try {
            val host = venue.host

            val before = elapsed()

            val clock = request(
                Request.Builder()
                    .url("https://$host/fapi/v1/time")
                    .get()
                    .build()
            )

            val observed = elapsed()

            val serverTime = when (
                val value = clock.opt("serverTime")
            ) {
                is Long -> value
                is Int -> value.toLong()
                else -> 0L
            }

            if (
                serverTime <= 0L ||
                observed - before !in 0L..2000L
            ) {
                throw AccountBalanceError(
                    "Waktu Binance belum sinkron"
                )
            }

            val age = elapsed() - observed

            if (age !in 0L..5000L) {
                throw AccountBalanceError(
                    "Sinkronisasi waktu kedaluwarsa"
                )
            }

            val timestamp = try {
                Math.addExact(serverTime, age)
            } catch (_: ArithmeticException) {
                throw AccountBalanceError(
                    "Waktu Binance belum sinkron"
                )
            }

            val query = Signer.signQueryString(
                listOf(
                    "recvWindow" to "5000",
                    "timestamp" to timestamp.toString()
                ),
                keys.secret
            )

            val accountStarted = elapsed()

            val body = request(
                Request.Builder()
                    .url(
                        "https://$host/fapi/v3/account?$query"
                    )
                    .header("X-MBX-APIKEY", keys.key)
                    .get()
                    .build()
            )

            val received = elapsed()

            if (received - accountStarted !in 0L..5000L) {
                throw AccountBalanceError(
                    "Respons saldo Binance terlalu lambat"
                )
            }

            val result = try {
                AccountBalanceParser.parse(
                    body,
                    venue,
                    received
                )
            } catch (_: Exception) {
                throw AccountBalanceError(
                    "Format saldo Binance tidak valid"
                )
            }

            synchronized(lock) {
                check(!closed)
                return result
            }

        } finally {
            synchronized(lock) {
                busy = false
            }
        }
    }

    private fun request(
        request: Request
    ): JSONObject {

        val call = synchronized(lock) {
            check(!closed)

            http.newCall(request).also {
                pending = it
            }
        }

        try {
            return call.execute().use { response ->

                val status = response.code

                if (status == 418 || status == 429) {
                    throw AccountBalanceError(
                        "Batas permintaan Binance",
                        AccountRetryAfter.delayMs(
                            status,
                            response.header("Retry-After"),
                            wallClock
                        )
                    )
                }

                val source = response.body.source()

                if (source.request(MAX_BODY_BYTES + 1L)) {
                    throw AccountBalanceError(
                        "Respons akun Binance terlalu besar"
                    )
                }

                val raw = source.readUtf8()

                val body = runCatching {
                    JSONObject(raw)
                }.getOrNull()

                val code = body?.optInt("code", 0) ?: 0

                if (code == -1003) {
                    throw AccountBalanceError(
                        "Batas permintaan Binance",
                        AccountRetryAfter.delayMs(
                            429,
                            response.header("Retry-After"),
                            wallClock
                        )
                    )
                }

                if (
                    status == 401 ||
                    status == 403 ||
                    code == -2014 ||
                    code == -2015
                ) {
                    throw AccountBalanceError(
                        "Akses akun Binance ditolak"
                    )
                }

                if (!response.isSuccessful || code < 0) {
                    throw AccountBalanceError(
                        "Data akun Binance belum tersedia"
                    )
                }

                body ?: throw AccountBalanceError(
                    "Data akun Binance belum dapat dibaca"
                )
            }

        } catch (error: AccountBalanceError) {
            throw error

        } catch (_: Exception) {
            throw AccountBalanceError(
                "Data akun Binance belum dapat dibaca"
            )

        } finally {
            synchronized(lock) {
                if (pending === call) {
                    pending = null
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            pending?.cancel()
            pending = null
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 2097152L
    }
}
