package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.*

enum class RadialMenuAction {
    SETTINGS, CLIPBOARD, INPUT_MODE, SWITCH_IME
}

class RadialMenuView(
    context: Context,
    private val ballCenterX: Float,
    private val ballCenterY: Float,
    private val ballRadius: Float
) : View(context) {

    var onAction: ((RadialMenuAction) -> Unit)? = null
    var onDismissRequest: (() -> Unit)? = null

    private var selectedSlice = -1
    private var touchDown = false
    private var revealProgress = 0f

    private val sliceRadius = ballRadius + dp(72f)
    private val innerRadius = ballRadius + dp(10f)

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = Color.argb(100, 255, 255, 255)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(20f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private data class Slice(
        val action: RadialMenuAction,
        val startAngle: Float,
        val icon: String,
        val label: String,
        val color: Int
    )

    private val slices = listOf(
        Slice(RadialMenuAction.SWITCH_IME, 45f, "⌨", "输入法", Color.parseColor("#6366F1")),
        Slice(RadialMenuAction.SETTINGS, 135f, "⚙", "设置", Color.parseColor("#475569")),
        Slice(RadialMenuAction.CLIPBOARD, 225f, "📋", "剪贴板", Color.parseColor("#10B981")),
        Slice(RadialMenuAction.INPUT_MODE, 315f, "🔤", "符号", Color.parseColor("#8B5CF6"))
    )

    override fun onDraw(canvas: Canvas) {
        try {
            if (width <= 0 || height <= 0) return
            dimPaint.color = Color.argb((revealProgress.coerceIn(0f, 1f) * 140).toInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            for (i in slices.indices) {
                drawSlice(canvas, slices[i], i == selectedSlice, i)
            }

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2f)
                color = Color.argb((revealProgress.coerceIn(0f, 1f) * 160).toInt(), 255, 255, 255)
            }
            val ringR = (innerRadius - dp(2f)).coerceAtLeast(1f)
            canvas.drawCircle(ballCenterX, ballCenterY, ringR, ringPaint)
        } catch (e: Exception) {
            android.util.Log.e("RadialMenu", "onDraw error", e)
        }
    }

    private fun drawSlice(canvas: Canvas, slice: Slice, selected: Boolean, index: Int) {
        val stagger = index * 0.08f
        val denom = (1f - stagger).coerceAtLeast(0.01f)
        val progress = ((revealProgress.coerceIn(0f, 1f) - stagger) / denom).coerceIn(0f, 1f)
        if (progress <= 0f) return

        val outerR = innerRadius + (sliceRadius - innerRadius) * progress
        if (outerR <= innerRadius + 0.5f) return
        val outerRect = RectF(
            ballCenterX - outerR, ballCenterY - outerR,
            ballCenterX + outerR, ballCenterY + outerR
        )
        val innerRect = RectF(
            ballCenterX - innerRadius, ballCenterY - innerRadius,
            ballCenterX + innerRadius, ballCenterY + innerRadius
        )

        val baseAlpha = if (selected) 220 else 160
        slicePaint.color = Color.argb(
            (baseAlpha * progress).toInt(),
            Color.red(slice.color),
            Color.green(slice.color),
            Color.blue(slice.color)
        )

        val path = Path().apply {
            arcTo(outerRect, slice.startAngle, 89.99f, true)
            arcTo(innerRect, slice.startAngle + 90f, -89.99f, false)
            close()
        }
        canvas.drawPath(path, slicePaint)
        if (selected) canvas.drawPath(path, borderPaint)

        if (progress > 0.5f) {
            val midAngle = Math.toRadians((slice.startAngle + 45f).toDouble())
            val iconR = (innerRadius + outerR) * 0.52f
            val labelR = (innerRadius + outerR) * 0.76f
            val iconX = (ballCenterX + cos(midAngle) * iconR).toFloat()
            val iconY = (ballCenterY - sin(midAngle) * iconR).toFloat() + sp(6f)
            canvas.drawText(slice.icon, iconX, iconY, iconPaint)
            val labelX = (ballCenterX + cos(midAngle) * labelR).toFloat()
            val labelY = (ballCenterY - sin(midAngle) * labelR).toFloat() + sp(3f)
            canvas.drawText(slice.label, labelX, labelY, labelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDown = true
                updateSelection(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchDown) {
                    val prev = selectedSlice
                    updateSelection(event.x, event.y)
                    if (selectedSlice >= 0 && selectedSlice != prev) {
                        performHaptic(HAPTIC_SELECT)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selectedSlice >= 0) {
                    performHaptic(HAPTIC_CONFIRM)
                    onAction?.invoke(slices[selectedSlice].action)
                } else {
                    onDismissRequest?.invoke()
                }
                selectedSlice = -1
                touchDown = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelection(x: Float, y: Float) {
        val dx = x - ballCenterX
        val dy = -(y - ballCenterY)
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < innerRadius || dist > sliceRadius) {
            selectedSlice = -1
            return
        }
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).let {
            if (it < 0) it + 360 else it
        }
        selectedSlice = slices.indexOfFirst { s ->
            val end = s.startAngle + 90f
            if (end > 360) angle >= s.startAngle || angle < (end - 360)
            else angle >= s.startAngle && angle < end
        }.coerceAtLeast(-1)
    }

    fun show() {
        visibility = VISIBLE
        revealProgress = 0f
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            addUpdateListener {
                revealProgress = (it.animatedValue as Float).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
    }

    fun dismiss() {
        android.animation.ValueAnimator.ofFloat(revealProgress, 0f).apply {
            duration = 160
            addUpdateListener {
                revealProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibility = GONE
                }
            })
            start()
        }
    }

    private fun performHaptic(type: Int) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (type) {
                    HAPTIC_SELECT -> vibrator.vibrate(VibrationEffect.createOneShot(10, 80))
                    HAPTIC_CONFIRM -> vibrator.vibrate(VibrationEffect.createOneShot(22, 160))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("RadialMenu", "haptic failed", e)
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        private const val HAPTIC_SELECT = 0
        private const val HAPTIC_CONFIRM = 1
    }
}
