package com.mefuturesaibot.app.exchange

import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 signing untuk request SIGNED Binance Futures.
 */
object Signer {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * HMAC SHA256, hex-encoded huruf kecil.
     */
    fun hmacSha256Hex(message: String, secret: String): String {
        require(secret.isNotBlank()) { "HMAC secret wajib diisi" }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(
            SecretKeySpec(
                secret.toByteArray(Charsets.UTF_8),
                HMAC_ALGORITHM
            )
        )

        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    /**
     * Bangun query string dengan urutan parameter tetap.
     * Nilai null dilewati.
     * Spasi diubah menjadi %20, bukan +.
     */
    fun buildQueryString(params: List<Pair<String, String?>>): String {
        return params
            .mapNotNull { (key, value) ->
                value?.let {
                    "${encode(key)}=${encode(it)}"
                }
            }
            .joinToString("&")
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
    }

    /**
     * Bangun query string yang sudah ditandatangani.
     */
    fun signQueryString(
        params: List<Pair<String, String?>>,
        apiSecret: String
    ): String {
        require(apiSecret.isNotBlank()) {
            "signQueryString: apiSecret wajib diisi"
        }

        val qs = buildQueryString(params)
        val signature = hmacSha256Hex(qs, apiSecret)

        return if (qs.isNotEmpty()) {
            "$qs&signature=$signature"
        } else {
            "signature=$signature"
        }
    }
}
