package com.mefuturesaibot.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.mefuturesaibot.app.exchange.Candle
import kotlin.math.max
import kotlin.math.min

class ClosePriceChart(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    private val up = context.getColor(R.color.me_buy)
    private val down = context.getColor(R.color.me_sell)
    private val grid = context.getColor(R.color.me_border)
    private var source: List<Candle> = emptyList()
    private var bars: List<Candle> = emptyList()
    private var high = 0.0
    private var low = 0.0
    private var volume = 0.0
    private var interval = ""
    var onSelect: ((Int) -> Unit)? = null
    var selected = -1
    var overlays = true
        set(value) { field = value; invalidate() }
    private var markTime: Long? = null
    private var markSide = "WAIT"
    internal fun mark(signal: com.mefuturesaibot.app.exchange.SmaSignal) {
        val time = signal.intervals.firstOrNull()?.closedAtMs
        if (markTime != time || markSide != signal.side.name) {
            markTime = time
            markSide = signal.side.name
            invalidate()
        }
    }
    private var tapX = 0f
    private var readings = emptyList<CandleReading?>()
    var rangeLabel = ""
        private set

    fun show(candles: List<Candle>, timeframe: String) {
        if (source === candles && interval == timeframe) return
        val previous = if (interval == timeframe) source.getOrNull(selected)?.openTime else null
        source = candles
        interval = timeframe
        bars = source.takeLast(60)
        selected = previous?.let { at -> source.indexOfFirst { it.openTime == at } } ?: -1
        if (selected !in (source.size - bars.size) until source.size) selected = source.lastIndex - 1
        readings = source.indices.map { CandleReading.at(source, it) }
        if (bars.isEmpty()) {
            rangeLabel = ""
            contentDescription = null
        } else {
            val top = bars.maxOf { it.high }
            val bottom = bars.minOf { it.low }
            high = top.toDouble()
            low = bottom.toDouble()
            volume = bars.maxOf { it.volume }.toDouble()
            rangeLabel = "Min ${bottom.stripTrailingZeros().toPlainString()} · Maks ${top.stripTrailingZeros().toPlainString()}"
            contentDescription = "${bars.size} candle $interval dan volume. $rangeLabel"
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) { tapX = event.x; return true }
        if (event.action == MotionEvent.ACTION_UP && bars.isNotEmpty()) {
            val pad = 4 * dp
            val index = ((event.x - pad) * bars.size / (width - 2 * pad)).toInt().coerceIn(0, bars.lastIndex)
            if (kotlin.math.abs(event.x - tapX) < 16 * dp) {
                selected = source.size - bars.size + index
                performClick()
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (selected in source.indices) onSelect?.invoke(selected)
        return true
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
        paint.color = grid
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
            val original = source.size - bars.size + index
            if (overlays) {
                val label = readings[original]?.label
                if (label == "Breakout naik" || label == "Breakout turun") {
                    paint.color = if (label == "Breakout naik") up else down
                    canvas.drawCircle(x, if (label == "Breakout naik") y(bar.low.toDouble()) + 3 * dp else y(bar.high.toDouble()) - 3 * dp, 2 * dp, paint)
                }
            }
            val nextOpen = source.getOrNull(original + 1)?.openTime
            val marked = markTime
            if (overlays && marked != null && nextOpen != null && marked >= bar.openTime && marked < nextOpen) {
                paint.color = context.getColor(R.color.me_accent)
                paint.textSize = 11 * dp
                canvas.drawText(markSide, x.coerceAtMost(width - 34 * dp), chartHeight + 13 * dp, paint)
            }
            if (original == selected) {
                paint.color = context.getColor(R.color.me_accent)
                canvas.drawLine(x, pad, x, height - pad, paint)
            }
            if (volume > 0) {
                val h = (bar.volume.toDouble() / volume * height * 0.17).toFloat()
                paint.alpha = 155
                canvas.drawRect(x - half, height - pad - h, x + half, height - pad, paint)
                paint.alpha = 255
            }
        }
        if (overlays) {
            val reading = readings.getOrNull(selected) ?: return
            paint.textSize = 10 * dp
            for ((level, name) in listOf(reading.support to "S", reading.resistance to "R", reading.fib to "Fib 61,8%")) {
                val value = level?.toDouble() ?: continue
                if (value !in low..high) continue
                paint.color = context.getColor(if (name.startsWith("Fib")) R.color.me_accent else R.color.me_muted)
                paint.alpha = 150
                val fy = y(value)
                val start = pad + step * (selected - (source.size - bars.size) + 0.5f)
                canvas.drawLine(start, fy, width - pad, fy, paint)
                canvas.drawText(name, pad, fy.coerceAtLeast(12 * dp), paint)
                paint.alpha = 255
            }
        }
    }
}
