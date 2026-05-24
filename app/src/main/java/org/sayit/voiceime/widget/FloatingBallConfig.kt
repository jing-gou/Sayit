package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import org.sayit.voiceime.AppSettings

data class FloatingBallConfig(
    val ballRadius: Float,
    val glowRadius: Float,
    val customBallImagePath: String? = null,
    val idleColor: Int,
    val listeningColor: Int,
    val gestureColor: Int,
    val deleteColor: Int,
    val restoreColor: Int,
    val translateColor: Int,
    val sendColor: Int,
    val robotColor: Int,
    val trashColor: Int,
    val dragColor: Int,
    val gestureThreshold: Float,
    val longPressTimeout: Long,
    val trailEnabled: Boolean,
    val hapticEnabled: Boolean
) {
    val overlayWindowSize: Float get() = ballRadius * 2.8f

    companion object {
        fun fromSettings(context: Context): FloatingBallConfig {
            AppSettings.setup(context)
            val dm = context.resources.displayMetrics
            val dp = { value: Float ->
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, dm)
            }
            val progress = AppSettings.readBallSize(context) / 100f
            val minRadius = dp(12f)
            val maxRadius = dp(56f)
            val ballRadius = minRadius + (maxRadius - minRadius) * progress
            return baseConfig(dp, ballRadius, AppSettings.customBallImagePath)
        }

        fun radiusDpForProgress(progress: Int): Int {
            val p = progress.coerceIn(0, 100) / 100f
            return (12f + (56f - 12f) * p).toInt()
        }

        fun default(context: Context): FloatingBallConfig = fromSettings(context)

        private fun baseConfig(
            dp: (Float) -> Float,
            ballRadius: Float,
            customBallImagePath: String?
        ): FloatingBallConfig {
            return FloatingBallConfig(
                ballRadius = ballRadius,
                glowRadius = ballRadius * 1.38f,
                customBallImagePath = customBallImagePath,
                idleColor = Color.parseColor("#6366F1"),
                listeningColor = Color.parseColor("#EF4444"),
                gestureColor = Color.parseColor("#818CF8"),
                deleteColor = Color.parseColor("#F97316"),
                restoreColor = Color.parseColor("#22C55E"),
                translateColor = Color.parseColor("#06B6D4"),
                sendColor = Color.parseColor("#10B981"),
                robotColor = Color.parseColor("#A855F7"),
                trashColor = Color.parseColor("#F43F5E"),
                dragColor = Color.parseColor("#64748B"),
                gestureThreshold = dp(24f),
                longPressTimeout = 300L,
                trailEnabled = true,
                hapticEnabled = true
            )
        }
    }
}
