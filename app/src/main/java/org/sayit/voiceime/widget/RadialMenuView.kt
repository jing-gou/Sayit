package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.*
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

enum class RadialMenuAction {
    SETTINGS, CLIPBOARD, INPUT_MODE, SWITCH_IME
}

class RadialMenuView(context: Context, private val ballCenterX: Float, private val ballCenterY: Float, private val ballRadius: Float) : View(context) {

    var onAction: ((RadialMenuAction) -> Unit)? = null
    var onDismissRequest: (() -> Unit)? = null

    private var selectedSlice = -1
    private var touchDown = false

    private val sliceRadius = ballRadius + dp(70f)
    private val innerRadius = ballRadius + dp(8f) // gap between ball and menu

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(80, 255, 255, 255)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(22f)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }

    private data class Slice(
        val action: RadialMenuAction,
        val startAngle: Float, // degrees, 0=right, counter-clockwise
        val icon: String,
        val label: String,
        val color: Int
    )

    // Layout: top=IME, left=settings, right=clipboard, bottom=input
    private val slices = listOf(
        Slice(RadialMenuAction.SWITCH_IME,  45f,  "⌨",  "输入法", Color.parseColor("#FF9800")),
        Slice(RadialMenuAction.SETTINGS,    135f,  "⚙",  "设置",   Color.parseColor("#607D8B")),
        Slice(RadialMenuAction.CLIPBOARD,   225f,  "📋", "剪切板", Color.parseColor("#4CAF50")),
        Slice(RadialMenuAction.INPUT_MODE,  315f,  "🔢", "符号",   Color.parseColor("#2196F3"))
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw dim background
        canvas.drawColor(Color.argb(60, 0, 0, 0))
        for (i in slices.indices) {
            drawSlice(canvas, slices[i], i == selectedSlice)
        }
    }

    private fun drawSlice(canvas: Canvas, slice: Slice, selected: Boolean) {
        val outerRect = RectF(
            ballCenterX - sliceRadius,
            ballCenterY - sliceRadius,
            ballCenterX + sliceRadius,
            ballCenterY + sliceRadius
        )
        val innerRect = RectF(
            ballCenterX - innerRadius,
            ballCenterY - innerRadius,
            ballCenterX + innerRadius,
            ballCenterY + innerRadius
        )

        bgPaint.color = if (selected) {
            Color.argb(200, Color.red(slice.color), Color.green(slice.color), Color.blue(slice.color))
        } else {
            Color.argb(130, Color.red(slice.color), Color.green(slice.color), Color.blue(slice.color))
        }

        val path = Path().apply {
            // Outer arc
            arcTo(outerRect, slice.startAngle, 89.99f, true)
            // Inner arc (reverse)
            arcTo(innerRect, slice.startAngle + 90f, -89.99f, false)
            close()
        }
        canvas.drawPath(path, bgPaint)
        canvas.drawPath(path, borderPaint)

        // Icon and label at center of the annular sector
        val midAngle = Math.toRadians((slice.startAngle + 45f).toDouble())
        val iconR = (innerRadius + sliceRadius) * 0.5f
        val labelR = (innerRadius + sliceRadius) * 0.75f

        val iconX = (ballCenterX + cos(midAngle) * iconR).toFloat()
        val iconY = (ballCenterY - sin(midAngle) * iconR).toFloat() + sp(7f) // Note: canvas Y is inverted
        canvas.drawText(slice.icon, iconX, iconY, iconPaint)

        val labelX = (ballCenterX + cos(midAngle) * labelR).toFloat()
        val labelY = (ballCenterY - sin(midAngle) * labelR).toFloat() + sp(4f)
        canvas.drawText(slice.label, labelX, labelY, labelPaint)
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
                    updateSelection(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selectedSlice >= 0) {
                    onAction?.invoke(slices[selectedSlice].action)
                } else {
                    // Tap center (ball) or dim background — collapse menu
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
        val dy = -(y - ballCenterY) // invert Y for math coordinates
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
            if (end > 360) {
                angle >= s.startAngle || angle < (end - 360)
            } else {
                angle >= s.startAngle && angle < end
            }
        }.coerceAtLeast(-1)
    }

    fun show() {
        visibility = VISIBLE
        alpha = 0f
        scaleX = 0.7f
        scaleY = 0.7f
        animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(150)
            .start()
    }

    fun dismiss() {
        animate()
            .alpha(0f).scaleX(0.7f).scaleY(0.7f)
            .setDuration(100)
            .withEndAction {
                visibility = GONE
            }
            .start()
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value,
            context.resources.displayMetrics
        )
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, value,
            context.resources.displayMetrics
        )
    }
}
