package com.mefuturesaibot.app.ai.execution

internal enum class SubmissionResult { NOT_CLAIMED, RECONCILIATION_REQUIRED }

// Foundation only: caller must supply a validated DEMO transport and immutable
// persisted payload. No automatic retry, including on a successful response.
internal class SmartOrderExecutionEngine(private val journal: OrderJournal) {
    fun submit(order: ReservedOrder, sendOnce: (String) -> Unit): SubmissionResult {
        val db = journal.readableDatabase
        val demo = db.rawQuery("SELECT venue FROM intents WHERE intent_key=? AND client_id=? AND released=0",
            arrayOf(order.intentKey,order.clientOrderId)).use { c ->
            c.moveToFirst() && c.getString(0) == "DEMO"
        }
        if (!demo || !OrderStateTransitionEngine.transition(journal,order,OrderState.RESERVED,OrderState.SUBMITTING))
            return SubmissionResult.NOT_CLAIMED
        try {
            sendOnce(order.clientOrderId)
        } finally {
            // A crash before this write leaves SUBMITTING, also unresolved.
            check(OrderStateTransitionEngine.transition(journal,order,OrderState.SUBMITTING,OrderState.UNKNOWN))
        }
        return SubmissionResult.RECONCILIATION_REQUIRED
    }
}
