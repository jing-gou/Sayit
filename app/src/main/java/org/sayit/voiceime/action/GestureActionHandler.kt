package org.sayit.voiceime.action

import org.sayit.voiceime.VoiceKeyboard
import org.sayit.voiceime.gesture.Direction
import org.sayit.voiceime.gesture.GestureAction

data class DeletedText(val text: String, val count: Int)

enum class VoiceMode {
    INPUT,
    QUESTION,
    TRANSLATE
}

class GestureActionHandler(private val ime: VoiceKeyboard) {

    private var currentVoiceMode = VoiceMode.INPUT
    private var isDragging = false
    private var logCallback: ((String) -> Unit)? = null

    // Stack: each entry is one deleted unit; restore pops from end (LIFO)
    private val deletedStack = ArrayDeque<String>()
    private var deleteCount = 0

    companion object {
        private const val PX_PER_CHAR = 20f
    }

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    fun handle(action: GestureAction) {
        when (action) {
            is GestureAction.LongPressStart -> {
                currentVoiceMode = VoiceMode.INPUT
                ime.startVoiceInput()
            }

            is GestureAction.LongPressEnd -> {
                if (isDragging) return
                ime.stopVoiceInput()
            }

            is GestureAction.Swipe -> {
                if (!ime.isCurrentlyListening() && action.direction == Direction.LEFT) {
                    resetDeleteState()
                }
                if (ime.isCurrentlyListening()) {
                    when (action.direction) {
                        Direction.LEFT -> currentVoiceMode = VoiceMode.TRANSLATE
                        Direction.RIGHT -> ime.commitEnterKey()
                        Direction.UP -> {
                            ime.cancelVoiceInput()
                            currentVoiceMode = VoiceMode.INPUT
                        }
                        Direction.DOWN -> currentVoiceMode = VoiceMode.QUESTION
                    }
                }
            }

            is GestureAction.SwipeProgress -> {
                if (!ime.isCurrentlyListening() && action.direction == Direction.LEFT) {
                    handleDeleteProgress(action.progress)
                }
            }

            is GestureAction.SwipeComplete -> {
                if (ime.isCurrentlyListening()) {
                    when (currentVoiceMode) {
                        VoiceMode.QUESTION -> ime.stopVoiceInputWithMode(VoiceMode.QUESTION)
                        VoiceMode.TRANSLATE -> ime.stopVoiceInputWithMode(VoiceMode.TRANSLATE)
                        else -> ime.stopVoiceInput()
                    }
                }
                resetDeleteState()
            }

            is GestureAction.SwipeCancel -> resetDeleteState()

            is GestureAction.DragStart -> isDragging = true

            is GestureAction.DragMove -> ime.updateFloatingBallPosition(action.x, action.y)

            is GestureAction.DragEnd -> {
                isDragging = false
                ime.resetDragOffset()
            }

            is GestureAction.None -> {}
        }
    }

    private fun handleDeleteProgress(rawDx: Float) {
        val ic = ime.getInputConnection() ?: return

        // Left of start (negative dx) deletes; right of start (non-negative) restores
        val targetCount = if (rawDx < 0) {
            (-rawDx / PX_PER_CHAR).toInt().coerceAtLeast(0)
        } else {
            0
        }

        // Delete one unit at a time — never batch-append, which breaks restore order
        while (deleteCount < targetCount) {
            val unit = ic.getTextBeforeCursor(1, 0)?.toString() ?: break
            if (unit.isEmpty()) break
            deletedStack.addLast(unit)
            ic.deleteSurroundingText(unit.length, 0)
            deleteCount++
        }

        // Restore one unit at a time from stack end (most recently deleted first)
        while (deleteCount > targetCount && deletedStack.isNotEmpty()) {
            val unit = deletedStack.removeLast()
            ic.commitText(unit, 1)
            deleteCount--
        }
    }

    private fun resetDeleteState() {
        deletedStack.clear()
        deleteCount = 0
    }

    fun undoDelete(): Boolean {
        if (deletedStack.isEmpty()) return false
        val ic = ime.getInputConnection() ?: return false
        while (deletedStack.isNotEmpty()) {
            ic.commitText(deletedStack.removeLast(), 1)
        }
        deleteCount = 0
        return true
    }

    fun clearDeleteHistory() = resetDeleteState()
}
