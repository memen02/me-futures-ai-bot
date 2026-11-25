package com.mefuturesaibot.app.account

import org.json.JSONObject

/**
 * Display-only private account stream events.
 * Never triggers order execution.
 */
internal enum class AccountStreamEventType {
    ACCOUNT_CHANGED,
    ORDER_CHANGED,
    CONFIG_CHANGED,
    SESSION_EXPIRED,
    IGNORED,
    INVALID
}

internal data class AccountStreamEvent(
    val type: AccountStreamEventType,
    val eventTime: Long = 0L
)

internal object AccountStreamEventParser {

    private const val MAX_MESSAGE_LENGTH = 262144

    fun parse(raw: String): AccountStreamEvent {

        if (raw.length !in 1..MAX_MESSAGE_LENGTH) {
            return invalid()
        }

        return try {
            val root = JSONObject(raw)

            val event = root.opt("e") as? String
                ?: return invalid()

            val type = when (event) {
                "ACCOUNT_UPDATE" ->
                    AccountStreamEventType.ACCOUNT_CHANGED

                "ORDER_TRADE_UPDATE" ->
                    AccountStreamEventType.ORDER_CHANGED

                "ACCOUNT_CONFIG_UPDATE" ->
                    AccountStreamEventType.CONFIG_CHANGED

                "listenKeyExpired" ->
                    AccountStreamEventType.SESSION_EXPIRED

                else ->
                    AccountStreamEventType.IGNORED
            }

            if (type == AccountStreamEventType.IGNORED) {
                return AccountStreamEvent(type)
            }

            val time = when (val value = root.opt("E")) {
                is Long -> value
                is Int -> value.toLong()
                else -> return invalid()
            }

            if (time <= 0L) {
                return invalid()
            }

            when (type) {
                AccountStreamEventType.ACCOUNT_CHANGED -> {
                    if (root.optJSONObject("a") == null) {
                        return invalid()
                    }
                }

                AccountStreamEventType.ORDER_CHANGED -> {
                    if (root.optJSONObject("o") == null) {
                        return invalid()
                    }
                }

                else -> Unit
            }

            AccountStreamEvent(type, time)

        } catch (_: Exception) {
            invalid()
        }
    }

    private fun invalid() = AccountStreamEvent(
        AccountStreamEventType.INVALID
    )
}
