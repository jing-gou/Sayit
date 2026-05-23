package org.sayit.voiceime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button

class VoiceKeyboard : InputMethodService() {

    private var micButton: Button? = null
    private var isListening = false

    override fun onCreateInputView(): View {
        micButton = Button(this).apply {
            text = if (isListening) "停止" else "🎤 点击开始语音输入"
            setOnClickListener { toggleListening() }
        }
        return micButton!!
    }

    private fun toggleListening() {
        isListening = !isListening
        micButton?.text = if (isListening) "停止" else "🎤 点击开始语音输入"

        if (isListening) {
            startSpeechRecognition()
        } else {
            stopSpeechRecognition()
        }
    }

    private fun startSpeechRecognition() {
        // TODO: 实现语音识别
        // 识别完成后调用 commitText(text)
    }

    private fun stopSpeechRecognition() {
        // TODO: 停止语音识别
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        micButton?.text = if (isListening) "停止" else "🎤 点击开始语音输入"
    }
}