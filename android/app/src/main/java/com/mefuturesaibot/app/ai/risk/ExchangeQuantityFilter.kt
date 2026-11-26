package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal
import java.math.RoundingMode

internal object ExchangeQuantityFilter {
    // Caller supplies current symbol-specific filters for the intended order type.
    fun roundDown(quantity: BigDecimal, step: BigDecimal, minimum: BigDecimal,
                  maximum: BigDecimal, price: BigDecimal, minNotional: BigDecimal): BigDecimal? {
        if (listOf(quantity,step,minimum,maximum,price).any { it.signum() <= 0 } ||
            minNotional.signum() < 0 || maximum < minimum) return null
        val rounded = minOf(quantity,maximum).divide(step,0,RoundingMode.DOWN).multiply(step)
        return rounded.takeIf { it >= minimum && it <= maximum && it.multiply(price) >= minNotional }
    }
}
