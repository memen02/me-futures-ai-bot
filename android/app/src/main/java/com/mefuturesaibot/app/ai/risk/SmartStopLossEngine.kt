package com.mefuturesaibot.app.ai.risk

internal object SmartStopLossEngine {
    // Structure level must be a confirmed swing from the same instrument.
    fun calculate(entry: EntryCandidate, structureLevel: Double, atr: Double,
                  atrBuffer: Double = 0.5, minimumAtrDistance: Double = 1.0): Double? {
        if (listOf(entry.price,structureLevel,atr,atrBuffer,minimumAtrDistance)
                .any { !it.isFinite() || it <= 0 }) return null
        val price = entry.price
        val stop = when (entry.side) {
            TradeSide.LONG -> {
                if (structureLevel >= price) return null
                minOf(structureLevel-atr*atrBuffer,price-atr*minimumAtrDistance)
            }
            TradeSide.SHORT -> {
                if (structureLevel <= price) return null
                maxOf(structureLevel+atr*atrBuffer,price+atr*minimumAtrDistance)
            }
        }
        return stop.takeIf { it.isFinite() && it > 0 &&
            if (entry.side == TradeSide.LONG) it < price else it > price }
    }
}
