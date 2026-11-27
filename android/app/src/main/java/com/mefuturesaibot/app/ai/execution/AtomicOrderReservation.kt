package com.mefuturesaibot.app.ai.execution

import android.content.ContentValues

internal data class ReservedOrder(val intentKey: String, val clientOrderId: String)

// Local reservation only. External account reconciliation and exclusive ownership
// must be enforced before invoking this API. Never send on a null result.
internal class AtomicOrderReservation(private val journal: OrderJournal) {
    fun reserve(identity: OrderIntentIdentity, riskMicros: Long, budgetMicros: Long,
                nowMs: Long): ReservedOrder? {
        if (!identity.valid() || riskMicros <= 0 || budgetMicros < riskMicros ||
            nowMs <= identity.signalEpochMs) return null
        val key = identity.key()
        val db = journal.writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT 1 FROM intents WHERE intent_key=?",
                arrayOf(key)).use { if (it.moveToFirst()) return null }
            var remaining = budgetMicros
            db.rawQuery("SELECT symbol,state,risk_micros FROM intents WHERE venue=? AND account_id=? AND released=0",
                arrayOf(identity.venue.name,identity.accountId)).use { c ->
                while (c.moveToNext()) {
                    // Initial conservative policy: any unresolved intent blocks all new entries.
                    if (c.getString(0) == identity.symbol ||
                        c.getString(1) in setOf("RESERVED","SUBMITTING","UNKNOWN")) return null
                    val risk = c.getLong(2)
                    if (risk <= 0 || risk > remaining) return null
                    remaining -= risk
                }
            }
            if (riskMicros > remaining) return null
            val clientId = identity.newClientOrderId()
            val values = ContentValues().apply {
                put("intent_key",key); put("venue",identity.venue.name)
                put("account_id",identity.accountId); put("symbol",identity.symbol)
                put("strategy",identity.strategyVersion); put("signal_epoch",identity.signalEpochMs)
                put("client_id",clientId); put("state",OrderState.RESERVED.name)
                put("risk_micros",riskMicros); put("created_ms",nowMs)
            }
            db.insertOrThrow("intents",null,values)
            db.setTransactionSuccessful()
            return ReservedOrder(key,clientId)
        } finally { db.endTransaction() }
    }
}
