package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import okhttp3.Request

/**
 * Fixed endpoint factory for private account monitoring.
 *
 * No order execution, credentials, automatic fallback,
 * redirects or user-controlled hosts.
 *
 * Mainnet private path follows Binance User Data Streams.
 * Demo private path requires live integration validation.
 */
internal object AccountEndpointFactory {

    private const val REAL_WS_HOST =
        "fstream.binance.com"

    private const val DEMO_WS_HOST =
        "demo-fstream.binance.com"

    private const val PRIVATE_WS_PATH =
        "/private/ws/"

    fun privateWebSocketRequest(
        venue: FuturesVenue,
        listenKey: String
    ): Request {

        require(
            venue != FuturesVenue.REAL ||
                BuildConfig.MAINNET_ORDERS
        ) {
            "Mainnet tidak tersedia pada build ini"
        }

        require(isValidListenKey(listenKey)) {
            "Format sesi Binance tidak valid"
        }

        val host = when (venue) {

            FuturesVenue.DEMO -> DEMO_WS_HOST

            FuturesVenue.REAL -> REAL_WS_HOST
        }

        return Request.Builder()
            .url(
                "wss://$host$PRIVATE_WS_PATH$listenKey"
            )
            .get()
            .build()
    }

    private fun isValidListenKey(
        value: String
    ): Boolean {

        return value.length in 1..512 &&
            value.all {
                it in 'a'..'z' ||
                    it in 'A'..'Z' ||
                    it in '0'..'9' ||
                    it == '-' ||
                    it == '_'
            }
    }
}
