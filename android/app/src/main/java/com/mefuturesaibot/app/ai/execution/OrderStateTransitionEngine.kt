package com.mefuturesaibot.app.ai.execution

import android.content.ContentValues

internal object OrderStateTransitionEngine {
    fun allowed(from: OrderState, to: OrderState): Boolean = when (from) {
        OrderState.RESERVED -> to == OrderState.SUBMITTING
        OrderState.SUBMITTING, OrderState.UNKNOWN -> to in setOf(OrderState.UNKNOWN,
            OrderState.ACKNOWLEDGED, OrderState.PARTIALLY_FILLED, OrderState.FILLED,
            OrderState.REJECTED, OrderState.CANCELED)
        OrderState.ACKNOWLEDGED -> to in setOf(OrderState.PARTIALLY_FILLED,OrderState.FILLED,OrderState.CANCELED)
        OrderState.PARTIALLY_FILLED -> to in setOf(OrderState.FILLED,OrderState.CANCELED)
        else -> false
    }

    // Compare-and-set: only one worker can claim RESERVED for submission.
    // No transition releases risk. Exchange observations require reconciliation.
    fun transition(journal: OrderJournal, order: ReservedOrder, from: OrderState, to: OrderState): Boolean {
        if (!allowed(from,to)) return false
        val values = ContentValues().apply { put("state",to.name) }
        return journal.writableDatabase.update("intents",values,
            "intent_key=? AND client_id=? AND state=? AND released=0",
            arrayOf(order.intentKey,order.clientOrderId,from.name)) == 1
    }
}
