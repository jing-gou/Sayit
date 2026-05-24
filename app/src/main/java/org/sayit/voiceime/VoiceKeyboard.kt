package org.sayit.voiceime

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.zip.GZIPInputStream
import java.io.ByteArrayInputStream
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.sayit.voiceime.action.DeletedText
import org.sayit.voiceime.action.GestureActionHandler
import org.sayit.voiceime.action.VoiceMode
import org.sayit.voiceime.api.LLMService
import org.sayit.voiceime.api.StreamCallback
import org.sayit.voiceime.gesture.GestureAction
import org.sayit.voiceime.overlay.OverlayHelper
import org.sayit.voiceime.overlay.ResultBubbleView
import org.sayit.voiceime.widget.FloatingBallConfig
import org.sayit.voiceime.widget.FloatingBallListener
import org.sayit.voiceime.widget.FloatingBallView
import org.sayit.voiceime.widget.RadialMenuAction
import org.sayit.voiceime.widget.RadialMenuView
import org.sayit.voiceime.clipboard.ClipboardHistoryManager
import org.sayit.voiceime.widget.ClipboardHistoryView
import org.sayit.voiceime.widget.SymbolPanelView

object ASRConfig {
    val WS_URL get() = AppSettings.asrWsUrl
    val API_KEY get() = AppSettings.asrApiKey
    val RESOURCE_ID get() = AppSettings.asrResourceId
    const val SAMPLE_RATE = 16000
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
}

class VoiceKeyboard : InputMethodService() {

    private val TAG = "VoiceKeyboard"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var isListening = false
    @Volatile private var taskFinished = false
    @Volatile private var resultsDelivered = false
    @Volatile private var recordingActive = false
    @Volatile private var speechCancelled = false

    private var symbolPanelInsetHeight = 0
    private var inputPlaceholder: View? = null

    private var audioRecord: AudioRecord? = null
    private var webSocket: WebSocket? = null
    private lateinit var okHttpClient: OkHttpClient

    @Volatile private var resultBuffer = ""

    // Overlay — ball uses a small window; other UI added on demand
    private var overlayHelper: OverlayHelper? = null
    private var ballLayoutParams: WindowManager.LayoutParams? = null
    private var screenW = 0
    private var screenH = 0

    // Drag offset (difference between finger and ball center at drag start)
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    // Views
    private var floatingBall: FloatingBallView? = null
    private var resultBubble: ResultBubbleView? = null
    private var radialMenu: RadialMenuView? = null
    private var symbolPanel: SymbolPanelView? = null
    private var clipboardPanel: ClipboardHistoryView? = null
    private var clipboardHistory: ClipboardHistoryManager? = null
    private var gestureHandler: GestureActionHandler? = null
    private var llmService: LLMService? = null
    private var currentVoiceMode = VoiceMode.INPUT

    companion object {
        private val gson = Gson()
        private const val CHUNK_SIZE = 6400
        private const val RECORDING_GRACE_MS = 300L

        private const val PROTOCOL_VERSION = 0x01
        private const val MSG_FULL_CLIENT_REQUEST  = 0x01
        private const val MSG_AUDIO_ONLY_REQUEST   = 0x02
        private const val MSG_FULL_SERVER_RESPONSE = 0x09
        private const val MSG_ERROR_RESPONSE       = 0x0F
        private const val FLAG_NONE                = 0x00
        private const val FLAG_HAS_SEQUENCE        = 0x01
        private const val FLAG_LAST_PACKET_NO_SEQ  = 0x02
        private const val FLAG_LAST_PACKET_WITH_SEQ= 0x03
        private const val SER_NONE  = 0x00
        private const val SER_JSON  = 0x01
        private const val COMP_NONE = 0x00
        private const val COMP_GZIP = 0x01
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        try {
            AppSettings.setup(this)
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            overlayHelper = OverlayHelper(applicationContext)
            val dm = resources.displayMetrics
            screenW = dm.widthPixels
            screenH = dm.heightPixels

            gestureHandler = GestureActionHandler(this)
            llmService = LLMService(okHttpClient)
            clipboardHistory = ClipboardHistoryManager(this).also { it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error", e)
        }
    }

    private fun ensureOverlay() {
        if (floatingBall != null) return
        if (Settings.canDrawOverlays(this)) {
            createOverlayWindow()
        } else {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted, launching settings")
            try {
                val intent = android.content.Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch overlay settings", e)
            }
        }
    }

    private fun createOverlayWindow() {
        Log.d(TAG, "createOverlayWindow")
        val helper = overlayHelper ?: return

        val config = FloatingBallConfig.default(applicationContext)
        val ballSize = (config.ballRadius * 2.5f).toInt()
        val initX = screenW - ballSize - dp(16)
        val initY = screenH / 3

        floatingBall = FloatingBallView(applicationContext, config).apply {
            listener = object : FloatingBallListener {
                override fun onGestureAction(action: GestureAction) {
                    gestureHandler?.handle(action)
                }
                override fun onVoiceStateChanged(listening: Boolean) {}
                override fun onTap() {
                    showRadialMenu()
                }
            }
        }

        ballLayoutParams = OverlayHelper.ballParams(ballSize, ballSize, initX, initY)
        helper.addView(floatingBall!!, ballLayoutParams!!)
        Log.d(TAG, "Ball overlay window added (${ballSize}x${ballSize} at $initX,$initY)")
    }

    private fun ballPosition(): Pair<Int, Int> {
        val params = ballLayoutParams
        return (params?.x ?: 0) to (params?.y ?: 0)
    }

    private fun ballSize(): Int = floatingBall?.width?.coerceAtLeast(1) ?: 1

    override fun onCreateInputView(): View {
        val placeholder = object : View(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val w = MeasureSpec.getSize(widthMeasureSpec)
                setMeasuredDimension(w, symbolPanelInsetHeight)
            }
        }
        inputPlaceholder = placeholder
        return placeholder
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // Placeholder height already drives visibleTopInsets; don't subtract again
        outInsets.contentTopInsets = outInsets.visibleTopInsets
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        outInsets.touchableRegion.setEmpty()
    }

    private fun updateSymbolPanelInset(heightPx: Int) {
        symbolPanelInsetHeight = heightPx
        inputPlaceholder?.requestLayout()
        updateInputViewShown()
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun startVoiceInput() {
        if (!isListening) startSpeechRecognition()
    }

    fun stopVoiceInput() {
        if (isListening) stopSpeechRecognition()
    }

    fun stopVoiceInputWithMode(mode: VoiceMode) {
        currentVoiceMode = mode
        if (isListening) stopSpeechRecognition()
    }

    fun getInputConnection(): android.view.inputmethod.InputConnection? {
        return currentInputConnection
    }

    fun deleteText(count: Int): DeletedText {
        val ic = currentInputConnection ?: return DeletedText("", 0)
        val deleted = ic.getTextBeforeCursor(count, 0)?.toString() ?: ""
        ic.deleteSurroundingText(count, 0)
        return DeletedText(deleted, count)
    }

    fun restoreText(deleted: DeletedText) {
        currentInputConnection?.commitText(deleted.text, 1)
    }

    fun commitEnterKey() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    /** Cancel in-progress voice input and clear ASR composing preview. */
    fun cancelVoiceInput() {
        if (!isListening && !recordingActive) return
        abortSpeechRecognition()
    }

    private fun clearVoiceComposingText() {
        scope.launch(Dispatchers.Main) {
            currentInputConnection?.setComposingText("", 0)
        }
    }

    fun updateFloatingBallPosition(rawX: Float, rawY: Float) {
        val ball = floatingBall ?: return
        val params = ballLayoutParams ?: return
        val helper = overlayHelper ?: return
        val size = ball.width.coerceAtLeast(1)
        if (dragOffsetX == 0f && dragOffsetY == 0f) {
            val ballCenterX = params.x + size / 2f
            val ballCenterY = params.y + size / 2f
            dragOffsetX = rawX - ballCenterX
            dragOffsetY = rawY - ballCenterY
        }
        params.x = (rawX - dragOffsetX - size / 2f).toInt().coerceIn(0, screenW - size)
        params.y = (rawY - dragOffsetY - size / 2f).toInt().coerceIn(0, screenH - size)
        helper.updateViewLayout(ball, params)
    }

    fun resetDragOffset() {
        dragOffsetX = 0f
        dragOffsetY = 0f
    }

    private fun startSpeechRecognition() {
        if (isListening) return
        if (!checkRecordPermission()) {
            requestRecordPermission()
            return
        }
        isListening = true
        taskFinished = false
        resultsDelivered = false
        speechCancelled = false
        currentVoiceMode = VoiceMode.INPUT
        floatingBall?.setListeningState(true)
        try {
            connectToASR()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ASR", e)
            stopSpeechRecognition()
        }
    }

    private fun stopSpeechRecognition() {
        if (!isListening && !recordingActive) return
        isListening = false
        floatingBall?.setListeningState(false)
        // Recording coroutine sends grace-period audio + end packet, then deliverSpeechResults()
    }

    private fun deliverSpeechResults() {
        if (resultsDelivered) return
        resultsDelivered = true
        recordingActive = false

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val ws = webSocket
        webSocket = null
        ws?.close(1000, "正常关闭")
        scope.launch { delay(3000); ws?.cancel() }

        when (currentVoiceMode) {
            VoiceMode.INPUT -> {
                if (resultBuffer.isNotEmpty()) {
                    commitText(resultBuffer)
                    resultBuffer = ""
                }
            }
            VoiceMode.QUESTION -> {
                val question = resultBuffer
                resultBuffer = ""
                if (question.isNotEmpty()) {
                    showLoadingBubble()
                    llmService?.askStreaming(question, object : StreamCallback {
                        override fun onStart() {
                            scope.launch(Dispatchers.Main) { showLoadingBubble() }
                        }
                        override fun onDelta(delta: String) {
                            scope.launch(Dispatchers.Main) { resultBubble?.appendText(delta) }
                        }
                        override fun onComplete(fullText: String) {
                            scope.launch(Dispatchers.Main) { resultBubble?.finalizeResult() }
                        }
                        override fun onError(e: Exception) {
                            scope.launch(Dispatchers.Main) {
                                resultBubble?.appendText("\n[错误: ${e.message}]")
                                resultBubble?.finalizeResult()
                            }
                        }
                    })
                }
            }
            VoiceMode.TRANSLATE -> {
                val text = resultBuffer
                resultBuffer = ""
                if (text.isNotEmpty()) {
                    showLoadingBubble()
                    llmService?.translateStreaming(text, callback = object : StreamCallback {
                        override fun onStart() {
                            scope.launch(Dispatchers.Main) { showLoadingBubble() }
                        }
                        override fun onDelta(delta: String) {
                            scope.launch(Dispatchers.Main) { resultBubble?.appendText(delta) }
                        }
                        override fun onComplete(fullText: String) {
                            scope.launch(Dispatchers.Main) { resultBubble?.finalizeResult() }
                        }
                        override fun onError(e: Exception) {
                            scope.launch(Dispatchers.Main) {
                                resultBubble?.appendText("\n[错误: ${e.message}]")
                                resultBubble?.finalizeResult()
                            }
                        }
                    })
                }
            }
        }
        currentVoiceMode = VoiceMode.INPUT
    }

    private fun abortSpeechRecognition() {
        speechCancelled = true
        isListening = false
        recordingActive = false
        floatingBall?.setListeningState(false)
        resultBuffer = ""
        resultsDelivered = true
        clearVoiceComposingText()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        val ws = webSocket
        webSocket = null
        ws?.cancel()
    }

    private fun showLoadingBubble() {
        val ball = floatingBall ?: return
        showResultBubble()
        val (bx, by) = ballPosition()
        val size = ballSize()
        resultBubble?.showLoading(bx, by, size, size, screenW)
        resultBubble?.onInsert = { result ->
            currentInputConnection?.commitText(result, 1)
        }
        resultBubble?.let { bubble ->
            (bubble.layoutParams as? WindowManager.LayoutParams)?.let { params ->
                overlayHelper?.updateViewLayout(bubble, params)
            }
        }
    }

    private fun showResultBubble() {
        if (resultBubble != null) return
        val helper = overlayHelper ?: return
        val bubble = ResultBubbleView(applicationContext).apply {
            onDismiss = { dismissResultBubble() }
        }
        val (bx, by) = ballPosition()
        val params = OverlayHelper.wrapContentParams(bx, by + ballSize())
        helper.addView(bubble, params)
        resultBubble = bubble
    }

    private fun dismissResultBubble() {
        resultBubble?.let { overlayHelper?.removeView(it) }
        resultBubble = null
    }

    private fun showRadialMenu() {
        try {
            val ball = floatingBall ?: return
            val helper = overlayHelper ?: return

            val existing = radialMenu
            if (existing != null && existing.visibility == View.VISIBLE) {
                dismissRadialMenu()
                return
            }

            val size = ballSize().coerceAtLeast(dp(40))
            val (bx, by) = ballPosition()
            val centerX = bx + size / 2f
            val centerY = by + size / 2f

            val menu = RadialMenuView(applicationContext, centerX, centerY, size / 2f).apply {
                onAction = { action -> handleRadialAction(action) }
                onDismissRequest = {
                    val m = radialMenu
                    radialMenu = null
                    m?.let { overlayHelper?.removeView(it) }
                }
            }

            helper.addView(menu, OverlayHelper.fullScreenParams())
            radialMenu = menu
            menu.show()
        } catch (e: Exception) {
            Log.e(TAG, "showRadialMenu failed", e)
        }
    }

    private fun dismissRadialMenu() {
        val menu = radialMenu ?: return
        radialMenu = null
        overlayHelper?.removeView(menu)
    }

    private fun handleRadialAction(action: RadialMenuAction) {
        dismissRadialMenu()

        when (action) {
            RadialMenuAction.SETTINGS -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            RadialMenuAction.CLIPBOARD -> showClipboardPanel()
            RadialMenuAction.INPUT_MODE -> {
                showSymbolPanel()
            }
            RadialMenuAction.SWITCH_IME -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showInputMethodPicker()
            }
        }
    }

    private fun showClipboardPanel() {
        val helper = overlayHelper ?: return
        val history = clipboardHistory ?: return
        dismissSymbolPanel()
        dismissClipboardPanel()

        val panel = ClipboardHistoryView(applicationContext).apply {
            onPaste = { text ->
                currentInputConnection?.commitText(text, 1)
                dismissClipboardPanel()
            }
            onDismiss = { dismissClipboardPanel() }
            onPanelHeightChanged = { height -> updateSymbolPanelInset(height) }
        }
        history.captureNow()
        panel.setEntries(history.getEntries())
        helper.addView(panel, OverlayHelper.bottomPanelParams())
        clipboardPanel = panel
        panel.show()
    }

    private fun dismissClipboardPanel() {
        updateSymbolPanelInset(0)
        clipboardPanel?.let { overlayHelper?.removeView(it) }
        clipboardPanel = null
    }

    private fun showSymbolPanel() {
        val helper = overlayHelper ?: return
        dismissClipboardPanel()
        dismissSymbolPanel()

        val panel = SymbolPanelView(applicationContext).apply {
            onSymbolSelected = { sym ->
                currentInputConnection?.commitText(sym, 1)
            }
            onBackspace = {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
            onDismiss = {
                dismissSymbolPanel()
            }
            onPanelHeightChanged = { height ->
                updateSymbolPanelInset(height)
            }
        }
        helper.addView(panel, OverlayHelper.bottomPanelParams())
        symbolPanel = panel
        panel.show()
    }

    private fun dismissSymbolPanel() {
        updateSymbolPanelInset(0)
        symbolPanel?.let {
            overlayHelper?.removeView(it)
        }
        symbolPanel = null
    }

    private fun connectToASR() {
        val requestId  = UUID.randomUUID().toString()
        val connectId  = UUID.randomUUID().toString()

        val request = Request.Builder()
            .url(ASRConfig.WS_URL)
            .header("X-Api-Key",        ASRConfig.API_KEY)
            .header("X-Api-Resource-Id", ASRConfig.RESOURCE_ID)
            .header("X-Api-Request-Id",  requestId)
            .header("X-Api-Connect-Id",  connectId)
            .header("X-Api-Sequence",    "-1")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = buildFullClientRequestPayload()
                val frame   = buildBinaryFrame(
                    msgType  = MSG_FULL_CLIENT_REQUEST,
                    flags    = FLAG_NONE,
                    ser      = SER_JSON,
                    comp     = COMP_NONE,
                    payload  = payload
                )
                webSocket.send(okio.ByteString.of(*frame))

                scope.launch {
                    try { startRecording(webSocket) }
                    catch (e: Exception) {
                        Log.e(TAG, "Recording failed", e)
                        scope.launch(Dispatchers.Main) { abortSpeechRecognition() }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryResponse(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {}

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS failed: ${response?.code} ${t.message}")
                scope.launch(Dispatchers.Main) { abortSpeechRecognition() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isListening || recordingActive) {
                    scope.launch(Dispatchers.Main) { deliverSpeechResults() }
                }
            }
        })
    }

    private suspend fun startRecording(ws: WebSocket) = withContext(Dispatchers.IO) {
        recordingActive = true
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                ASRConfig.SAMPLE_RATE, ASRConfig.CHANNEL_CONFIG, ASRConfig.AUDIO_FORMAT)
            check(bufferSize > 0) { "Invalid bufferSize: $bufferSize" }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                ASRConfig.SAMPLE_RATE,
                ASRConfig.CHANNEL_CONFIG,
                ASRConfig.AUDIO_FORMAT,
                bufferSize
            )
            check(audioRecord?.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }

            audioRecord?.startRecording()

            val buffer = ByteArray(CHUNK_SIZE)

            while (isActive && isListening && !taskFinished) {
                val read = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: 0
                if (read > 0) {
                    val frame = buildBinaryFrame(
                        msgType = MSG_AUDIO_ONLY_REQUEST,
                        flags   = FLAG_NONE,
                        ser     = SER_NONE,
                        comp    = COMP_NONE,
                        payload = buffer.copyOf(read)
                    )
                    ws.send(okio.ByteString.of(*frame))
                }
            }

            // Grace period: keep sending audio before end packet
            val graceEnd = System.currentTimeMillis() + RECORDING_GRACE_MS
            while (isActive && System.currentTimeMillis() < graceEnd) {
                val read = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: 0
                if (read > 0) {
                    val frame = buildBinaryFrame(
                        msgType = MSG_AUDIO_ONLY_REQUEST,
                        flags   = FLAG_NONE,
                        ser     = SER_NONE,
                        comp    = COMP_NONE,
                        payload = buffer.copyOf(read)
                    )
                    ws.send(okio.ByteString.of(*frame))
                } else {
                    delay(20)
                }
            }

            val lastFrame = buildBinaryFrame(
                msgType = MSG_AUDIO_ONLY_REQUEST,
                flags   = FLAG_LAST_PACKET_NO_SEQ,
                ser     = SER_NONE,
                comp    = COMP_NONE,
                payload = ByteArray(0)
            )
            ws.send(okio.ByteString.of(*lastFrame))

            var wait = 0
            while (!taskFinished && wait < 100) { delay(100); wait++ }
        } finally {
            scope.launch(Dispatchers.Main) { deliverSpeechResults() }
        }
    }

    private fun handleBinaryResponse(raw: ByteArray) {
        if (raw.size < 4) return

        val byte0 = raw[0].toInt() and 0xFF
        val byte1 = raw[1].toInt() and 0xFF
        val byte2 = raw[2].toInt() and 0xFF

        val headerSizeBytes = (byte0 and 0x0F) * 4
        val msgType         = (byte1 shr 4) and 0x0F
        val flags           = byte1 and 0x0F
        val compression     = byte2 and 0x0F

        val hasSequence = (flags == FLAG_HAS_SEQUENCE || flags == FLAG_LAST_PACKET_WITH_SEQ)
        val payloadOffset = headerSizeBytes + (if (hasSequence) 4 else 0)

        if (raw.size < payloadOffset + 4) return

        val payloadSize = ByteBuffer.wrap(raw, payloadOffset, 4)
            .order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val dataOffset = payloadOffset + 4

        if (raw.size < dataOffset + payloadSize) return

        val payloadBytes = raw.copyOfRange(dataOffset, dataOffset + payloadSize.toInt())

        when (msgType) {
            MSG_FULL_SERVER_RESPONSE -> {
                val json = decompressIfNeeded(payloadBytes, compression)
                val isLast = (flags == FLAG_LAST_PACKET_WITH_SEQ || flags == FLAG_LAST_PACKET_NO_SEQ)
                processRecognitionJson(json, isLast)
            }
            MSG_ERROR_RESPONSE -> {
                if (payloadBytes.size >= 8) {
                    val code    = ByteBuffer.wrap(payloadBytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                    val msgSize = ByteBuffer.wrap(payloadBytes, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    val msg     = if (payloadBytes.size >= 8 + msgSize)
                        String(payloadBytes, 8, msgSize, Charsets.UTF_8) else "?"
                    Log.e(TAG, "ASR error code=$code msg=$msg")
                }
                scope.launch(Dispatchers.Main) { stopSpeechRecognition() }
            }
        }
    }

    private fun decompressIfNeeded(data: ByteArray, compression: Int): String {
        return if (compression == COMP_GZIP) {
            GZIPInputStream(ByteArrayInputStream(data)).bufferedReader(Charsets.UTF_8).readText()
        } else {
            String(data, Charsets.UTF_8)
        }
    }

    private fun processRecognitionJson(json: String, isLast: Boolean) {
        if (speechCancelled) return
        try {
            val obj  = gson.fromJson(json, JsonObject::class.java)
            val text = obj.getAsJsonObject("result")?.get("text")?.asString ?: ""
            if (text.isNotEmpty()) {
                scope.launch(Dispatchers.Main) {
                    if (speechCancelled) return@launch
                    currentInputConnection?.setComposingText(text, 1)
                    resultBuffer = text
                }
            }
            if (isLast) {
                taskFinished = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: ${e.message}")
        }
    }

    private fun buildBinaryFrame(
        msgType: Int, flags: Int, ser: Int, comp: Int, payload: ByteArray
    ): ByteArray {
        val byte0 = ((PROTOCOL_VERSION and 0x0F) shl 4) or (0x01 and 0x0F)
        val byte1 = ((msgType and 0x0F) shl 4) or (flags and 0x0F)
        val byte2 = ((ser and 0x0F) shl 4) or (comp and 0x0F)
        val byte3 = 0x00

        val header = byteArrayOf(byte0.toByte(), byte1.toByte(), byte2.toByte(), byte3.toByte())

        val sizeBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt(payload.size).array()

        return header + sizeBytes + payload
    }

    private fun buildFullClientRequestPayload(): ByteArray {
        val json = JsonObject().apply {
            add("audio", JsonObject().apply {
                addProperty("format",   "pcm")
                addProperty("rate",     ASRConfig.SAMPLE_RATE)
                addProperty("bits",     16)
                addProperty("channel",  1)
            })
            add("request", JsonObject().apply {
                addProperty("model_name",      "bigmodel")
                addProperty("enable_itn",      true)
                addProperty("enable_punc",     true)
                addProperty("show_utterances", true)
                addProperty("result_type",     "full")
            })
        }.toString()
        return json.toByteArray(Charsets.UTF_8)
    }

    private fun commitText(text: String) {
        scope.launch(Dispatchers.Main) {
            currentInputConnection?.commitText(text, 1)
        }
    }

    private fun checkRecordPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestRecordPermission() = PermissionActivity.startPermissionRequest(this)

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ensureOverlay()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (isListening) stopSpeechRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeechRecognition()
        abortSpeechRecognition()
        scope.cancel()
        clipboardHistory?.stop()
        clipboardHistory = null
        try {
            dismissRadialMenu()
            dismissSymbolPanel()
            dismissClipboardPanel()
            dismissResultBubble()
            overlayHelper?.removeAll()
        } catch (_: Exception) {}
        okHttpClient.dispatcher.executorService.shutdown()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
