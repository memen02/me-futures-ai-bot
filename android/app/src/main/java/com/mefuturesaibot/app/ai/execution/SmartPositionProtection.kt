package com.mefuturesaibot.app.ai.execution

import java.math.BigDecimal

internal enum class ProtectionAction { FREEZE, VERIFY_PROTECTION, REVIEW_MISSING_PROTECTION }
internal data class PositionProtectionSnapshot(val clientOrderId: String, val signedQuantity: BigDecimal,
    val mark: BigDecimal, val stop: BigDecimal?, val target: BigDecimal?,
    val observedAtMs: Long, val stopVerified: Boolean, val targetVerified: Boolean)

// Advisory only. Verification must bind exchange order IDs, account, symbol,
// close semantics, position side and actual trigger prices.
internal object SmartPositionProtection {
    fun assess(order: ReservedOrder, snapshot: PositionProtectionSnapshot?, nowMs: Long): ProtectionAction {
        if (snapshot == null || snapshot.clientOrderId != order.clientOrderId ||
            snapshot.observedAtMs <= 0 || nowMs < snapshot.observedAtMs ||
            nowMs-snapshot.observedAtMs >= 5000 || snapshot.signedQuantity.signum() == 0 ||
            snapshot.mark.signum() <= 0) return ProtectionAction.FREEZE
        val stop = snapshot.stop ?: return ProtectionAction.REVIEW_MISSING_PROTECTION
        val target = snapshot.target ?: return ProtectionAction.REVIEW_MISSING_PROTECTION
        val long = snapshot.signedQuantity.signum() > 0
        val valid = stop.signum() > 0 && target.signum() > 0 &&
            if (long) stop < snapshot.mark && target > snapshot.mark
            else stop > snapshot.mark && target < snapshot.mark
        return if (valid && snapshot.stopVerified && snapshot.targetVerified)
            ProtectionAction.VERIFY_PROTECTION else ProtectionAction.REVIEW_MISSING_PROTECTION
    }
}
