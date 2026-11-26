package com.mefuturesaibot.app.ai.risk

internal object LiquidationProtection {
    // Liquidation estimate must come from a verified margin/tier model for
    // the proposed position. Unknown estimates must block new entries.
    fun permits(side: TradeSide, entry: Double, stop: Double, liquidation: Double?,
                bufferFraction: Double = 0.005): Boolean {
        val liq = liquidation ?: return false
        if (listOf(entry,stop,liq,bufferFraction).any { !it.isFinite() || it <= 0 } ||
            bufferFraction >= 1) return false
        val buffer = entry*bufferFraction
        return when (side) {
            TradeSide.LONG -> stop < entry && stop-liq >= buffer
            TradeSide.SHORT -> stop > entry && liq-stop >= buffer
        }
    }
}
