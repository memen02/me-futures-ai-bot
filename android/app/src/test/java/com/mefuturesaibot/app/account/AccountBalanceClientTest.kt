package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.exchange.Signer
import java.io.IOException
import java.math.BigDecimal
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AccountBalanceClientTest {

    private val keys = BinanceCredentials(
        "example-key",
        "example-secret"
    )

    private val clockBody =
        """{"serverTime":1700000000000}"""

    private val accountBody = """
        {
          "assets": [
            {
              "asset": "USDT",
              "walletBalance": "100.00",
              "marginBalance": "105.00",
              "availableBalance": "80.00",
              "unrealizedProfit": "5.00"
            }
          ]
        }
    """.trimIndent()

    private fun response(
        request: Request,
        code: Int,
        body: String,
        retryAfter: String? = null
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fixture")
            .body(body.toResponseBody())

        if (retryAfter != null) {
            builder.header(
                "Retry-After",
                retryAfter
            )
        }

        return builder.build()
    }

    private fun client(
        elapsed: () -> Long = { 1000L },
        answer: (Request) -> Response
    ): AccountBalanceClient {

        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                answer(chain.request())
            }
            .build()

        return AccountBalanceClient(
            http,
            elapsed
        )
    }

    @Test
    fun demoUsesOnlySignedAccountRead() {
        val paths = mutableListOf<String>()

        client { request ->
            val url = request.url

            paths.add(url.encodedPath)

            assertEquals("GET", request.method)
            assertEquals("https", url.scheme)
            assertEquals(
                "demo-fapi.binance.com",
                url.host
            )

            if (url.encodedPath.endsWith("/time")) {
                assertNull(
                    request.header("X-MBX-APIKEY")
                )
                assertNull(url.encodedQuery)

                response(request, 200, clockBody)
            } else {
                assertEquals(
                    keys.key,
                    request.header("X-MBX-APIKEY")
                )

                assertEquals(
                    "5000",
                    url.queryParameter("recvWindow")
                )

                assertEquals(
                    "1700000000000",
                    url.queryParameter("timestamp")
                )

                val query = url.encodedQuery!!
                    .substringBefore("&signature=")

                assertEquals(
                    Signer.hmacSha256Hex(
                        query,
                        keys.secret
                    ),
                    url.queryParameter("signature")
                )

                assertFalse(
                    url.toString().contains(keys.secret)
                )

                response(request, 200, accountBody)
            }
        }.use { api ->
            val balance = api.read(
                keys,
                FuturesVenue.DEMO
            )

            assertEquals(
                BigDecimal("105.00"),
                balance.equity
            )

            assertEquals(
                FuturesVenue.DEMO,
                balance.venue
            )
        }

        assertEquals(
            listOf(
                "/fapi/v1/time",
                "/fapi/v3/account"
            ),
            paths
        )
    }

    @Test
    fun realUsesMainnetHost() {
        if (!BuildConfig.MAINNET_ORDERS) {
            var calls = 0

            client { request ->
                calls++
                response(request, 200, clockBody)
            }.use { api ->
                assertThrows(
                    IllegalArgumentException::class.java
                ) {
                    api.read(keys, FuturesVenue.REAL)
                }

                assertEquals(0, calls)
            }

            return
        }

        client { request ->
            assertEquals(
                "fapi.binance.com",
                request.url.host
            )

            assertEquals("GET", request.method)

            response(
                request,
                200,
                if (request.url.encodedPath
                    .endsWith("/time")
                ) clockBody else accountBody
            )
        }.use { api ->
            assertEquals(
                FuturesVenue.REAL,
                api.read(
                    keys,
                    FuturesVenue.REAL
                ).venue
            )
        }
    }

    @Test
    fun rateLimitHonorsRetryAfter() {
        client { request ->
            response(
                request,
                429,
                "{}",
                "180"
            )
        }.use { api ->
            val error = assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertEquals(
                180000L,
                error.retryAfterMs
            )
        }
    }

    @Test
    fun rateLimitHasMinimumCooldown() {
        client { request ->
            response(
                request,
                429,
                "{}",
                "1"
            )
        }.use { api ->
            val error = assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertEquals(
                120000L,
                error.retryAfterMs
            )
        }
    }

    @Test
    fun ipBanHasLongerCooldown() {
        client { request ->
            response(request, 418, "{}")
        }.use { api ->
            val error = assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertEquals(
                300000L,
                error.retryAfterMs
            )
        }
    }

    @Test
    fun deniedAccessDoesNotExposeResponse() {
        client { request ->
            if (request.url.encodedPath
                .endsWith("/time")
            ) {
                response(request, 200, clockBody)
            } else {
                response(
                    request,
                    403,
                    """{"msg":"private fixture"}"""
                )
            }
        }.use { api ->
            val error = assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertEquals(
                "Akses akun Binance ditolak",
                error.explanation
            )

            assertFalse(
                error.message.orEmpty()
                    .contains("private fixture")
            )
        }
    }

    @Test
    fun slowClockPreventsPrivateRequest() {
        var now = 1000L
        var calls = 0

        client(elapsed = { now }) { request ->
            calls++
            now += 2001L

            response(request, 200, clockBody)
        }.use { api ->
            assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertEquals(1, calls)
        }
    }

    @Test
    fun networkFailureDoesNotExposeSecret() {
        client {
            throw IOException(keys.secret)
        }.use { api ->
            val error = assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertFalse(
                error.message.orEmpty()
                    .contains(keys.secret)
            )
        }
    }

    @Test
    fun closedClientRejectsNewReads() {
        var calls = 0

        val api = client { request ->
            calls++
            response(request, 200, clockBody)
        }

        api.close()

        assertThrows(
            IllegalStateException::class.java
        ) {
            api.read(keys, FuturesVenue.DEMO)
        }

        assertEquals(0, calls)
    }

    @Test
    fun failedReadReleasesBusyState() {
        var calls = 0

        client { request ->
            calls++

            when (calls) {
                1 -> response(request, 500, "{}")
                2 -> response(request, 200, clockBody)
                else -> response(
                    request,
                    200,
                    accountBody
                )
            }
        }.use { api ->
            assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            val result = api.read(
                keys,
                FuturesVenue.DEMO
            )

            assertEquals(
                BigDecimal("100.00"),
                result.wallet
            )

            assertEquals(3, calls)
        }
    }


    @Test
    fun malformedBalanceHasSafeError() {
        client { request ->
            response(
                request,
                200,
                if (request.url.encodedPath.endsWith("/time"))
                    clockBody
                else
                    """{"assets":[{"asset":"USDT","walletBalance":"private-value"}]}"""
            )
        }.use { api ->
            val error = assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertEquals(
                "Format saldo Binance tidak valid",
                error.explanation
            )

            assertFalse(
                error.message.orEmpty().contains("private-value")
            )
        }
    }

    @Test
    fun slowAccountResponseIsRejected() {
        var now = 1000L

        client(elapsed = { now }) { request ->
            if (request.url.encodedPath.endsWith("/time")) {
                response(request, 200, clockBody)
            } else {
                now += 5001L
                response(request, 200, accountBody)
            }
        }.use { api ->
            val error = assertThrows(
                AccountBalanceError::class.java
            ) {
                api.read(keys, FuturesVenue.DEMO)
            }

            assertEquals(
                "Respons saldo Binance terlalu lambat",
                error.explanation
            )
        }
    }
}
