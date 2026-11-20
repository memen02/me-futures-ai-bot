package com.mefuturesaibot.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.mefuturesaibot.app.exchange.Candle
import com.mefuturesaibot.app.exchange.CandleHistory
import kotlin.math.max
import kotlin.math.min

internal fun chartCandles(candles: List<Candle>, minutes: Int): List<Candle> {
    require(minutes in listOf(1, 5, 15))
    if (minutes == 1) return candles
    val minute = CandleHistory.INTERVAL_MS
    return candles.groupBy { it.openTime / (minute * minutes) }.mapNotNull { (bucket, group) ->
        if (group.size != minutes || group.first().openTime != bucket * minute * minutes ||
            group.last().openTime != (bucket + 1) * minute * minutes - minute) return@mapNotNull null
        Candle(group.first().openTime, group.first().open, group.maxOf { it.high }, group.minOf { it.low },
            group.last().close, group.sumOf { it.volume })
    }
}

class ClosePriceChart(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    private val up = Color.rgb(47, 221, 159)
    private val down = Color.rgb(255, 100, 133)
    private var source: List<Candle> = emptyList()
    private var bars: List<Candle> = emptyList()
    private var high = 0.0
    private var low = 0.0
    private var volume = 0.0
    private var minutes = 1
    var rangeLabel = ""
        private set

    fun selectInterval(value: Int) {
        if (minutes == value) return
        minutes = value
        rebuild()
    }

    fun show(candles: List<Candle>) {
        if (source === candles) return
        source = candles
        rebuild()
    }

    private fun rebuild() {
        bars = chartCandles(source, minutes).takeLast(60)
        if (bars.isEmpty()) {
            rangeLabel = ""
            contentDescription = null
        } else {
            val top = bars.maxOf { it.high }
            val bottom = bars.minOf { it.low }
            high = top.toDouble()
            low = bottom.toDouble()
            volume = bars.maxOf { it.volume }.toDouble()
            rangeLabel = "Min ${bottom.stripTrailingZeros().toPlainString()} · Maks ${top.stripTrailingZeros().toPlainString()} USDT"
            contentDescription = "${bars.size} candle $minutes menit dan volume BTC. $rangeLabel"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return
        val pad = 4 * dp
        val chartHeight = height * 0.73f
        val step = (width - 2 * pad) / bars.size
        val half = min(8 * dp, step * 0.34f)
        fun y(price: Double) = if (high == low) pad + chartHeight / 2
            else pad + chartHeight * ((high - price) / (high - low)).toFloat()
        paint.strokeWidth = dp
        paint.color = Color.rgb(36, 63, 86)
        for (i in 0..3) {
            val level = pad + chartHeight * i / 3
            canvas.drawLine(pad, level, width - pad, level, paint)
        }
        bars.forEachIndexed { index, bar ->
            val x = pad + step * (index + 0.5f)
            val open = y(bar.open.toDouble())
            val close = y(bar.close.toDouble())
            paint.color = if (bar.close >= bar.open) up else down
            canvas.drawLine(x, y(bar.high.toDouble()), x, y(bar.low.toDouble()), paint)
            canvas.drawRect(x - half, min(open, close), x + half, max(max(open, close), min(open, close) + dp), paint)
            if (volume > 0) {
                val h = (bar.volume.toDouble() / volume * height * 0.17).toFloat()
                paint.alpha = 155
                canvas.drawRect(x - half, height - pad - h, x + half, height - pad, paint)
                paint.alpha = 255
            }
        }
    }
}
