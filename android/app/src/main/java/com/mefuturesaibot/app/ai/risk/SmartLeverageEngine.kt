package com.mefuturesaibot.app.ai.risk

import kotlin.math.ceil

internal object SmartLeverageEngine {
    // Select the lowest integer leverage that fits the allocated initial margin.
    // Exchange tier and liquidation checks remain mandatory.
    fun select(notional: Double, allocatedMargin: Double, exchangeCap: Int,
               policy: RiskPolicy): Int? {
        if (!notional.isFinite() || notional <= 0 ||
            !allocatedMargin.isFinite() || allocatedMargin <= 0 || exchangeCap < 1) return null
        val cap = minOf(exchangeCap,policy.leverageCap)
        val needed = ceil(notional/allocatedMargin).coerceAtLeast(1.0)
        if (!needed.isFinite() || needed > cap) return null
        return needed.toInt()
    }
}
