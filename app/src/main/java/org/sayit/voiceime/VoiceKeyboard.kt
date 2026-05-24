package org.sayit.voiceime

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import org.sayit.voiceime.gesture.GestureAction
import org.sayit.voiceime.overlay.ResultBubbleView
import org.sayit.voiceime.widget.FloatingBallConfig
import org.sayit.voiceime.widget.FloatingBallListener
import org.sayit.voiceime.widget.FloatingBallView

object ASRConfig {
    val WS_URL = BuildConfig.ASR_WS_URL
    val API_KEY = BuildConfig.ASR_API_KEY
    val RESOURCE_ID = BuildConfig.ASR_RESOURCE_ID
    const val SAMPLE_RATE = 16000
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
}

class VoiceKeyboard : InputMethodService() {

    private val TAG = "VoiceKeyboard"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var micButton: Button? = null
    private var debugLogView: TextView? = null
    @Volatile private var isListening = false
    @Volatile private var taskFinished = false

    private var audioRecord: AudioRecord? = null
    private var webSocket: WebSocket? = null
    private lateinit var okHttpClient: OkHttpClient

    @Volatile private var resultBuffer = ""

    private lateinit var floatingBall: FloatingBallView
    private var gestureHandler: GestureActionHandler? = null
    private var llmService: LLMService? = null
    private var resultBubble: ResultBubbleView? = null
    private var currentVoiceMode = VoiceMode.INPUT

    companion object {
        private val gson = Gson()
        private const val CHUNK_SIZE = 6400  // 200ms @ 16kHz 16bit mono

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
            okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            Log.d(TAG, "okHttpClient created")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error", e)
        }
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView called")
        return try {
            buildInputView()
        } catch (e: Throwable) {
            Log.e(TAG, "onCreateInputView CRASH", e)
            // Return a safe fallback view so the IME doesn't crash-loop
            TextView(this).apply {
                text = "Sayit 初始化失败:\n${e.javaClass.simpleName}: ${e.message}"
                textSize = 14f
                setPadding(32, 32, 32, 32)
                setTextColor(0xFFFF0000.toInt())
                setBackgroundColor(0xFF1A1A1A.toInt())
                minimumHeight = 400.dpToPx()
            }
        }
    }

    private fun buildInputView(): View {
        Log.d(TAG, "buildInputView step 1: root + debugPanel")
        val root = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            minimumHeight = 400.dpToPx()
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val debugPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
        micButton = Button(this).apply {
            text = "🎤 开始录音"
            textSize = 16f
            setOnClickListener { toggleListening() }
            visibility = View.GONE
        }
        debugLogView = TextView(this).apply {
            text = "[DEBUG] 等待操作..."
            textSize = 11f
            setPadding(0, 8, 0, 0)
            setTextColor(0xFFFF0000.toInt())
            maxLines = 10
        }
        debugPanel.addView(micButton)
        debugPanel.addView(debugLogView)
        root.addView(debugPanel)

        try {
            Log.d(TAG, "buildInputView step 2: FloatingBallConfig")
            val config = FloatingBallConfig.default(this)

            Log.d(TAG, "buildInputView step 3: FloatingBallView")
            floatingBall = FloatingBallView(this, config).apply {
                listener = object : FloatingBallListener {
                    override fun onGestureAction(action: GestureAction) {
                        android.util.Log.d("VoiceKeyboard", "onGestureAction: $action")
                        gestureHandler?.handle(action)
                    }
                    override fun onVoiceStateChanged(listening: Boolean) {}
                }
                val size = (config.ballRadius * 2.5f).toInt()
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            }
            root.addView(floatingBall)
            Log.d(TAG, "FloatingBallView added OK")
        } catch (e: Throwable) {
            Log.e(TAG, "FloatingBallView CRASH", e)
            val errText = TextView(this).apply {
                text = "悬浮球加载失败:\n${e.javaClass.simpleName}: ${e.message}"
                textSize = 12f
                setPadding(16, 16, 16, 16)
                setTextColor(0xFFFF0000.toInt())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            root.addView(errText)
        }

        try {
            Log.d(TAG, "buildInputView step 4: ResultBubbleView")
            resultBubble = ResultBubbleView(this).apply {
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            root.addView(resultBubble)
            Log.d(TAG, "ResultBubbleView added OK")
        } catch (e: Throwable) {
            Log.e(TAG, "ResultBubbleView CRASH", e)
        }

        try {
            Log.d(TAG, "buildInputView step 5: handlers")
            gestureHandler = GestureActionHandler(this)
            gestureHandler?.setLogCallback { msg -> debugLog(msg) }
            llmService = LLMService(okHttpClient)
            Log.d(TAG, "handlers OK")
        } catch (e: Throwable) {
            Log.e(TAG, "handlers CRASH", e)
        }

        debugLog("UI initialized OK")
        Log.d(TAG, "buildInputView DONE")
        return root
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

    fun undoLastInput() {
        val ic = currentInputConnection ?: return
        ic.commitText("", 1)
    }

    fun updateFloatingBallPosition(rawX: Float, rawY: Float) {
        val parent = floatingBall.parent as? FrameLayout ?: return
        val newX = rawX - floatingBall.width / 2f
        val newY = rawY - floatingBall.height / 2f
        floatingBall.x = newX.coerceIn(0f, (parent.width - floatingBall.width).toFloat())
        floatingBall.y = newY.coerceIn(0f, (parent.height - floatingBall.height).toFloat())
    }

    fun snapFloatingBallToEdge() {
        floatingBall.snapToEdge()
    }

    private fun toggleListening() {
        debugLog("toggleListening: isListening=$isListening")
        if (isListening) stopSpeechRecognition() else startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        debugLog(">>> startSpeechRecognition")
        debugLog("isListening=$isListening")
        Log.d(TAG, "startSpeechRecognition called, isListening=$isListening")
        if (isListening) {
            debugLog("Already listening, return")
            return
        }

        // Check if floatingBall is initialized
        if (!::floatingBall.isInitialized) {
            debugLog("ERROR: floatingBall not initialized!")
            Log.e(TAG, "floatingBall not initialized!")
            return
        }
        debugLog("floatingBall is initialized")

        debugLog("Checking permission...")
        if (!checkRecordPermission()) {
            debugLog("NO PERMISSION - requesting")
            requestRecordPermission()
            return
        }
        debugLog("Permission OK, starting...")
        isListening = true
        taskFinished = false
        currentVoiceMode = VoiceMode.INPUT
        debugLog("Setting ball listening state")
        floatingBall.setListeningState(true)
        updateButtonText("🔴 录音中...")
        try {
            debugLog("Connecting to ASR...")
            connectToASR()
            debugLog("connectToASR() returned")
        } catch (e: Exception) {
            debugLog("ERROR: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "Error starting ASR", e)
            e.printStackTrace()
            stopSpeechRecognition()
        }
    }

    private fun stopSpeechRecognition() {
        isListening = false
        floatingBall.setListeningState(false)
        updateButtonText("🎤 开始录音")
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        val ws = webSocket; webSocket = null
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
                    scope.launch {
                        val result = llmService?.ask(question)
                        result?.onSuccess { answer ->
                            scope.launch(Dispatchers.Main) {
                                showResultBubble(answer)
                            }
                        }
                        result?.onFailure { e ->
                            debugLog("LLM提问失败: ${e.message}")
                        }
                    }
                }
            }
            VoiceMode.TRANSLATE -> {
                val text = resultBuffer
                resultBuffer = ""
                if (text.isNotEmpty()) {
                    scope.launch {
                        val result = llmService?.translate(text)
                        result?.onSuccess { translated ->
                            scope.launch(Dispatchers.Main) {
                                showResultBubble(translated)
                            }
                        }
                        result?.onFailure { e ->
                            debugLog("翻译失败: ${e.message}")
                        }
                    }
                }
            }
        }
        currentVoiceMode = VoiceMode.INPUT
    }

    private fun showResultBubble(text: String) {
        resultBubble?.show(text, floatingBall)
        resultBubble?.onInsert = { result ->
            currentInputConnection?.commitText(result, 1)
        }
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
                val logId = response.header("X-Tt-Logid") ?: "无"
                debugLog("连接成功 logid=$logId")

                val payload = buildFullClientRequestPayload()
                val frame   = buildBinaryFrame(
                    msgType  = MSG_FULL_CLIENT_REQUEST,
                    flags    = FLAG_NONE,
                    ser      = SER_JSON,
                    comp     = COMP_NONE,
                    payload  = payload
                )
                webSocket.send(okio.ByteString.of(*frame))
                debugLog("full client request 已发送 ${frame.size}B")

                scope.launch {
                    try { startRecording(webSocket) }
                    catch (e: Exception) {
                        debugLog("录音失败: ${e.message}")
                        scope.launch(Dispatchers.Main) { stopSpeechRecognition() }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryResponse(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                debugLog("收到文本（非预期）: ${text.take(100)}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                debugLog("连接失败: ${response?.code} ${t.message}")
                scope.launch(Dispatchers.Main) { stopSpeechRecognition() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                debugLog("连接关闭: $code")
                if (isListening) scope.launch(Dispatchers.Main) { stopSpeechRecognition() }
            }
        })
    }

    private suspend fun startRecording(ws: WebSocket) = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            ASRConfig.SAMPLE_RATE, ASRConfig.CHANNEL_CONFIG, ASRConfig.AUDIO_FORMAT)
        check(bufferSize > 0) { "无效 bufferSize: $bufferSize" }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            ASRConfig.SAMPLE_RATE,
            ASRConfig.CHANNEL_CONFIG,
            ASRConfig.AUDIO_FORMAT,
            bufferSize
        )
        check(audioRecord?.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord 初始化失败" }

        audioRecord?.startRecording()
        debugLog("开始录音")

        val buffer = ByteArray(CHUNK_SIZE)
        var totalBytes = 0

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
                totalBytes += read
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
        debugLog("最后一包已发送，总计 ${totalBytes / 1024}KB")

        var wait = 0
        while (!taskFinished && wait < 100) { delay(100); wait++ }
        audioRecord?.release(); audioRecord = null
    }

    private fun handleBinaryResponse(raw: ByteArray) {
        if (raw.size < 4) { debugLog("响应太短: ${raw.size}B"); return }

        val byte0 = raw[0].toInt() and 0xFF
        val byte1 = raw[1].toInt() and 0xFF
        val byte2 = raw[2].toInt() and 0xFF

        val headerSizeBytes = (byte0 and 0x0F) * 4
        val msgType         = (byte1 shr 4) and 0x0F
        val flags           = byte1 and 0x0F
        val compression     = byte2 and 0x0F

        val hasSequence = (flags == FLAG_HAS_SEQUENCE || flags == FLAG_LAST_PACKET_WITH_SEQ)
        val payloadOffset = headerSizeBytes + (if (hasSequence) 4 else 0)

        if (raw.size < payloadOffset + 4) { debugLog("响应长度不足"); return }

        val payloadSize = ByteBuffer.wrap(raw, payloadOffset, 4)
            .order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val dataOffset = payloadOffset + 4

        if (raw.size < dataOffset + payloadSize) {
            debugLog("payload 长度不符 expected=$payloadSize actual=${raw.size - dataOffset}")
            return
        }

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
                    debugLog("服务端错误 code=$code msg=$msg")
                }
                scope.launch(Dispatchers.Main) { stopSpeechRecognition() }
            }
            else -> debugLog("未知 msgType=0x${msgType.toString(16)}")
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
        try {
            val obj  = gson.fromJson(json, JsonObject::class.java)
            val text = obj.getAsJsonObject("result")?.get("text")?.asString ?: ""
            if (text.isNotEmpty()) {
                debugLog("识别: $text")
                scope.launch(Dispatchers.Main) {
                    currentInputConnection?.setComposingText(text, 1)
                    resultBuffer = text
                }
            }
            if (isLast) {
                taskFinished = true
            }
        } catch (e: Exception) {
            debugLog("JSON解析失败: ${e.message} raw=${json.take(80)}")
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

    private fun updateButtonText(text: String) { micButton?.text = text }

    private fun debugLog(msg: String) {
        Log.d(TAG, msg)
        val line = "[${System.currentTimeMillis() % 100000}] $msg"
        android.os.Handler(mainLooper).post {
            debugLogView?.let { it.text = "$line\n${it.text}".take(500) }
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateButtonText(if (isListening) "🔴 录音中..." else "🎤 开始录音")
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (isListening) stopSpeechRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeechRecognition()
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}