package com.mefuturesaibot.app.ai.execution

internal enum class ReconciliationAction { FREEZE, OBSERVE, PROTECT_POSITION, REVIEW_TERMINAL }
internal data class OrderObservation(val clientOrderId: String, val state: OrderState,
    val executedQuantity: java.math.BigDecimal, val observedAtMs: Long)

// Caller must authenticate venue/account/symbol and bind the exchange response.
// Not-found, stale, inconsistent or absent observations never authorize resubmission.
internal object OrderReconciliationEngine {
    fun assess(order: ReservedOrder, observation: OrderObservation?, nowMs: Long): ReconciliationAction {
        if (observation == null || observation.clientOrderId != order.clientOrderId ||
            observation.observedAtMs <= 0 || nowMs < observation.observedAtMs ||
            nowMs-observation.observedAtMs >= 5000 || observation.executedQuantity.signum() < 0)
            return ReconciliationAction.FREEZE
        val filled = observation.executedQuantity.signum() > 0
        return when (observation.state) {
            OrderState.ACKNOWLEDGED -> if (filled) ReconciliationAction.FREEZE else ReconciliationAction.OBSERVE
            OrderState.PARTIALLY_FILLED, OrderState.FILLED ->
                if (filled) ReconciliationAction.PROTECT_POSITION else ReconciliationAction.FREEZE
            OrderState.CANCELED -> if (filled) ReconciliationAction.PROTECT_POSITION else ReconciliationAction.REVIEW_TERMINAL
            OrderState.REJECTED -> if (filled) ReconciliationAction.FREEZE else ReconciliationAction.REVIEW_TERMINAL
            else -> ReconciliationAction.FREEZE
        }
    }
}
