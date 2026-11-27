package com.mefuturesaibot.app.ai.execution

internal enum class SubmissionResult { NOT_CLAIMED, RECONCILIATION_REQUIRED }

// No transport invocation until durable payload, authenticated account ownership,
// serialized kill switch and exchange reconciliation are integrated.
// Keep the API fail closed even if a caller accidentally wires it into a service.
internal class SmartOrderExecutionEngine(private val journal: OrderJournal) {
    @Suppress("UNUSED_PARAMETER")
    fun submit(order: ReservedOrder, sendOnce: (String) -> Unit): SubmissionResult =
        SubmissionResult.NOT_CLAIMED
}
