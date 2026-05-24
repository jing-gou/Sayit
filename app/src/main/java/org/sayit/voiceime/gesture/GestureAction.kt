package org.sayit.voiceime.gesture

enum class Direction {
    LEFT, RIGHT, UP, DOWN
}

enum class GestureState {
    IDLE,
    PRESSING,
    LONG_PRESSING,
    SWIPING_DELETE,
    SWIPING_GESTURE,
    DRAGGING
}

sealed class GestureAction {
    object None : GestureAction()
    object LongPressStart : GestureAction()
    object LongPressEnd : GestureAction()
    data class Swipe(val direction: Direction) : GestureAction()
    data class SwipeProgress(val direction: Direction, val progress: Float) : GestureAction()
    object SwipeComplete : GestureAction()
    object SwipeCancel : GestureAction()
    data class DragStart(val startX: Float, val startY: Float) : GestureAction()
    data class DragMove(val x: Float, val y: Float) : GestureAction()
    data class DragEnd(val velocityX: Float, val velocityY: Float) : GestureAction()
}