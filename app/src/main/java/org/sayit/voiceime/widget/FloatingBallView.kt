package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.*
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import org.sayit.voiceime.gesture.Direction
import org.sayit.voiceime.gesture.GestureAction
import org.sayit.voiceime.gesture.GestureState
import kotlin.math.*

interface FloatingBallListener {
    fun onGestureAction(action: GestureAction)
    fun onVoiceStateChanged(isListening: Boolean)
    fun onTap() {}
}

class FloatingBallView(context: Context, initialConfig: FloatingBallConfig) : View(context) {

    var config: FloatingBallConfig = initialConfig
        private set

    private var customBitmap: Bitmap? = null

    var listener: FloatingBallListener? = null

    private var gestureState = GestureState.IDLE
    private var currentDirection: Direction? = null
    private var swipeDx = 0f

    private var pressStartX = 0f
    private var pressStartY = 0f
    private var fingerX = 0f
    private var fingerY = 0f

    private var breathScale = 1f
    private var breathGlowAlpha = 0.35f
    private var wavePhase = 0f
    private var iconScale = 1f
    private var gestureProgress = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private var isListening = false
    private var pressStartTime = 0L

    private val trailPath = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(18f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = sp(9f)
        textAlign = Paint.Align.CENTER
    }

    private val breathAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800
        repeatCount = android.animation.ValueAnimator.INFINITE
        repeatMode = android.animation.ValueAnimator.REVERSE
        addUpdateListener {
            breathScale = 0.96f + 0.05f * it.animatedFraction
            breathGlowAlpha = 0.28f + 0.18f * it.animatedFraction
            if (gestureState == GestureState.IDLE) invalidate()
        }
    }

    private val waveAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener {
            wavePhase = it.animatedFraction
            if (isListening) invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        loadCustomBitmap()
        breathAnimator.start()
    }

    fun updateConfig(newConfig: FloatingBallConfig) {
        config = newConfig
        loadCustomBitmap(forceReload = true)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = config.overlayWindowSize.toInt().coerceAtLeast(1)
        setMeasuredDimension(size, size)
    }

    private fun loadCustomBitmap(forceReload: Boolean = false) {
        val path = config.customBallImagePath ?: run {
            customBitmap?.recycle()
            customBitmap = null
            return
        }
        if (!forceReload && customBitmap != null && customBitmap?.isRecycled != true) return
        customBitmap?.recycle()
        customBitmap = null
        try {
            customBitmap = BitmapFactory.decodeFile(path)
            if (customBitmap == null) {
                android.util.Log.w("FloatingBall", "decodeFile returned null: $path")
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingBall", "Failed to load custom ball image", e)
        }
    }

    fun ensureCustomBitmapLoaded() {
        loadCustomBitmap(forceReload = false)
    }

    fun releaseResources() {
        breathAnimator.cancel()
        waveAnimator.cancel()
        handler.removeCallbacksAndMessages(null)
        customBitmap?.recycle()
        customBitmap = null
    }

    fun setListeningState(listening: Boolean) {
        isListening = listening
        if (listening) {
            waveAnimator.start()
            animateIconBounce()
            performHaptic(HAPTIC_LONG_PRESS)
        } else {
            waveAnimator.cancel()
            wavePhase = 0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        try {
            if (width <= 0 || height <= 0) return
            val cx = width / 2f
            val cy = height / 2f

            drawGlow(canvas, cx, cy)
            if (isListening) drawSoundWaves(canvas, cx, cy)
            drawGestureRing(canvas, cx, cy)
            drawBall(canvas, cx, cy)
            drawIcon(canvas, cx, cy)
            if (gestureState == GestureState.SWIPING_DELETE || gestureState == GestureState.SWIPING_GESTURE) {
                drawGestureHints(canvas, cx, cy)
            }
            if (config.trailEnabled && gestureState == GestureState.SWIPING_DELETE) {
                drawGestureTrail(canvas, cx, cy)
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingBall", "onDraw error", e)
        }
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float) {
        val color = getAccentColor()
        val radius = config.glowRadius * breathScale * (1f + gestureProgress * 0.15f)
        glowPaint.shader = RadialGradient(
            cx, cy, radius,
            Color.argb((breathGlowAlpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, glowPaint)
    }

    private fun drawBall(canvas: Canvas, cx: Float, cy: Float) {
        val scale = breathScale * iconScale
        val r = config.ballRadius * scale
        val bitmap = customBitmap
        if (bitmap != null) {
            val clipPath = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            val dst = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawBitmap(
                bitmap, null, dst,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            canvas.restore()
        } else {
            val topColor = lightenColor(getAccentColor(), 1.15f)
            val bottomColor = darkenColor(getAccentColor(), 0.75f)
            ballPaint.shader = RadialGradient(
                cx - r * 0.25f, cy - r * 0.35f, r * 1.8f,
                topColor, bottomColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, ballPaint)
        }

        ringPaint.color = Color.argb(90, 255, 255, 255)
        canvas.drawCircle(cx, cy, r - dp(1.5f), ringPaint)
    }

    private fun drawGestureRing(canvas: Canvas, cx: Float, cy: Float) {
        if (gestureState != GestureState.SWIPING_GESTURE && gestureState != GestureState.LONG_PRESSING) return
        val ringR = config.ballRadius + dp(10f) + gestureProgress * dp(8f)
        ringPaint.color = Color.argb((80 + gestureProgress * 120).toInt(), 255, 255, 255)
        ringPaint.strokeWidth = dp(2f) + gestureProgress * dp(2f)
        canvas.drawCircle(cx, cy, ringR, ringPaint)
    }

    private fun drawSoundWaves(canvas: Canvas, cx: Float, cy: Float) {
        repeat(3) { i ->
            val phase = (wavePhase + i * 0.33f) % 1f
            val radius = config.ballRadius + phase * config.glowRadius * 0.7f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(((1f - phase) * 140).toInt(), 239, 68, 68)
                style = Paint.Style.STROKE
                strokeWidth = dp(2.5f) * (1f - phase * 0.6f)
            }
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float) {
        if (customBitmap != null && !isListening &&
            gestureState == GestureState.IDLE
        ) {
            return
        }
        val (icon, size) = resolveIcon()
        iconPaint.textSize = sp(size)
        canvas.save()
        canvas.scale(iconScale, iconScale, cx, cy)
        val textY = cy - (iconPaint.descent() + iconPaint.ascent()) / 2f
        canvas.drawText(icon, cx, textY, iconPaint)
        canvas.restore()
    }

    private fun resolveIcon(): Pair<String, Float> {
        return when {
            isListening && gestureState == GestureState.SWIPING_GESTURE -> when (currentDirection) {
                Direction.UP -> "🗑" to 17f
                Direction.LEFT -> "🌐" to 17f
                Direction.DOWN -> "🤖" to 17f
                Direction.RIGHT -> "➤" to 20f
                null -> "●" to 14f
            }
            isListening -> "●" to 14f
            gestureState == GestureState.SWIPING_DELETE -> {
                if (swipeDx >= 0) "↩" to 18f else "⌫" to 18f
            }
            gestureState == GestureState.DRAGGING -> "✥" to 17f
            else -> "🎤" to 16f
        }
    }

    private fun drawGestureHints(canvas: Canvas, cx: Float, cy: Float) {
        val label = when {
            isListening && gestureState == GestureState.SWIPING_GESTURE -> when (currentDirection) {
                Direction.UP -> "撤销"
                Direction.LEFT -> "翻译"
                Direction.DOWN -> "问答"
                Direction.RIGHT -> "发送"
                null -> null
            }
            gestureState == GestureState.SWIPING_DELETE -> if (swipeDx >= 0) "撤回" else "删除"
            else -> null
        } ?: return
        val y = cy + config.ballRadius + dp(14f)
        hintPaint.color = Color.argb(200, 255, 255, 255)
        canvas.drawText(label, cx, y, hintPaint)
    }

    private fun drawGestureTrail(canvas: Canvas, cx: Float, cy: Float) {
        val localFingerX = fingerX - (pressStartX - cx)
        val localFingerY = fingerY - (pressStartY - cy)
        trailPath.reset()
        trailPath.moveTo(cx, cy)
        trailPath.lineTo(localFingerX, localFingerY)
        trailPaint.shader = LinearGradient(
            cx, cy, localFingerX, localFingerY,
            getAccentColor(), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(trailPath, trailPaint)
    }

    private fun getAccentColor(): Int = when {
        isListening && gestureState == GestureState.SWIPING_GESTURE -> when (currentDirection) {
            Direction.UP -> config.trashColor
            Direction.LEFT -> config.translateColor
            Direction.DOWN -> config.robotColor
            Direction.RIGHT -> config.sendColor
            else -> config.listeningColor
        }
        isListening -> config.listeningColor
        gestureState == GestureState.SWIPING_DELETE -> if (swipeDx >= 0) config.restoreColor else config.deleteColor
        gestureState == GestureState.DRAGGING -> config.dragColor
        else -> config.idleColor
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            return handleTouchEvent(event)
        } catch (e: Exception) {
            android.util.Log.e("FloatingBall", "onTouchEvent error", e)
            gestureState = GestureState.IDLE
            return true
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressStartX = event.rawX
                pressStartY = event.rawY
                fingerX = event.rawX
                fingerY = event.rawY
                swipeDx = 0f
                gestureState = GestureState.PRESSING
                gestureProgress = 0f
                pressStartTime = android.os.SystemClock.uptimeMillis()

                longPressRunnable = Runnable {
                    if (gestureState == GestureState.PRESSING) {
                        transitionTo(GestureState.LONG_PRESSING)
                        listener?.onGestureAction(GestureAction.LongPressStart)
                    }
                }
                handler.postDelayed(longPressRunnable!!, config.longPressTimeout)
                performHaptic(HAPTIC_TAP)
                invalidate()
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
                            if (!isListening && absDx > absDy && dx < 0) {
                                transitionTo(GestureState.SWIPING_DELETE)
                                swipeDx = dx
                                listener?.onGestureAction(GestureAction.Swipe(Direction.LEFT))
                                performHaptic(HAPTIC_SWIPE)
                            } else {
                                transitionTo(GestureState.DRAGGING)
                                listener?.onGestureAction(GestureAction.DragStart(pressStartX, pressStartY))
                                performHaptic(HAPTIC_DRAG)
                            }
                        }
                    }

                    GestureState.LONG_PRESSING -> {
                        if (distance > config.gestureThreshold * 1.2f) {
                            val dir = determineDirection(dx, dy)
                            if (gestureState != GestureState.SWIPING_GESTURE) {
                                transitionTo(GestureState.SWIPING_GESTURE)
                                listener?.onGestureAction(GestureAction.Swipe(dir))
                                performHaptic(HAPTIC_SWIPE)
                            } else if (dir != currentDirection) {
                                currentDirection = dir
                                performHaptic(HAPTIC_DIRECTION)
                            }
                            currentDirection = dir
                            gestureProgress = calculateGestureProgress(dx, dy, dir)
                        }
                    }

                    GestureState.SWIPING_DELETE -> {
                        val prevDx = swipeDx
                        swipeDx = dx
                        gestureProgress = (abs(dx) / (config.gestureThreshold * 6f)).coerceIn(0f, 1f)
                        if (prevDx < 0 && swipeDx >= 0) performHaptic(HAPTIC_RESTORE)
                        else if (prevDx >= 0 && swipeDx < 0) performHaptic(HAPTIC_SWIPE)
                        listener?.onGestureAction(GestureAction.SwipeProgress(Direction.LEFT, dx))
                    }

                    GestureState.SWIPING_GESTURE -> {
                        val dir = determineDirection(dx, dy)
                        if (dir != currentDirection) {
                            currentDirection = dir
                            performHaptic(HAPTIC_DIRECTION)
                        }
                        gestureProgress = calculateGestureProgress(dx, dy, dir)
                        listener?.onGestureAction(GestureAction.SwipeProgress(dir, gestureProgress))
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
                        performHaptic(HAPTIC_COMPLETE)
                    }
                    GestureState.SWIPING_GESTURE -> {
                        listener?.onGestureAction(GestureAction.SwipeComplete)
                        performHaptic(HAPTIC_COMPLETE)
                    }
                    GestureState.DRAGGING -> {
                        listener?.onGestureAction(GestureAction.DragEnd(0f, 0f))
                        performHaptic(HAPTIC_TAP)
                    }
                    GestureState.PRESSING -> {
                        if (android.os.SystemClock.uptimeMillis() - pressStartTime < 200) {
                            performHaptic(HAPTIC_TAP)
                            listener?.onTap()
                            animateIconBounce()
                        }
                    }
                    else -> {}
                }
                gestureState = GestureState.IDLE
                currentDirection = null
                swipeDx = 0f
                gestureProgress = 0f
                animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.4f)).start()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun transitionTo(newState: GestureState) {
        gestureState = newState
        animate().cancel()
        animate().scaleX(1.08f).scaleY(1.08f).setDuration(120)
            .setInterpolator(OvershootInterpolator(2f))
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            .start()
    }

    private fun animateIconBounce() {
        animate().cancel()
        animate()
            .scaleX(1.1f).scaleY(1.1f)
            .setDuration(90)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(160)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    private fun determineDirection(dx: Float, dy: Float): Direction {
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDx > absDy && dx < 0 -> Direction.LEFT
            absDx > absDy && dx > 0 -> Direction.RIGHT
            absDy >= absDx && dy < 0 -> Direction.UP
            else -> Direction.DOWN
        }
    }

    private fun calculateGestureProgress(dx: Float, dy: Float, direction: Direction): Float {
        val distance = when (direction) {
            Direction.LEFT, Direction.RIGHT -> abs(dx)
            Direction.UP, Direction.DOWN -> abs(dy)
        }
        return (distance / (config.gestureThreshold * 6f)).coerceIn(0f, 1f)
    }

    private fun performHaptic(type: Int) {
        if (!config.hapticEnabled) return
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (type) {
                    HAPTIC_LONG_PRESS -> vibrator.vibrate(
                        VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                    HAPTIC_SWIPE -> vibrator.vibrate(
                        VibrationEffect.createOneShot(18, 120)
                    )
                    HAPTIC_DIRECTION -> vibrator.vibrate(
                        VibrationEffect.createOneShot(12, 80)
                    )
                    HAPTIC_RESTORE -> vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 10, 40, 12), -1)
                    )
                    HAPTIC_DRAG -> vibrator.vibrate(
                        VibrationEffect.createOneShot(8, 60)
                    )
                    HAPTIC_COMPLETE -> vibrator.vibrate(
                        VibrationEffect.createOneShot(25, 160)
                    )
                    else -> vibrator.vibrate(
                        VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12)
            }
        } catch (e: Exception) {
            android.util.Log.w("FloatingBall", "haptic failed", e)
        }
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt().coerceAtMost(255),
            (Color.green(color) * factor).toInt().coerceAtMost(255),
            (Color.blue(color) * factor).toInt().coerceAtMost(255)
        )
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

     private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureCustomBitmapLoaded()
        if (!breathAnimator.isRunning) breathAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnimator.cancel()
        waveAnimator.cancel()
        handler.removeCallbacksAndMessages(null)
        // Keep customBitmap — view may be re-attached when "input only" mode toggles
    }

    companion object {
        private const val HAPTIC_TAP = 0
        private const val HAPTIC_LONG_PRESS = 1
        private const val HAPTIC_SWIPE = 2
        private const val HAPTIC_DIRECTION = 3
        private const val HAPTIC_RESTORE = 4
        private const val HAPTIC_DRAG = 5
        private const val HAPTIC_COMPLETE = 6
    }
}
