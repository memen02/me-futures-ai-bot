package com.mefuturesaibot.app.ai.risk

internal object SmartTrailingStopEngine {
    // Returns a tighter candidate only; exchange acknowledgement is required
    // before replacing the recorded protective stop.
    fun tighten(side: TradeSide, entry: Double, currentStop: Double,
                mark: Double, atr: Double, initialRiskDistance: Double,
                atrMultiplier: Double = 2.0): Double? {
        if (listOf(entry,currentStop,mark,atr,initialRiskDistance,atrMultiplier)
                .any { !it.isFinite() || it <= 0 }) return null
        val profit = if (side == TradeSide.LONG) mark-entry else entry-mark
        if (profit < initialRiskDistance) return null
        val candidate = if (side == TradeSide.LONG) mark-atr*atrMultiplier else mark+atr*atrMultiplier
        if (!candidate.isFinite() || candidate <= 0) return null
        return candidate.takeIf {
            if (side == TradeSide.LONG) it > currentStop && it < mark
            else it < currentStop && it > mark
        }
    }
}
