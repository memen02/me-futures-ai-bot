package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue

/**
 * Serial-thread owner of account monitoring resources.
 *
 * Retires session identity before closing resources.
 * Does not execute orders or own trading services.
 *
 * Resource callbacks must be delivered on the owner thread.
 */
internal class AccountMonitoringLifecycle(
    private val currentRevision:
        (FuturesVenue) -> CredentialRevisionGuard.Revision,
    private val resourceFactory:
        (AccountSessionIsolation.Token) -> AutoCloseable
) : AutoCloseable {

    private val owner = Thread.currentThread()
    private val isolation = AccountSessionIsolation()

    private var active: AccountSessionIsolation.Token? = null
    private var resource: AutoCloseable? = null
    private var closed = false
    private var transitioning = false

    private fun requireOwner() {
        check(Thread.currentThread() === owner) {
            "Wrong monitoring owner thread"
        }
    }

    private fun requireMutation() {
        requireOwner()
        check(!transitioning) {
            "Monitoring transition in progress"
        }
    }

    private fun revisionMatches(
        token: AccountSessionIsolation.Token
    ): Boolean =
        runCatching {
            isolation.accepts(
                token,
                token.venue,
                currentRevision(token.venue)
            )
        }.getOrDefault(false)

    fun accepts(
        token: AccountSessionIsolation.Token
    ): Boolean {
        requireOwner()

        return !closed &&
            !transitioning &&
            active === token &&
            revisionMatches(token)
    }

    fun activate(
        venue: FuturesVenue,
        revision: CredentialRevisionGuard.Revision
    ): AccountSessionIsolation.Token? {
        requireMutation()
        check(!closed)

        transitioning = true

        try {
            retireCurrent()

            if (currentRevision(venue) !== revision) {
                return null
            }

            val token = isolation.activate(
                venue,
                revision
            )

            active = token

            try {
                resource = resourceFactory(token)

                if (!revisionMatches(token)) {
                    retireCurrent()
                    return null
                }

                return token
            } catch (e: Exception) {
                try {
                    retireCurrent()
                } catch (cleanup: Exception) {
                    if (cleanup !== e) {
                        e.addSuppressed(cleanup)
                    }
                }

                throw e
            }
        } finally {
            transitioning = false
        }
    }

    fun refresh(): Boolean {
        requireMutation()

        val token = active ?: return false

        if (accepts(token)) {
            return true
        }

        stop()
        return false
    }

    fun stop() {
        requireMutation()

        transitioning = true

        try {
            retireCurrent()
        } finally {
            transitioning = false
        }
    }

    private fun retireCurrent() {
        val previous = resource

        isolation.invalidate()
        active = null
        resource = null

        try {
            previous?.close()
        } catch (e: Exception) {
            // Fail closed if resource shutdown fails.
            closed = true
            isolation.close()
            throw e
        }
    }

    override fun close() {
        requireMutation()

        if (closed) return

        closed = true
        transitioning = true

        try {
            isolation.close()
            retireCurrent()
        } finally {
            transitioning = false
        }
    }
}
