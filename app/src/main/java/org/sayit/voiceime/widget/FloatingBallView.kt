package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import org.sayit.voiceime.gesture.Direction
import org.sayit.voiceime.gesture.GestureAction
import org.sayit.voiceime.gesture.GestureState
import kotlin.math.*

interface FloatingBallListener {
    fun onGestureAction(action: GestureAction)
    fun onVoiceStateChanged(isListening: Boolean)
}

class FloatingBallView(context: Context, val config: FloatingBallConfig) : View(context) {

    var listener: FloatingBallListener? = null

    private var gestureState = GestureState.IDLE
    private var currentDirection: Direction? = null

    private var pressStartX = 0f
    private var pressStartY = 0f
    private var fingerX = 0f
    private var fingerY = 0f

    private var breathScale = 1f
    private var breathGlowAlpha = 0.3f
    private var wavePhase = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private var isListening = false
    private var hasMovedDuringDrag = false

    private var trailPath = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
    }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = sp(16f)
        textAlign = Paint.Align.CENTER
    }

    private val breathAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = android.animation.ValueAnimator.INFINITE
        repeatMode = android.animation.ValueAnimator.REVERSE
        addUpdateListener { anim ->
            breathScale = 0.97f + 0.06f * anim.animatedFraction
            breathGlowAlpha = 0.3f + 0.2f * anim.animatedFraction
            invalidate()
        }
    }

    private val waveAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener { anim ->
            wavePhase = anim.animatedFraction
            if (isListening) invalidate()
        }
    }

    init {
        try {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            isClickable = true
            isFocusable = true
            breathAnimator.start()
        } catch (e: Exception) {
            android.util.Log.e("FloatingBall", "Init error", e)
        }
    }

    fun setListeningState(listening: Boolean) {
        try {
            isListening = listening
            if (listening) {
                waveAnimator.start()
                performHaptic(HAPTIC_LONG_PRESS)
            } else {
                waveAnimator.cancel()
                wavePhase = 0f
            }
            invalidate()
        } catch (e: Exception) {
            android.util.Log.e("FloatingBall", "setListeningState error", e)
        }
    }

    override fun onDraw(canvas: Canvas) {
        try {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            drawGlow(canvas, cx, cy)
            drawBall(canvas, cx, cy)
            if (isListening) drawSoundWaves(canvas, cx, cy)
            drawIcon(canvas, cx, cy)
            if (gestureState == GestureState.PRESSING) drawDirectionIndicators(canvas, cx, cy)
            if (config.trailEnabled && gestureState == GestureState.SWIPING_DELETE) drawGestureTrail(canvas, cx, cy)
        } catch (e: Throwable) {
            android.util.Log.e("FloatingBall", "onDraw CRASH", e)
        }
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float) {
        val radius = config.glowRadius * breathScale
        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            getCurrentColor(breathGlowAlpha),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, glowPaint)
    }

    private fun drawBall(canvas: Canvas, cx: Float, cy: Float) {
        ballPaint.shader = RadialGradient(
            cx - config.ballRadius * 0.3f,
            cy - config.ballRadius * 0.3f,
            config.ballRadius * 2f,
            getCurrentColor(1f),
            darkenColor(getCurrentColor(1f)),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, config.ballRadius * breathScale, ballPaint)
    }

    private fun drawSoundWaves(canvas: Canvas, cx: Float, cy: Float) {
        val waveCount = 3
        for (i in 0 until waveCount) {
            val phase = (wavePhase + i * 0.33f) % 1f
            val radius = config.ballRadius + phase * config.glowRadius * 0.8f
            val alpha = ((1f - phase) * 100).toInt()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 255, 82, 82)
                style = Paint.Style.STROKE
                strokeWidth = dp(2f) * (1f - phase)
            }
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float) {
        val icon = when {
            isListening -> "●"
            gestureState == GestureState.SWIPING_DELETE -> "✕"
            gestureState == GestureState.SWIPING_GESTURE -> getDirectionIcon()
            else -> "🎤"
        }
        val textY = cy - (iconPaint.descent() + iconPaint.ascent()) / 2f
        canvas.drawText(icon, cx, textY, iconPaint)
    }

    private fun getDirectionIcon(): String {
        return when (currentDirection) {
            Direction.LEFT -> "←"
            Direction.RIGHT -> "→"
            Direction.UP -> "↑"
            Direction.DOWN -> "↓"
            null -> "●"
        }
    }

    private fun drawDirectionIndicators(canvas: Canvas, cx: Float, cy: Float) {
        val indicatorRadius = config.ballRadius + dp(20f)
        val indicatorSize = dp(6f)

        val directions = listOf(
            Direction.LEFT to (cx - indicatorRadius to cy),
            Direction.RIGHT to (cx + indicatorRadius to cy),
            Direction.UP to (cx to cy - indicatorRadius),
            Direction.DOWN to (cx to cy + indicatorRadius)
        )

        val colors = mapOf(
            Direction.LEFT to config.deleteColor,
            Direction.RIGHT to 0xFF4CAF50.toInt(),
            Direction.UP to config.gestureColor,
            Direction.DOWN to config.loadingColor
        )

        directions.forEach { (dir, pos) ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors[dir]!!
                alpha = 120
            }
            canvas.drawCircle(pos.first, pos.second, indicatorSize, paint)
        }
    }

    private fun drawGestureTrail(canvas: Canvas, cx: Float, cy: Float) {
        trailPath.reset()
        trailPath.moveTo(cx, cy)
        trailPath.lineTo(fingerX, fingerY)

        trailPaint.shader = LinearGradient(
            cx, cy, fingerX, fingerY,
            getCurrentColor(1f), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(trailPath, trailPaint)
    }

    private fun getCurrentColor(alphaMultiplier: Float): Int {
        val color = when {
            isListening -> config.listeningColor
            gestureState == GestureState.SWIPING_DELETE -> config.deleteColor
            gestureState == GestureState.SWIPING_GESTURE -> config.gestureColor
            gestureState == GestureState.DRAGGING -> config.gestureColor
            else -> config.idleColor
        }
        val alpha = (Color.alpha(color) * alphaMultiplier).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            android.util.Log.d("FloatingBall", "onTouchEvent action=${event.action}")
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    android.util.Log.d("FloatingBall", "ACTION_DOWN start")
                    pressStartX = event.rawX
                    pressStartY = event.rawY
                    fingerX = event.rawX
                    fingerY = event.rawY
                    gestureState = GestureState.PRESSING
                    hasMovedDuringDrag = false

                    longPressRunnable = Runnable {
                        try {
                            if (gestureState == GestureState.PRESSING) {
                                gestureState = GestureState.LONG_PRESSING
                                android.util.Log.d("FloatingBall", "Long press triggered!")
                                listener?.onGestureAction(GestureAction.LongPressStart)
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("FloatingBall", "longPressRunnable error", e)
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, config.longPressTimeout)
                    try { performHaptic(HAPTIC_TAP) } catch (e: Throwable) {
                        android.util.Log.e("FloatingBall", "haptic error", e)
                    }
                    invalidate()
                    android.util.Log.d("FloatingBall", "ACTION_DOWN done")
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    fingerX = event.rawX
                    fingerY = event.rawY
                    val dx = fingerX - pressStartX
                    val dy = fingerY - pressStartY
                    val distance = sqrt(dx * dx + dy * dy)

                    when (gestureState) {
                        GestureState.PRESSING -> {
                            if (distance > config.gestureThreshold) {
                                longPressRunnable?.let { handler.removeCallbacks(it) }
                                val absDx = abs(dx)
                                val absDy = abs(dy)

                                if (absDx > absDy && dx < 0) {
                                    gestureState = GestureState.SWIPING_DELETE
                                    currentDirection = Direction.LEFT
                                    listener?.onGestureAction(GestureAction.Swipe(Direction.LEFT))
                                } else {
                                    gestureState = GestureState.DRAGGING
                                    hasMovedDuringDrag = true
                                    listener?.onGestureAction(GestureAction.DragStart(pressStartX, pressStartY))
                                }
                            }
                        }

                        GestureState.LONG_PRESSING -> {
                            if (distance > config.gestureThreshold * 1.5f) {
                                gestureState = GestureState.SWIPING_GESTURE
                                currentDirection = determineDirection(dx, dy)
                                listener?.onGestureAction(GestureAction.Swipe(currentDirection!!))
                            }
                        }

                        GestureState.SWIPING_DELETE -> {
                            val progress = calculateDeleteProgress(dx, dy)
                            listener?.onGestureAction(GestureAction.SwipeProgress(Direction.LEFT, progress))
                        }

                        GestureState.SWIPING_GESTURE -> {
                            val progress = calculateGestureProgress(dx, dy, currentDirection!!)
                            listener?.onGestureAction(GestureAction.SwipeProgress(currentDirection!!, progress))
                        }

                        GestureState.DRAGGING -> {
                            listener?.onGestureAction(GestureAction.DragMove(fingerX, fingerY))
                        }

                        else -> {}
                    }
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    when (gestureState) {
                        GestureState.LONG_PRESSING -> {
                            listener?.onGestureAction(GestureAction.LongPressEnd)
                            listener?.onVoiceStateChanged(false)
                            setListeningState(false)
                        }
                        GestureState.SWIPING_DELETE -> {
                            listener?.onGestureAction(GestureAction.SwipeComplete)
                        }
                        GestureState.SWIPING_GESTURE -> {
                            listener?.onGestureAction(GestureAction.SwipeComplete)
                        }
                        GestureState.DRAGGING -> {
                            listener?.onGestureAction(GestureAction.DragEnd(0f, 0f))
                        }
                        GestureState.PRESSING -> {
                            try { performHaptic(HAPTIC_TAP) } catch (_: Throwable) {}
                        }
                        else -> {}
                    }

                    gestureState = GestureState.IDLE
                    currentDirection = null
                    trailPath.reset()
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        } catch (e: Throwable) {
            android.util.Log.e("FloatingBall", "onTouchEvent CRASH", e)
            return true
        }
    }

    private fun determineDirection(dx: Float, dy: Float): Direction {
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDx > absDy && dx < 0 -> Direction.LEFT
            absDx > absDx && dx > 0 -> Direction.RIGHT
            absDy > absDx && dy < 0 -> Direction.UP
            else -> Direction.DOWN
        }
    }

    private fun calculateDeleteProgress(dx: Float, dy: Float): Float {
        val maxDistance = config.gestureThreshold * 8f
        return (abs(dx) / maxDistance).coerceIn(0f, 1f)
    }

    private fun calculateGestureProgress(dx: Float, dy: Float, direction: Direction): Float {
        val distance = when (direction) {
            Direction.LEFT, Direction.RIGHT -> abs(dx)
            Direction.UP, Direction.DOWN -> abs(dy)
        }
        val maxDistance = config.gestureThreshold * 8f
        return (distance / maxDistance).coerceIn(0f, 1f)
    }

    private fun performHaptic(type: Int) {
        if (!config.hapticEnabled) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val duration = when (type) {
            HAPTIC_LONG_PRESS -> 50L
            HAPTIC_SWIPE -> 20L
            HAPTIC_TAP -> 10L
            else -> 30L
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
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

    private fun darkenColor(color: Int): Int {
        val factor = 0.7f
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnimator.cancel()
        waveAnimator.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val HAPTIC_TAP = 0
        private const val HAPTIC_LONG_PRESS = 1
        private const val HAPTIC_SWIPE = 2
    }
}