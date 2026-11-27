package com.mefuturesaibot.app.ai.execution

import com.mefuturesaibot.app.demo.FuturesVenue

internal data class RecoveryIntent(val order: ReservedOrder, val symbol: String, val state: OrderState)

// Read all unreleased reservations before startup entry authorization.
// Never automatically resend RESERVED or SUBMITTING records after restart.
internal class OrderRecoveryEngine(private val journal: OrderJournal) {
    fun pending(venue: FuturesVenue, accountId: String): List<RecoveryIntent> {
        require(accountId.matches(Regex("[a-f0-9]{64}")))
        val result = mutableListOf<RecoveryIntent>()
        journal.readableDatabase.rawQuery(
            "SELECT intent_key,client_id,symbol,state FROM intents WHERE venue=? AND account_id=? AND released=0 ORDER BY created_ms,intent_key LIMIT 10001",
            arrayOf(venue.name,accountId)).use { c ->
            while (c.moveToNext()) {
                result.add(RecoveryIntent(ReservedOrder(c.getString(0),c.getString(1)),
                    c.getString(2),OrderState.valueOf(c.getString(3))))
            }
        }
        check(result.size <= 10000)
        return result
    }

    // Local journal emptiness is necessary, not sufficient: external positions,
    // legacy intents, orders and exclusive account ownership must also be checked.
    fun localJournalClear(venue: FuturesVenue, accountId: String): Boolean =
        pending(venue,accountId).isEmpty()
}
