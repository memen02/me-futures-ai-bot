package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import org.junit.Assert.*
import org.junit.Test

class AccountEndpointFactoryTest {

    private val key = "fake-Listen_Key123"

    private fun demo(value: String = key) =
        AccountEndpointFactory.privateWebSocketRequest(
            FuturesVenue.DEMO,
            value
        )

    private fun assertInvalid(value: String) {
        val error = assertThrows(
            IllegalArgumentException::class.java
        ) {
            demo(value)
        }

        assertEquals(
            "Format sesi Binance tidak valid",
            error.message
        )
    }

    @Test
    fun demoUsesFixedHost() {
        assertEquals(
            "demo-fstream.binance.com",
            demo().url.host
        )
    }

    @Test
    fun demoUsesPrivatePath() {
        assertEquals(
            "/private/ws/$key",
            demo().url.encodedPath
        )
    }

    @Test
    fun requestUsesTlsAndDefaultPort() {
        val url = demo().url

        assertTrue(url.isHttps)
        assertEquals("https", url.scheme)
        assertEquals(443, url.port)
    }

    @Test
    fun requestUsesGetWithoutBody() {
        val request = demo()

        assertEquals("GET", request.method)
        assertNull(request.body)
    }

    @Test
    fun requestHasNoAuthenticationHeaders() {
        val request = demo()

        assertEquals(0, request.headers.size)
        assertNull(request.header("X-MBX-APIKEY"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
    }

    @Test
    fun requestHasNoUrlCredentialsOrQuery() {
        val url = demo().url

        assertTrue(url.username.isEmpty())
        assertTrue(url.password.isEmpty())
        assertNull(url.query)
        assertNull(url.fragment)
    }

    @Test
    fun mainnetRespectsBuildVariant() {
        if (BuildConfig.MAINNET_ORDERS) {
            val request =
                AccountEndpointFactory
                    .privateWebSocketRequest(
                        FuturesVenue.REAL,
                        key
                    )

            assertEquals(
                "fstream.binance.com",
                request.url.host
            )

            assertEquals(
                "/private/ws/$key",
                request.url.encodedPath
            )

            assertTrue(request.url.isHttps)
            assertEquals(443, request.url.port)
            assertEquals("GET", request.method)
            assertEquals(0, request.headers.size)
            assertNull(request.body)
            assertNull(request.url.query)
            assertNull(request.url.fragment)
        } else {
            val error = assertThrows(
                IllegalArgumentException::class.java
            ) {
                AccountEndpointFactory
                    .privateWebSocketRequest(
                        FuturesVenue.REAL,
                        key
                    )
            }

            assertEquals(
                "Mainnet tidak tersedia pada build ini",
                error.message
            )
        }
    }

    @Test
    fun acceptsAllowedAsciiCharacters() {
        val value =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "0123456789-_"

        assertEquals(
            "/private/ws/$value",
            demo(value).url.encodedPath
        )
    }

    @Test
    fun acceptsMinimumKeyLength() {
        assertEquals(
            "/private/ws/a",
            demo("a").url.encodedPath
        )
    }

    @Test
    fun acceptsMaximumKeyLength() {
        val value = "a".repeat(512)

        assertEquals(
            "/private/ws/$value",
            demo(value).url.encodedPath
        )
    }

    @Test
    fun rejectsEmptyAndWhitespaceKeys() {
        listOf(
            "",
            " ",
            " abc",
            "abc ",
            "a b",
            "\t",
            "\n",
            "\r"
        ).forEach {
            assertInvalid(it)
        }
    }

    @Test
    fun rejectsOversizedKeys() {
        assertInvalid("a".repeat(513))
        assertInvalid("a".repeat(4096))
    }

    @Test
    fun rejectsUrlManipulationCharacters() {
        listOf(
            "../abc",
            "abc/def",
            "abc\\def",
            "abc?query=value",
            "abc#fragment",
            "abc%2Fdef",
            "abc@foreign.test",
            "abc:443",
            "https://foreign.test",
            "wss://foreign.test",
            "abc&value",
            "abc=value",
            "abc+def",
            ".",
            ".."
        ).forEach {
            assertInvalid(it)
        }
    }

    @Test
    fun rejectsUnicodeAndControlCharacters() {
        listOf(
            "abc\u0000",
            "abc\u001F",
            "abc\u007F",
            "abc\u00E9",
            "abc\uFF21",
            "abc\u200B"
        ).forEach {
            assertInvalid(it)
        }
    }

    @Test
    fun validationErrorDoesNotExposeListenKey() {
        val sensitive =
            "fake-sensitive-listen-key?invalid"

        val error = assertThrows(
            IllegalArgumentException::class.java
        ) {
            demo(sensitive)
        }

        assertFalse(
            error.toString().contains(sensitive)
        )

        assertEquals(
            "Format sesi Binance tidak valid",
            error.message
        )
    }
}
