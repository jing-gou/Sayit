package org.sayit.voiceime.action

import android.view.inputmethod.InputConnection
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

    // Incremental delete state
    private val deletedChars = StringBuilder()
    private var deleteCount = 0
    private var isDeleting = false

    companion object {
        private const val PX_PER_CHAR = 10f // pixels of horizontal drag per character
    }

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    private fun debugLog(msg: String) {
        android.util.Log.d("GestureHandler", msg)
        logCallback?.invoke("[Gesture] $msg")
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
                if (ime.isCurrentlyListening()) {
                    when (action.direction) {
                        Direction.LEFT -> currentVoiceMode = VoiceMode.TRANSLATE
                        Direction.RIGHT -> ime.commitEnterKey()
                        Direction.UP -> ime.undoLastInput()
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

            is GestureAction.SwipeCancel -> {
                resetDeleteState()
            }

            is GestureAction.DragStart -> {
                isDragging = true
            }

            is GestureAction.DragMove -> {
                ime.updateFloatingBallPosition(action.x, action.y)
            }

            is GestureAction.DragEnd -> {
                isDragging = false
                ime.resetDragOffset()
            }

            is GestureAction.None -> {}
        }
    }

    private fun handleDeleteProgress(rawDx: Float) {
        val ic = ime.getInputConnection() ?: return

        // rawDx is the signed horizontal displacement from press start
        // Negative = finger is left of start (delete), positive = right (restore)
        // Calculate target character count: only delete when dx is negative
        val targetCount = if (rawDx < 0) {
            (-rawDx / PX_PER_CHAR).toInt().coerceAtLeast(0)
        } else {
            0 // finger is to the right of start = restore all
        }

        if (targetCount > deleteCount) {
            // Delete more characters
            val toDelete = targetCount - deleteCount
            val before = ic.getTextBeforeCursor(toDelete, 0)?.toString()
            if (before != null && before.isNotEmpty()) {
                deletedChars.append(before)
                ic.deleteSurroundingText(before.length, 0)
                deleteCount += before.length
            }
        } else if (targetCount < deleteCount && deletedChars.isNotEmpty()) {
            // Restore characters (finger moved back right)
            val toRestore = deleteCount - targetCount
            val actualRestore = toRestore.coerceAtMost(deletedChars.length)
            if (actualRestore > 0) {
                val start = deletedChars.length - actualRestore
                val text = deletedChars.substring(start)
                deletedChars.delete(start, deletedChars.length)
                ic.commitText(text, 1)
                deleteCount -= actualRestore
            }
        }
    }

    private fun resetDeleteState() {
        deletedChars.clear()
        deleteCount = 0
        isDeleting = false
    }

    fun undoDelete(): Boolean {
        if (deletedChars.isEmpty()) return false
        val ic = ime.getInputConnection() ?: return false
        ic.commitText(deletedChars.toString(), 1)
        deletedChars.clear()
        deleteCount = 0
        return true
    }

    fun clearDeleteHistory() {
        deletedChars.clear()
        deleteCount = 0
    }
}
