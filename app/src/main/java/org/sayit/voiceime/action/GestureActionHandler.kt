package org.sayit.voiceime.action

import android.view.KeyEvent
import org.sayit.voiceime.VoiceKeyboard
import org.sayit.voiceime.gesture.Direction
import org.sayit.voiceime.gesture.GestureAction
import java.util.ArrayDeque

data class DeletedText(val text: String, val count: Int)

enum class VoiceMode {
    INPUT,
    QUESTION,
    TRANSLATE
}

class GestureActionHandler(private val ime: VoiceKeyboard) {

    private val deleteHistory = ArrayDeque<DeletedText>()
    private var currentVoiceMode = VoiceMode.INPUT
    private var isDragging = false
    private var logCallback: ((String) -> Unit)? = null

    fun setLogCallback(callback: (String) -> Unit) {
        logCallback = callback
    }

    private fun debugLog(msg: String) {
        android.util.Log.d("GestureHandler", msg)
        logCallback?.invoke("[Gesture] $msg")
    }

    fun handle(action: GestureAction) {
        val actionName = action::class.simpleName ?: "Unknown"
        debugLog("handle: $actionName")
        when (action) {
            is GestureAction.LongPressStart -> {
                currentVoiceMode = VoiceMode.INPUT
                debugLog("LongPressStart -> startVoiceInput()")
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
                    val count = calculateDeleteCount(action.progress)
                    if (count > 0) {
                        val deleted = ime.deleteText(count)
                        if (deleted.count > 0) {
                            deleteHistory.push(deleted)
                        }
                    }
                }
            }

            is GestureAction.SwipeComplete -> {
                if (ime.isCurrentlyListening()) {
                    when (currentVoiceMode) {
                        VoiceMode.QUESTION -> {
                            ime.stopVoiceInputWithMode(VoiceMode.QUESTION)
                        }
                        VoiceMode.TRANSLATE -> {
                            ime.stopVoiceInputWithMode(VoiceMode.TRANSLATE)
                        }
                        else -> {
                            ime.stopVoiceInput()
                        }
                    }
                }
            }

            is GestureAction.SwipeCancel -> {
                if (deleteHistory.isNotEmpty()) {
                    val last = deleteHistory.pop()
                    ime.restoreText(last)
                }
            }

            is GestureAction.DragStart -> {
                isDragging = true
            }

            is GestureAction.DragMove -> {
                ime.updateFloatingBallPosition(action.x, action.y)
            }

            is GestureAction.DragEnd -> {
                isDragging = false
            }

            is GestureAction.None -> {}
        }
    }

    fun undoDelete(): Boolean {
        if (deleteHistory.isEmpty()) return false
        val last = deleteHistory.pop()
        ime.restoreText(last)
        return true
    }

    fun clearDeleteHistory() {
        deleteHistory.clear()
    }

    private fun calculateDeleteCount(progress: Float): Int {
        val normalized = progress.coerceIn(0f, 1f)
        return (1 + normalized * normalized * 19).toInt()
    }
}