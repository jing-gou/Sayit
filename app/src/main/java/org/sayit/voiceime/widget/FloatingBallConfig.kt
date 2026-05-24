package org.sayit.voiceime.widget

import android.content.Context
import android.util.TypedValue

data class FloatingBallConfig(
    val ballRadius: Float,
    val glowRadius: Float,
    val idleColor: Int,
    val listeningColor: Int,
    val gestureColor: Int,
    val deleteColor: Int,
    val loadingColor: Int,
    val gestureThreshold: Float,
    val longPressTimeout: Long,
    val trailEnabled: Boolean,
    val hapticEnabled: Boolean,
    val edgeSnapEnabled: Boolean
) {
    companion object {
        fun default(context: Context): FloatingBallConfig {
            val dp = { value: Float ->
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, value,
                    context.resources.displayMetrics
                )
            }
            return FloatingBallConfig(
                ballRadius = dp(28f),
                glowRadius = dp(40f),
                idleColor = 0xFF78909C.toInt(),
                listeningColor = 0xFFFF5252.toInt(),
                gestureColor = 0xFF2196F3.toInt(),
                deleteColor = 0xFFFF9800.toInt(),
                loadingColor = 0xFF9C27B0.toInt(),
                gestureThreshold = dp(30f),
                longPressTimeout = 300L,
                trailEnabled = true,
                hapticEnabled = true,
                edgeSnapEnabled = true
            )
        }
    }
}