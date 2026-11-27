package com.mefuturesaibot.app.ai.execution

import com.mefuturesaibot.app.demo.FuturesVenue
import java.security.MessageDigest
import java.util.UUID

internal data class OrderIntentIdentity(val venue: FuturesVenue, val accountId: String,
    val symbol: String, val strategyVersion: String, val signalEpochMs: Long) {
    fun valid(): Boolean = accountId.matches(Regex("[a-f0-9]{64}")) &&
        symbol.matches(Regex("[A-Z0-9]{1,32}")) &&
        strategyVersion.matches(Regex("[A-Za-z0-9._-]{1,128}")) && signalEpochMs > 0

    fun key(): String {
        require(valid())
        return MessageDigest.getInstance("SHA-256")
            .digest("${venue.name}|${accountId}|${symbol}|${strategyVersion}|${signalEpochMs}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    // Generate once at reservation; persist before any network request.
    fun newClientOrderId(): String = (if (venue == FuturesVenue.REAL) "meR" else "meD") +
        UUID.randomUUID().toString().replace("-","")
}
