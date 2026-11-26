package com.mefuturesaibot.app.account

/**
 * Process-local credential revision registry.
 *
 * Identity is scoped to the vault storage path.
 * Revisions never contain credential material.
 *
 * A revision is invalidated before a mutation
 * is attempted, including a failed mutation.
 */
internal class CredentialRevisionGuard {

    internal class Revision internal constructor()

    private val revisions =
        mutableMapOf<String, Revision>()

    @Synchronized
    fun current(identity: String): Revision {
        require(identity.isNotBlank())

        return revisions.getOrPut(identity) {
            Revision()
        }
    }

    @Synchronized
    fun invalidate(identity: String) {
        require(identity.isNotBlank())

        revisions[identity] = Revision()
    }

    @Synchronized
    fun isCurrent(
        identity: String,
        expected: Revision
    ): Boolean =
        current(identity) === expected
}

/**
 * Credentials and their revision are captured
 * together under the vault synchronization lock.
 *
 * This is intentionally not a data class.
 */
internal class CredentialRevisionSnapshot(
    val credentials: BinanceCredentials,
    val revision: CredentialRevisionGuard.Revision
)
