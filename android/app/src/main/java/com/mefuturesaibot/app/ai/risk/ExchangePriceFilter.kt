package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal
import java.math.RoundingMode

internal object ExchangePriceFilter {
    fun round(price: BigDecimal, tick: BigDecimal, minimum: BigDecimal,
              maximum: BigDecimal, upward: Boolean): BigDecimal? {
        if (price.signum() <= 0 || tick.signum() <= 0 ||
            minimum.signum() < 0 || maximum.signum() <= 0 || maximum < minimum) return null
        val rounded = price.divide(tick,0,if (upward) RoundingMode.CEILING else RoundingMode.FLOOR).multiply(tick)
        return rounded.takeIf { it.signum() > 0 && it >= minimum && it <= maximum }
    }
}
