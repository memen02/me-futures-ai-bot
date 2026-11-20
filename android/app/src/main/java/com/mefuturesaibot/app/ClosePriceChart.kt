package com.mefuturesaibot.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.mefuturesaibot.app.exchange.Candle

/** One point per closed minute; the vertical scale spans this window's closing prices. */
class ClosePriceChart(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Path()
    private val fill = Path()
    private val dp = resources.displayMetrics.density
    private var source: List<Candle> = emptyList()
    private var levels = DoubleArray(0)
    var rangeLabel = ""
        private set

    fun show(candles: List<Candle>) {
        if (source === candles) return
        source = candles
        if (candles.isEmpty()) {
            rangeLabel = ""
            levels = DoubleArray(0)
        } else {
            val low = candles.minOf { it.close }
            val high = candles.maxOf { it.close }
            val spread = (high - low).toDouble()
            levels = DoubleArray(candles.size) { i ->
                if (spread == 0.0) 0.5 else (candles[i].close - low).toDouble() / spread
            }
            rangeLabel = "Min ${low.stripTrailingZeros().toPlainString()}   ·   Maks ${high.stripTrailingZeros().toPlainString()}"
        }
        contentDescription = if (levels.isEmpty()) null else "Harga penutupan ${levels.size} candle satu menit. $rangeLabel USDT."
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (levels.isEmpty()) return
        val inset = 4f * dp
        val right = width - inset
        val bottom = height - inset
        if (right <= inset || bottom <= inset) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp
        paint.color = Color.rgb(47, 56, 65)
        for (y in listOf(inset, height / 2f, bottom)) canvas.drawLine(inset, y, right, y, paint)
        line.reset()
        var lastX = inset
        var lastY = bottom
        for (i in levels.indices) {
            lastX = inset + (right - inset) * i / (levels.size - 1).coerceAtLeast(1)
            lastY = bottom - (bottom - inset) * levels[i].toFloat()
            if (i == 0) line.moveTo(lastX, lastY) else line.lineTo(lastX, lastY)
        }
        fill.set(line)
        fill.lineTo(lastX, bottom)
        fill.lineTo(inset, bottom)
        fill.close()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(25, 255, 134, 85)
        canvas.drawPath(fill, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 2 * dp
        paint.color = Color.rgb(255, 145, 91)
        canvas.drawPath(line, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(lastX, lastY, 3 * dp, paint)
    }
}
