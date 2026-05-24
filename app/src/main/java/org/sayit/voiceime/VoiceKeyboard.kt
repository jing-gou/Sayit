package org.sayit.voiceime

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.widget.Button
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
    private var debugLog: TextView? = null
    @Volatile private var isListening = false
    @Volatile private var taskFinished = false

    private var audioRecord: AudioRecord? = null
    private var webSocket: WebSocket? = null
    private lateinit var okHttpClient: OkHttpClient

    @Volatile private var resultBuffer = ""

    companion object {
        private val gson = Gson()
        private const val CHUNK_SIZE = 6400  // 200ms @ 16kHz 16bit mono

        // ── 文档定义的 header 常量 ──
        private const val PROTOCOL_VERSION = 0x01
        // Message types
        private const val MSG_FULL_CLIENT_REQUEST  = 0x01
        private const val MSG_AUDIO_ONLY_REQUEST   = 0x02
        private const val MSG_FULL_SERVER_RESPONSE = 0x09
        private const val MSG_ERROR_RESPONSE       = 0x0F
        // Flags
        private const val FLAG_NONE                = 0x00
        private const val FLAG_HAS_SEQUENCE        = 0x01
        private const val FLAG_LAST_PACKET_NO_SEQ  = 0x02
        private const val FLAG_LAST_PACKET_WITH_SEQ= 0x03
        // Serialization
        private const val SER_NONE  = 0x00
        private const val SER_JSON  = 0x01
        // Compression
        private const val COMP_NONE = 0x00
        private const val COMP_GZIP = 0x01
    }

    override fun onCreate() {
        super.onCreate()
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        micButton = Button(this).apply {
            text = "🎤 开始录音"
            textSize = 16f
            setOnClickListener { toggleListening() }
        }
        debugLog = TextView(this).apply {
            text = "[DEBUG] 等待操作..."
            textSize = 11f
            setPadding(0, 8, 0, 0)
            setTextColor(0xFFFF0000.toInt())
            maxLines = 10
        }
        layout.addView(micButton)
        layout.addView(debugLog)
        return layout
    }

    private fun toggleListening() {
        if (isListening) stopSpeechRecognition() else startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        if (!checkRecordPermission()) { requestRecordPermission(); return }
        isListening = true
        taskFinished = false
        updateButtonText("🔴 录音中...")
        try { connectToASR() } catch (e: Exception) {
            debugLog("启动失败: ${e.message}")
            stopSpeechRecognition()
        }
    }

    private fun stopSpeechRecognition() {
        isListening = false
        updateButtonText("🎤 开始录音")
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        val ws = webSocket; webSocket = null
        ws?.close(1000, "正常关闭")
        scope.launch { delay(3000); ws?.cancel() }
        if (resultBuffer.isNotEmpty()) { commitText(resultBuffer); resultBuffer = "" }
    }

    // ── 连接，注意补上 X-Api-Connect-Id ──
    private fun connectToASR() {
        val requestId  = UUID.randomUUID().toString()
        val connectId  = UUID.randomUUID().toString()

        val request = Request.Builder()
            .url(ASRConfig.WS_URL)
            .header("X-Api-Key",        ASRConfig.API_KEY)
            .header("X-Api-Resource-Id", ASRConfig.RESOURCE_ID)
            .header("X-Api-Request-Id",  requestId)
            .header("X-Api-Connect-Id",  connectId)   // 文档要求，之前缺失
            .header("X-Api-Sequence",    "-1")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val logId = response.header("X-Tt-Logid") ?: "无"
                debugLog("连接成功 logid=$logId")

                // 第一步：发送 full client request（二进制）
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
                // 文档协议是二进制，文本消息仅作兜底日志
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

    // ── 录音并发送 audio only request ──
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
                // 中间包：flag = FLAG_NONE（非最后一包）
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

        // 发送最后一包（flag = FLAG_LAST_PACKET_NO_SEQ，即 0x02）
        val lastFrame = buildBinaryFrame(
            msgType = MSG_AUDIO_ONLY_REQUEST,
            flags   = FLAG_LAST_PACKET_NO_SEQ,
            ser     = SER_NONE,
            comp    = COMP_NONE,
            payload = ByteArray(0)
        )
        ws.send(okio.ByteString.of(*lastFrame))
        debugLog("最后一包已发送，总计 ${totalBytes / 1024}KB")

        // 等待最终结果
        var wait = 0
        while (!taskFinished && wait < 100) { delay(100); wait++ }
        audioRecord?.release(); audioRecord = null
    }

    // ════════════════════════════════════════════════════
    // 按文档解析二进制响应
    // ════════════════════════════════════════════════════
    private fun handleBinaryResponse(raw: ByteArray) {
        if (raw.size < 4) { debugLog("响应太短: ${raw.size}B"); return }

        val byte0 = raw[0].toInt() and 0xFF
        val byte1 = raw[1].toInt() and 0xFF
        val byte2 = raw[2].toInt() and 0xFF

        val headerSizeBytes = (byte0 and 0x0F) * 4   // 低4位 × 4
        val msgType         = (byte1 shr 4) and 0x0F
        val flags           = byte1 and 0x0F
        val compression     = byte2 and 0x0F

        // sequence number 占4字节，仅当 flags = 0x01 或 0x03 时存在
        val hasSequence = (flags == FLAG_HAS_SEQUENCE || flags == FLAG_LAST_PACKET_WITH_SEQ)
        val payloadOffset = headerSizeBytes + (if (hasSequence) 4 else 0)

        if (raw.size < payloadOffset + 4) { debugLog("响应长度不足"); return }

        // payload size（大端 uint32）
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
                // error: 4字节错误码 + 4字节消息长度 + 消息
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
                scope.launch(Dispatchers.Main) {
                    if (resultBuffer.isNotEmpty()) { commitText(resultBuffer); resultBuffer = "" }
                    stopSpeechRecognition()
                }
            }
        } catch (e: Exception) {
            debugLog("JSON解析失败: ${e.message} raw=${json.take(80)}")
        }
    }

    // ════════════════════════════════════════════════════
    // 按文档构建二进制帧
    // header(4B) + [sequence(4B)] + payloadSize(4B) + payload
    // ════════════════════════════════════════════════════
    private fun buildBinaryFrame(
        msgType: Int, flags: Int, ser: Int, comp: Int, payload: ByteArray
    ): ByteArray {
        // Byte0: version(4) | headerSize(4)  — headerSize固定1，即4字节
        val byte0 = ((PROTOCOL_VERSION and 0x0F) shl 4) or (0x01 and 0x0F)
        // Byte1: msgType(4) | flags(4)
        val byte1 = ((msgType and 0x0F) shl 4) or (flags and 0x0F)
        // Byte2: ser(4) | comp(4)
        val byte2 = ((ser and 0x0F) shl 4) or (comp and 0x0F)
        // Byte3: reserved
        val byte3 = 0x00

        val header = byteArrayOf(byte0.toByte(), byte1.toByte(), byte2.toByte(), byte3.toByte())

        // payload size，大端 uint32
        val sizeBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt(payload.size).array()

        return header + sizeBytes + payload
    }

    // full client request 的 JSON payload（参数见文档表格）
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
            debugLog?.let { it.text = "$line\n${it.text}".take(500) }
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
}