package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue

/**
 * Blocking, display-only account balance access.
 *
 * read must run on a worker thread.
 *
 * close must support cancellation from the
 * lifecycle controller thread and must be safe
 * to call more than once.
 *
 * Each instance belongs to one immutable
 * venue and credential identity.
 */
internal interface AccountBalanceAccess : AutoCloseable {

    fun read(): AccountBalance

    override fun close()
}

/**
 * Creates a fresh balance access instance.
 *
 * Factory creation must not perform network
 * requests or invoke controller callbacks.
 *
 * Closed access instances must never be reused.
 */
internal fun interface AccountBalanceFactory {

    fun create(): AccountBalanceAccess
}

/**
 * Production adapters for display-only balances.
 *
 * One factory belongs to one immutable venue
 * and credential identity.
 *
 * Credential or venue changes require closing
 * the old controller and creating a new factory.
 *
 * No order execution, credential persistence,
 * automatic fallback or account switching.
 */
internal object AccountBalanceDependencies {

    fun balanceFactory(
        venue: FuturesVenue,
        keys: BinanceCredentials
    ): AccountBalanceFactory {

        require(
            venue != FuturesVenue.REAL ||
                BuildConfig.MAINNET_ORDERS
        ) {
            "Mainnet tidak tersedia pada build ini"
        }

        return AccountBalanceFactory {

            val client = AccountBalanceClient()

            object : AccountBalanceAccess {

                override fun read(): AccountBalance =
                    client.read(
                        keys = keys,
                        venue = venue
                    )

                override fun close() {
                    client.close()
                }
            }
        }
    }
}
