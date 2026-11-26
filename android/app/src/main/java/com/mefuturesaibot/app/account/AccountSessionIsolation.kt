package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue

/**
 * Process-local identity gate for account monitoring.
 *
 * Each activation creates a unique session token.
 * Old tokens cannot become active again.
 *
 * This gate does not own network resources.
 * The coordinator must close retired resources.
 */
internal class AccountSessionIsolation {

    internal class Token internal constructor(
        val venue: FuturesVenue,
        val revision: CredentialRevisionGuard.Revision
    )

    private var active: Token? = null

    private var closed = false

    @Synchronized
    fun activate(
        venue: FuturesVenue,
        revision: CredentialRevisionGuard.Revision
    ): Token {
        check(!closed) {
            "Account session isolation is closed"
        }

        return Token(
            venue,
            revision
        ).also {
            active = it
        }
    }

    @Synchronized
    fun accepts(
        token: Token,
        venue: FuturesVenue,
        revision: CredentialRevisionGuard.Revision
    ): Boolean {
        return !closed &&
            active === token &&
            token.venue == venue &&
            token.revision === revision
    }

    @Synchronized
    fun retire(token: Token): Boolean {
        if (closed || active !== token) {
            return false
        }

        active = null

        return true
    }

    @Synchronized
    fun invalidate() {
        active = null
    }

    @Synchronized
    fun close() {
        closed = true
        active = null
    }
}
