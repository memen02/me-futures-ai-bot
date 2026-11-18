package com.mefuturesaibot.app.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SignerTest {

    @Test
    fun hmacSha256Hex_cocok_dengan_test_vector_independen() {
        val expected =
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"

        val actual = Signer.hmacSha256Hex(
            "The quick brown fox jumps over the lazy dog",
            "key"
        )

        assertEquals(expected, actual)
        assertEquals(64, actual.length)
    }

    @Test
    fun buildQueryString_urutan_dipertahankan_dan_null_dilewati() {
        val qs = Signer.buildQueryString(
            listOf(
                "symbol" to "BTCUSDT",
                "side" to "BUY",
                "note" to null,
                "qty" to "1"
            )
        )

        assertEquals("symbol=BTCUSDT&side=BUY&qty=1", qs)
    }

    @Test
    fun buildQueryString_kosong_untuk_list_kosong() {
        assertEquals("", Signer.buildQueryString(emptyList()))
    }

    @Test
    fun buildQueryString_menggunakan_percent_encoding() {
        val qs = Signer.buildQueryString(
            listOf("note" to "a b+c&d=e%")
        )

        assertEquals("note=a%20b%2Bc%26d%3De%25", qs)
    }

    @Test
    fun buildQueryString_menggunakan_utf8_untuk_karakter_non_ascii() {
        val qs = Signer.buildQueryString(
            listOf("note" to "café")
        )

        assertEquals("note=caf%C3%A9", qs)
    }

    @Test
    fun signQueryString_deterministik_untuk_secret_dan_params_sama() {
        val params = listOf(
            "symbol" to "BTCUSDT",
            "timestamp" to "1700000000000"
        )

        val signedA = Signer.signQueryString(params, "super-secret")
        val signedB = Signer.signQueryString(params, "super-secret")

        assertEquals(signedA, signedB)
        assertTrue(signedA.contains("&signature="))
        assertTrue(!signedA.contains("super-secret"))
    }

    @Test
    fun signQueryString_secret_berbeda_hasil_berbeda() {
        val params = listOf("symbol" to "BTCUSDT")

        val signedA = Signer.signQueryString(params, "secret-a")
        val signedB = Signer.signQueryString(params, "secret-b")

        assertTrue(signedA != signedB)
    }

    @Test
    fun signQueryString_menolak_secret_kosong() {
        assertThrows(IllegalArgumentException::class.java) {
            Signer.signQueryString(listOf("symbol" to "BTCUSDT"), "")
        }
    }

    @Test
    fun signQueryString_menolak_secret_hanya_spasi() {
        assertThrows(IllegalArgumentException::class.java) {
            Signer.signQueryString(listOf("symbol" to "BTCUSDT"), "   ")
        }
    }

    @Test
    fun hmacSha256Hex_menolak_secret_kosong() {
        assertThrows(IllegalArgumentException::class.java) {
            Signer.hmacSha256Hex("message", "")
        }
    }
}
