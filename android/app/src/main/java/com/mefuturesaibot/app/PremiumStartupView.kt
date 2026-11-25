package com.mefuturesaibot.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * Decorative startup animation.
 *
 * No network requests, trading decisions,
 * background workers or artificial progress.
 */
class PremiumStartupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG
    )

    private val robot = requireNotNull(
        context.getDrawable(R.drawable.ic_robot_gold)
    ).mutate()

    private val gold = Color.rgb(240, 185, 11)
    private val lightGold = Color.rgb(255, 212, 73)
    private val mutedGold = Color.rgb(111, 87, 36)
    private val darkGold = Color.rgb(46, 39, 25)

    private val outerRing = RectF(
        14f, 14f, 186f, 186f
    )

    private val innerRing = RectF(
        25f, 25f, 175f, 175f
    )

    private val face = RectF(
        78f, 85.4f, 122f, 120.8f
    )

    private val visorClip = Path().apply {
        addRoundRect(
            face, 11f, 11f,
            Path.Direction.CW
        )
    }

    private val ringDashes = DashPathEffect(
        floatArrayOf(3.3f, 9.8f), 0f
    )

    private val halo = RadialGradient(
        100f,
        100f,
        96f,
        intArrayOf(
            Color.argb(28, 240, 185, 11),
            Color.TRANSPARENT
        ),
        null,
        Shader.TileMode.CLAMP
    )

    private var phase = 0f
    private var requested = false
    private var lastFrame = 0L

    private val animator = ValueAnimator.ofFloat(
        0f, 1f
    ).apply {
        duration = 24000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()

        addUpdateListener { animation ->
            val now = SystemClock.elapsedRealtime()

            if (now - lastFrame >= 33L) {
                lastFrame = now
                phase = animation.animatedValue as Float
                invalidate()
            }
        }
    }

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setLoading(enabled: Boolean) {
        if (requested == enabled) return
        requested = enabled
        updateAnimation()
    }

    private fun updateAnimation() {
        val shouldAnimate =
            requested &&
            isAttachedToWindow &&
            isShown &&
            windowVisibility == VISIBLE &&
            ValueAnimator.areAnimatorsEnabled()

        if (shouldAnimate) {
            if (!animator.isStarted) {
                lastFrame = 0L
                animator.start()
            }
        } else {
            animator.cancel()
            phase = 0f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        phase = 0f
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(
        visibility: Int
    ) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    override fun onVisibilityAggregated(
        isVisible: Boolean
    ) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            updateAnimation()
        } else {
            animator.cancel()
            phase = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val availableWidth =
            width - paddingLeft - paddingRight

        val availableHeight =
            height - paddingTop - paddingBottom

        val size = min(
            availableWidth,
            availableHeight
        ).toFloat()

        if (size <= 0f) return

        val scale = size / 200f

        canvas.save()

        canvas.translate(
            paddingLeft +
                (availableWidth - size) / 2f,
            paddingTop +
                (availableHeight - size) / 2f
        )

        canvas.scale(scale, scale)

        drawHalo(canvas)
        drawRings(canvas)
        drawRobot(canvas)

        canvas.restore()
    }

    private fun drawHalo(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = halo

        canvas.drawCircle(
            100f, 100f, 96f, paint
        )

        paint.shader = null
    }

    private fun drawRings(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.8f
        paint.color = darkGold

        canvas.drawOval(outerRing, paint)
        canvas.drawOval(innerRing, paint)

        // Three complete rotations per cycle.
        val outerAngle = phase * 1080f

        paint.strokeWidth = 1.6f
        paint.color = gold

        canvas.drawArc(
            outerRing,
            outerAngle,
            74f,
            false,
            paint
        )

        paint.strokeWidth = 0.9f
        paint.color = lightGold

        canvas.drawArc(
            outerRing,
            outerAngle + 180f,
            25f,
            false,
            paint
        )

        // Two reverse rotations per cycle.
        val innerAngle = -phase * 720f

        paint.strokeWidth = 1f
        paint.color = mutedGold

        canvas.save()
        canvas.rotate(innerAngle, 100f, 100f)

        paint.pathEffect = ringDashes
        canvas.drawOval(innerRing, paint)
        paint.pathEffect = null

        canvas.restore()

        paint.color = gold
        paint.strokeWidth = 1.2f

        canvas.drawArc(
            innerRing,
            innerAngle,
            65f,
            false,
            paint
        )

        paint.style = Paint.Style.FILL
    }

    private fun drawRobot(canvas: Canvas) {
        val wave = sin(
            phase.toDouble() *
                Math.PI * 2.0 * 8.0
        ).toFloat()

        val breathing = 1f + wave * 0.018f

        canvas.save()

        canvas.scale(
            breathing,
            breathing,
            100f,
            100f
        )

        robot.setBounds(
            34, 34, 166, 166
        )

        robot.draw(canvas)

        if (animator.isRunning) {
            val scanPhase = (phase * 8f) % 1f

            val scanY =
                face.top + face.height() * scanPhase

            val opacity = (
                sin(
                    scanPhase.toDouble() * Math.PI
                ) * 145.0
            ).toInt().coerceIn(0, 145)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.9f
            paint.color = lightGold
            paint.alpha = opacity

            canvas.save()
            canvas.clipPath(visorClip)

            canvas.drawLine(
                face.left,
                scanY,
                face.right,
                scanY,
                paint
            )

            canvas.restore()

            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }

        canvas.restore()
    }
}
