package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.exchange.Scheduler

/**
 * Blocking listenKey operations.
 *
 * start and keepAlive must run on a worker thread.
 * close must support cancellation from the
 * lifecycle controller thread.
 */
internal interface AccountSessionKeyAccess : AutoCloseable {

    fun start(): String

    fun keepAlive(expectedKey: String): String

    override fun close()
}

/**
 * Single-attempt private account connection.
 *
 * All lifecycle methods and callbacks must
 * use the same serial controller thread.
 */
internal interface AccountSessionSocket : AutoCloseable {

    fun connect()

    override fun close()
}

/**
 * Creates a fresh key client for each attempt.
 */
internal fun interface AccountSessionKeyFactory {

    fun create(): AccountSessionKeyAccess
}

/**
 * Creates a fresh socket for each attempt.
 *
 * Factories must not connect or invoke callbacks
 * before returning the socket.
 */
internal fun interface AccountSessionSocketFactory {

    fun create(
        listenKey: String,
        onStatus: (AccountSocketStatus) -> Unit,
        onEvent: (AccountStreamEvent) -> Unit,
        onFailure: (AccountSocketFailure) -> Unit
    ): AccountSessionSocket
}

/**
 * Production adapters for one immutable venue
 * and credential identity.
 *
 * No trading operations or automatic fallback.
 */
internal object AccountSessionDependencies {

    fun keyFactory(
        venue: FuturesVenue,
        apiKey: String
    ): AccountSessionKeyFactory {

        return AccountSessionKeyFactory {

            val client = AccountListenKeyClient(
                venue = venue,
                apiKey = apiKey
            )

            object : AccountSessionKeyAccess {

                override fun start(): String =
                    client.start()

                override fun keepAlive(
                    expectedKey: String
                ): String =
                    client.keepAlive(expectedKey)

                override fun close() {
                    client.close()
                }
            }
        }
    }

    /**
     * Each socket must receive a dedicated scheduler
     * targeting the same serial controller thread.
     *
     * Never share the manager scheduler or reuse
     * a scheduler across socket instances.
     */
    fun socketFactory(
        venue: FuturesVenue,
        schedulerFactory: () -> Scheduler
    ): AccountSessionSocketFactory {

        return AccountSessionSocketFactory {
            listenKey,
            onStatus,
            onEvent,
            onFailure ->

            val request =
                AccountEndpointFactory
                    .privateWebSocketRequest(
                        venue,
                        listenKey
                    )

            val transport = AccountPrivateWebSocket(
                request = request,
                onStatus = onStatus,
                onEvent = onEvent,
                onFailure = onFailure,
                scheduler = schedulerFactory()
            )

            object : AccountSessionSocket {

                override fun connect() {
                    transport.connect()
                }

                override fun close() {
                    transport.close()
                }
            }
        }
    }
}
