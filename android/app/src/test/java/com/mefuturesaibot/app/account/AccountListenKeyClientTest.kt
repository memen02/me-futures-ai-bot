package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AccountListenKeyClientTest {

    private val apiKey = "fake-api-key"
    private val listenKey = "fake-listen-key_123"

    private fun response(
        request: Request,
        code: Int = 200,
        body: String = """{"listenKey":"$listenKey"}""",
        retryAfter: String? = null
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Mock response")
            .body(body.toResponseBody())

        if (retryAfter != null) {
            builder.header("Retry-After", retryAfter)
        }

        return builder.build()
    }

    private fun client(
        venue: FuturesVenue = FuturesVenue.DEMO,
        key: String = apiKey,
        wallClock: () -> Long =
            { System.currentTimeMillis() },
        handler: (Request) -> Response
    ): AccountListenKeyClient {
        val http = OkHttpClient.Builder()
            .addInterceptor {
                handler(it.request())
            }
            .build()

        return AccountListenKeyClient(
            venue,
            key,
            http,
            wallClock
        )
    }

    @Test
    fun startsDemoSessionWithExactRequest() {
        var calls = 0

        client { request ->
            calls++

            assertEquals("POST", request.method)
            assertEquals("https", request.url.scheme)
            assertEquals(
                "demo-fapi.binance.com",
                request.url.host
            )
            assertEquals(
                "/fapi/v1/listenKey",
                request.url.encodedPath
            )
            assertNull(request.url.query)
            assertEquals(
                apiKey,
                request.header("X-MBX-APIKEY")
            )
            assertEquals(
                0L,
                request.body!!.contentLength()
            )

            response(request)
        }.use {
            assertEquals(listenKey, it.start())
        }

        assertEquals(1, calls)
    }

    @Test
    fun realVenueRespectsBuildGuard() {
        var calls = 0

        val create = {
            client(FuturesVenue.REAL) { request ->
                calls++

                assertEquals(
                    "fapi.binance.com",
                    request.url.host
                )

                response(request)
            }
        }

        if (BuildConfig.MAINNET_ORDERS) {
            create().use {
                assertEquals(listenKey, it.start())
            }

            assertEquals(1, calls)
        } else {
            assertThrows(
                IllegalArgumentException::class.java
            ) {
                create()
            }

            assertEquals(0, calls)
        }
    }

    @Test
    fun keepaliveUsesPutWithoutQuery() {
        client { request ->
            assertEquals("PUT", request.method)
            assertEquals(
                "/fapi/v1/listenKey",
                request.url.encodedPath
            )
            assertNull(request.url.query)
            assertEquals(
                apiKey,
                request.header("X-MBX-APIKEY")
            )
            assertEquals(
                0L,
                request.body!!.contentLength()
            )

            response(request)
        }.use {
            assertEquals(
                listenKey,
                it.keepAlive(listenKey)
            )
        }
    }

    @Test
    fun keepaliveAcceptsEmptyObject() {
        client {
            response(it, body = "{}")
        }.use {
            assertEquals(
                listenKey,
                it.keepAlive(listenKey)
            )
        }
    }

    @Test
    fun keepaliveAcceptsEmptyBody() {
        client {
            response(it, body = "")
        }.use {
            assertEquals(
                listenKey,
                it.keepAlive(listenKey)
            )
        }
    }

    @Test
    fun keepaliveReturnsReplacementKey() {
        client {
            response(
                it,
                body = """{"listenKey":"replacement_123"}"""
            )
        }.use {
            assertEquals(
                "replacement_123",
                it.keepAlive(listenKey)
            )
        }
    }

    @Test
    fun rejectsMissingListenKey() {
        client {
            response(it, body = "{}")
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }
        }
    }

    @Test
    fun rejectsNumericListenKey() {
        client {
            response(
                it,
                body = """{"listenKey":123}"""
            )
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }
        }
    }

    @Test
    fun rejectsUnsafeListenKeyCharacters() {
        val invalidKeys = listOf(
            "",
            "abc/def",
            "abc?def",
            "abc#def",
            "abc def",
            "abc%20def",
            "x".repeat(513)
        )

        for (invalid in invalidKeys) {
            client {
                response(
                    it,
                    body = """{"listenKey":"$invalid"}"""
                )
            }.use {
                assertThrows(
                    AccountListenKeyError::class.java
                ) {
                    it.start()
                }
            }
        }
    }

    @Test
    fun rejectsInvalidExpectedKeyBeforeNetwork() {
        var calls = 0

        client {
            calls++
            response(it)
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.keepAlive("invalid/key")
            }
        }

        assertEquals(0, calls)
    }

    @Test
    fun rejectsInvalidApiKey() {
        val invalidKeys = listOf(
            "",
            "key with space",
            "key\ninjection",
            "x".repeat(513)
        )

        for (invalid in invalidKeys) {
            assertThrows(
                IllegalArgumentException::class.java
            ) {
                client(key = invalid) {
                    error("Unexpected network request")
                }
            }
        }
    }

    @Test
    fun rejectsMalformedResponse() {
        client {
            response(it, body = "{broken")
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }
        }
    }

    @Test
    fun rejectsUnexpectedKeepaliveObject() {
        client {
            response(
                it,
                body = """{"unexpected":true}"""
            )
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.keepAlive(listenKey)
            }
        }
    }

    @Test
    fun recognizesExpiredSession() {
        client {
            response(
                it,
                code = 400,
                body = """{"code":-1125}"""
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.keepAlive(listenKey)
            }

            assertTrue(error.sessionExpired)
            assertEquals(0L, error.retryAfterMs)
        }
    }

    @Test
    fun respectsRetryAfter429() {
        client {
            response(
                it,
                code = 429,
                body = "{}",
                retryAfter = "180"
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(180000L, error.retryAfterMs)
            assertFalse(error.sessionExpired)
        }
    }

    @Test
    fun appliesMinimum429Cooldown() {
        client {
            response(
                it,
                code = 429,
                body = "{}",
                retryAfter = "1"
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(120000L, error.retryAfterMs)
        }
    }

    @Test
    fun appliesMinimum418Cooldown() {
        client {
            response(
                it,
                code = 418,
                body = "{}"
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(300000L, error.retryAfterMs)
        }
    }

    @Test
    fun recognizesBinanceRateLimitCode() {
        client {
            response(
                it,
                code = 400,
                body = """{"code":-1003}"""
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(120000L, error.retryAfterMs)
        }
    }

    @Test
    fun retryAfterCannotOverflow() {
        client {
            response(
                it,
                code = 429,
                body = "{}",
                retryAfter = Long.MAX_VALUE.toString()
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(
                (Long.MAX_VALUE / 1000L) * 1000L,
                error.retryAfterMs
            )
        }
    }

    @Test
    fun sanitizesAccessDeniedResponse() {
        client {
            response(
                it,
                code = 403,
                body = """{"msg":"$apiKey $listenKey"}"""
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertFalse(
                error.toString().contains(apiKey)
            )
            assertFalse(
                error.toString().contains(listenKey)
            )
            assertNull(error.cause)
        }
    }

    @Test
    fun sanitizesNetworkFailure() {
        client {
            throw IOException("$apiKey $listenKey")
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertFalse(
                error.toString().contains(apiKey)
            )
            assertFalse(
                error.toString().contains(listenKey)
            )
            assertNull(error.cause)
        }
    }

    @Test
    fun rejectsOversizedResponse() {
        client {
            response(
                it,
                body = "x".repeat(16385)
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(
                "Respons sesi Binance terlalu besar",
                error.explanation
            )
        }
    }

    @Test
    fun closedClientRejectsNewRequests() {
        var calls = 0

        val session = client {
            calls++
            response(it)
        }

        session.close()
        session.close()

        assertThrows(
            IllegalStateException::class.java
        ) {
            session.start()
        }

        assertEquals(0, calls)
    }

    @Test
    fun failedRequestReleasesBusyState() {
        var calls = 0

        client {
            calls++

            if (calls == 1) {
                throw IOException("Mock failure")
            }

            response(it)
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(listenKey, it.start())
        }

        assertEquals(2, calls)
    }

    @Test
    fun rejectsSuccessWithNegativeBinanceCode() {
        client {
            response(
                it,
                body = """{"code":-1000,"listenKey":"$listenKey"}"""
            )
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }
        }
    }

    @Test
    fun oversized429PreservesCooldown() {
        client {
            response(
                it,
                code = 429,
                body = "x".repeat(20000),
                retryAfter = "240"
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(240000L, error.retryAfterMs)
        }
    }

    @Test
    fun oversized418PreservesCooldown() {
        client {
            response(
                it,
                code = 418,
                body = "x".repeat(20000),
                retryAfter = "600"
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(600000L, error.retryAfterMs)
        }
    }

    @Test
    fun supportsHttpDateRetryAfter() {
        client(
            wallClock = { 1700000000000L }
        ) {
            response(
                it,
                code = 429,
                body = "{}",
                retryAfter =
                    "Tue, 14 Nov 2023 22:18:20 GMT"
            )
        }.use {
            val error = assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }

            assertEquals(300000L, error.retryAfterMs)
        }
    }

    @Test
    fun rejectsRedirectResponse() {
        client { request ->
            response(request, code = 302)
                .newBuilder()
                .header(
                    "Location",
                    "https://example.invalid/"
                )
                .build()
        }.use {
            assertThrows(
                AccountListenKeyError::class.java
            ) {
                it.start()
            }
        }
    }

    @Test
    fun rejectsConcurrentRequests() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val worker = Executors.newSingleThreadExecutor()

        val session = client { request ->
            calls.incrementAndGet()
            entered.countDown()

            check(release.await(5, TimeUnit.SECONDS))

            response(request)
        }

        try {
            val first = worker.submit<String> {
                session.start()
            }

            assertTrue(
                entered.await(5, TimeUnit.SECONDS)
            )

            assertThrows(
                IllegalStateException::class.java
            ) {
                session.start()
            }

            assertEquals(1, calls.get())

            release.countDown()

            assertEquals(
                listenKey,
                first.get(5, TimeUnit.SECONDS)
            )
        } finally {
            release.countDown()
            session.close()
            worker.shutdownNow()
        }
    }

    @Test
    fun closeRejectsInFlightResult() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()

        val session = client { request ->
            entered.countDown()

            check(release.await(5, TimeUnit.SECONDS))

            response(request)
        }

        try {
            val result = worker.submit<Boolean> {
                try {
                    session.start()
                    false
                } catch (_: AccountListenKeyError) {
                    true
                }
            }

            assertTrue(
                entered.await(5, TimeUnit.SECONDS)
            )

            session.close()
            release.countDown()

            assertTrue(
                result.get(5, TimeUnit.SECONDS)
            )

            assertThrows(
                IllegalStateException::class.java
            ) {
                session.start()
            }
        } finally {
            release.countDown()
            session.close()
            worker.shutdownNow()
        }
    }
}
