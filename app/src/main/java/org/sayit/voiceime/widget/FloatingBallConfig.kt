package org.sayit.voiceime.widget

import android.content.Context
import android.graphics.Color
import android.util.TypedValue

data class FloatingBallConfig(
    val ballRadius: Float,
    val glowRadius: Float,
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
    companion object {
        fun default(context: Context): FloatingBallConfig {
            val dp = { value: Float ->
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, value,
                    context.resources.displayMetrics
                )
            }
            return FloatingBallConfig(
                ballRadius = dp(26f),
                glowRadius = dp(36f),
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
