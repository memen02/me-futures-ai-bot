package com.mefuturesaibot.app.ai.execution

import com.mefuturesaibot.app.ai.risk.SmartTrailingStopEngine
import com.mefuturesaibot.app.ai.risk.TradeSide
import java.math.BigDecimal
import java.math.RoundingMode

// Candidate only: keep existing protection until replacement is verified.
internal object SmartTrailingStopExecution {
    fun candidate(order: ReservedOrder, snapshot: PositionProtectionSnapshot,
                  entry: Double, atr: Double, initialRiskDistance: Double,
                  tick: BigDecimal, nowMs: Long): BigDecimal? {
        if (tick.signum() <= 0 ||
            SmartPositionProtection.assess(order,snapshot,nowMs) != ProtectionAction.VERIFY_PROTECTION) return null
        val current = snapshot.stop ?: return null
        val long = snapshot.signedQuantity.signum() > 0
        val raw = SmartTrailingStopEngine.tighten(if (long) TradeSide.LONG else TradeSide.SHORT,
            entry,current.toDouble(),snapshot.mark.toDouble(),atr,initialRiskDistance) ?: return null
        val rounded = BigDecimal.valueOf(raw).divide(tick,0,
            if (long) RoundingMode.FLOOR else RoundingMode.CEILING).multiply(tick)
        return rounded.takeIf { it.signum() > 0 &&
            (if (long) it > current && it < snapshot.mark else it < current && it > snapshot.mark) }
    }
}
