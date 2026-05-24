package org.sayit.voiceime.action

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import org.sayit.voiceime.VoiceKeyboard
import org.sayit.voiceime.gesture.Direction
import org.sayit.voiceime.gesture.GestureAction
import kotlin.math.max
import kotlin.math.min

data class DeletedText(val text: String, val count: Int)

enum class VoiceMode {
    INPUT,
    QUESTION,
    TRANSLATE
}

class GestureActionHandler(private val ime: VoiceKeyboard) {

    private var currentVoiceMode = VoiceMode.INPUT
    private var isDragging = false
    private val deletedStack = ArrayDeque<String>()
    private var deleteCount = 0
    private var selectionHandledThisGesture = false
    private var recordingSwipeDirection: Direction? = null

    companion object {
        private const val PX_PER_CHAR = 20f
    }

    fun handle(action: GestureAction) {
        when (action) {
            is GestureAction.LongPressStart -> {
                currentVoiceMode = VoiceMode.INPUT
                recordingSwipeDirection = null
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
                    recordingSwipeDirection = action.direction
                    when (action.direction) {
                        Direction.LEFT -> currentVoiceMode = VoiceMode.TRANSLATE
                        Direction.RIGHT -> Unit
                        Direction.UP -> {
                            ime.cancelVoiceInput()
                            currentVoiceMode = VoiceMode.INPUT
                            recordingSwipeDirection = null
                        }
                        Direction.DOWN -> currentVoiceMode = VoiceMode.QUESTION
                    }
                }
            }

            is GestureAction.SwipeProgress -> {
                if (ime.isCurrentlyListening()) {
                    recordingSwipeDirection = action.direction
                } else if (action.direction == Direction.LEFT) {
                    handleDeleteProgress(action.progress)
                }
            }

            is GestureAction.SwipeComplete -> {
                if (ime.isCurrentlyListening()) {
                    val sendAfter = recordingSwipeDirection == Direction.RIGHT &&
                        currentVoiceMode == VoiceMode.INPUT
                    when (currentVoiceMode) {
                        VoiceMode.QUESTION -> ime.stopVoiceInputWithMode(VoiceMode.QUESTION)
                        VoiceMode.TRANSLATE -> ime.stopVoiceInputWithMode(VoiceMode.TRANSLATE)
                        else -> ime.stopVoiceInput(sendAfter = sendAfter)
                    }
                    recordingSwipeDirection = null
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

        val targetCount = if (rawDx < 0) {
            (-rawDx / PX_PER_CHAR).toInt().coerceAtLeast(0)
        } else {
            0
        }

        if (!selectionHandledThisGesture && rawDx < 0) {
            val selected = readSelectedText(ic)
            if (!selected.isNullOrEmpty()) {
                deletedStack.addLast(selected)
                ic.commitText("", 1)
                deleteCount = max(deleteCount, 1)
                selectionHandledThisGesture = true
            }
        }

        while (deleteCount < targetCount) {
            val unit = ic.getTextBeforeCursor(1, 0)?.toString() ?: break
            if (unit.isEmpty()) break
            deletedStack.addLast(unit)
            ic.deleteSurroundingText(unit.length, 0)
            deleteCount++
        }

        while (deleteCount > targetCount && deletedStack.isNotEmpty()) {
            val unit = deletedStack.removeLast()
            ic.commitText(unit, 1)
            deleteCount--
        }
    }

    private fun resetDeleteState() {
        deletedStack.clear()
        deleteCount = 0
        selectionHandledThisGesture = false
    }

    private fun readSelectedText(ic: InputConnection): String? {
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) return selected.toString()

        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val start = min(extracted.selectionStart, extracted.selectionEnd)
        val end = max(extracted.selectionStart, extracted.selectionEnd)
        if (start == end) return null
        return extracted.text?.subSequence(start, end)?.toString()
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
