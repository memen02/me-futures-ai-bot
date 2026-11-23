package com.mefuturesaibot.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
    private var lastX = 0f
    private var dragged = false
    private var visibleCount = 60
    private var endOffset = 0
    private var offsetAtTouch = 0
    private var firstVisible = 0
    private var pinned = false
    private var scaleStart = 60
    private var scaleAmount = 1f
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            dragged = true
            scaleStart = visibleCount
            scaleAmount = 1f
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleAmount *= detector.scaleFactor
            visibleCount = (scaleStart / scaleAmount).toInt().coerceIn(12, 60)
            window()
            return true
        }
    })
    private var readings = emptyList<CandleReading?>()
    var rangeLabel = ""
        private set

    fun show(candles: List<Candle>, timeframe: String) {
        if (source === candles && interval == timeframe) return
        val previous = if (pinned && interval == timeframe) source.getOrNull(selected)?.openTime else null
        if (interval != timeframe) { endOffset = 0; visibleCount = 60 }
        source = candles
        interval = timeframe
        selected = previous?.let { at -> source.indexOfFirst { it.openTime == at } } ?: -1
        pinned = selected >= 0
        if (!pinned) selected = (source.lastIndex - 1).coerceAtLeast(0)
        readings = source.indices.map { CandleReading.at(source, it) }
        window()
    }

    private fun window() {
        endOffset = endOffset.coerceIn(0, maxOf(0, source.size - visibleCount))
        val end = source.size - endOffset
        firstVisible = maxOf(0, end - visibleCount)
        bars = source.subList(firstVisible, end)
        if (bars.isEmpty()) {
            rangeLabel = ""
            contentDescription = null
        } else {
            val top = bars.maxOf { it.high }
            val bottom = bars.minOf { it.low }
            high = top.toDouble()
            low = bottom.toDouble()
            volume = bars.maxOf { it.volume }.toDouble()
            rangeLabel = "Min ${bottom.plain()} · Maks ${top.plain()}"
            contentDescription = "${bars.size} candle $interval dan volume. $rangeLabel"
        }
        invalidate()
    }

    fun selectCandle(index: Int) {
        if (index !in source.indices) return
        pinned = true
        selected = index
        if (index !in firstVisible until firstVisible + bars.size) {
            endOffset = (source.lastIndex - index - visibleCount / 2).coerceAtLeast(0)
            window()
        }
        onSelect?.invoke(index)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { tapX = event.x; lastX = event.y; offsetAtTouch = endOffset; dragged = false }
            MotionEvent.ACTION_MOVE -> if (!scale.isInProgress && event.pointerCount == 1) {
                val dx = event.x - tapX
                if (kotlin.math.abs(dx) > 8 * dp && kotlin.math.abs(dx) > kotlin.math.abs(event.y - lastX)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragged = true
                    endOffset = offsetAtTouch + (dx / maxOf(1, width) * bars.size).toInt()
                    window()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged && bars.isNotEmpty()) {
                    val pad = 4 * dp
                    val index = ((event.x - pad) * bars.size / (width - 2 * pad)).toInt().coerceIn(0, bars.lastIndex)
                    selected = firstVisible + index
                    performClick()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        selectCandle(selected)
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
            val original = firstVisible + index
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
                val start = pad + step * (selected - 20 - firstVisible + 0.5f).coerceIn(0f, bars.size.toFloat())
                canvas.drawLine(start, fy, width - pad, fy, paint)
                canvas.drawText("$name ${level.plain()}", pad, fy.coerceAtLeast(12 * dp), paint)
                paint.alpha = 255
            }
        }
    }
}
