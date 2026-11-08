package com.mefuturesaibot.app.exchange

import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 signing untuk request SIGNED Binance Futures -- versi Kotlin dari
 * referensi Node.js `backend/src/exchange/signer.js` (lihat docs/PROJECT_STATUS.md
 * soal status referensi itu). Logic murni, tanpa Android API apa pun -- bisa diuji
 * penuh lewat JUnit biasa (JVM), tidak butuh emulator. Lihat SignerTest.kt.
 */
object Signer {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    /** HMAC SHA256, hex-encoded (huruf kecil, 64 karakter) -- primitif tanda tangan Binance. */
    fun hmacSha256Hex(message: String, secret: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /**
     * Bangun query string dari daftar pasangan key-value, urutan dipertahankan
     * sesuai urutan di `params` (List, bukan Map, supaya urutan selalu pasti).
     * Pasangan bernilai null dilewati. Value di-encode dengan URLEncoder.
     */
    fun buildQueryString(params: List<Pair<String, String?>>): String {
        return params
            .filter { it.second != null }
            .joinToString(separator = "&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
    }

    /** Bangun query string yang sudah ditandatangani: `...&signature=<hmac>`. */
    fun signQueryString(params: List<Pair<String, String?>>, apiSecret: String): String {
        require(apiSecret.isNotEmpty()) { "signQueryString: apiSecret wajib diisi" }
        val qs = buildQueryString(params)
        val signature = hmacSha256Hex(qs, apiSecret)
        return if (qs.isNotEmpty()) "$qs&signature=$signature" else "signature=$signature"
    }
}
