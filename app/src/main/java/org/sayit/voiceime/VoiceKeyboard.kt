package org.sayit.voiceime

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString

// ============================================================

// 🔧 配置区 - 从 BuildConfig 读取配置
// ============================================================
object ASRConfig {
    // WebSocket URL（从火山引擎获取）
    val WS_URL = BuildConfig.ASR_WS_URL

    // AppID
    val APP_ID = BuildConfig.ASR_APP_ID

    // AccessKey ID
    val ACCESS_KEY_ID = BuildConfig.ASR_ACCESS_KEY_ID

    // AccessKey Secret
    val ACCESS_KEY_SECRET = BuildConfig.ASR_ACCESS_KEY_SECRET

    // 音频采样率（通常是 16000）
    const val SAMPLE_RATE = 16000

    // 音频格式（通常是 PCM 16-bit）
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // 声道（单声道）
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
}

// ============================================================
// 🎤 语音输入法核心类
// ============================================================
class VoiceKeyboard : InputMethodService() {

    private val TAG = "VoiceKeyboard"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var micButton: Button? = null
    private var isListening = false

    // 录音相关
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // WebSocket 相关
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null

    // 识别结果缓冲
    private var resultBuffer = ""

    override fun onCreateInputView(): View {
        micButton = Button(this).apply {
            text = if (isListening) "停止录音" else "🎤 开始录音"
            setOnClickListener { toggleListening() }
        }
        return micButton!!
    }

    // ============================================================
    // 🎯 切换录音状态
    // ============================================================
    private fun toggleListening() {
        if (isListening) {
            stopSpeechRecognition()
        } else {
            startSpeechRecognition()
        }
    }

    // ============================================================
    // 🚀 开始语音识别
    // ============================================================
    private fun startSpeechRecognition() {
        // 检查录音权限
        if (!checkRecordPermission()) {
            requestRecordPermission()
            return
        }

        isListening = true
        updateButtonText("🔴 录音中...")

        // 初始化 WebSocket 客户端
        okHttpClient = OkHttpClient.Builder()
            .build()

        // 建立连接
        connectToASR()
    }

    // ============================================================
    // 🛑 停止语音识别
    // ============================================================
    private fun stopSpeechRecognition() {
        isListening = false
        updateButtonText("🎤 开始录音")

        // 停止录音
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // 关闭连接
        webSocket?.close(1000, "正常关闭")
        webSocket = null

        // 提交最后的识别结果
        if (resultBuffer.isNotEmpty()) {
            commitText(resultBuffer)
            resultBuffer = ""
        }
    }

    // ============================================================
    // 🔌 连接语音识别服务
    // ============================================================
    private fun connectToASR() {
        val request = Request.Builder()
            .url(ASRConfig.WS_URL)
            .addHeader("App-Id", ASRConfig.APP_ID)
            .addHeader("Access-Key-Id", ASRConfig.ACCESS_KEY_ID)
            // TODO: 添加签名信息
            .build()

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {

            // 连接成功
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 连接成功")
                scope.launch {
                    startRecording(ws)
                }
            }

            // 收到消息
            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "收到识别结果: $text")
                handleASRResult(text)
            }

            // 收到二进制消息
            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                Log.d(TAG, "收到二进制消息")
            }

            // 连接关闭
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接关闭: $code - $reason")
            }

            // 连接已关闭
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接已关闭")
            }

            // 连接失败
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "连接失败", t)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(this@VoiceKeyboard, "连接失败: ${t.message}", Toast.LENGTH_SHORT).show()
                    stopSpeechRecognition()
                }
            }
        })
    }

    // ============================================================
    // 🎙️ 开始录音
    // ============================================================
    private suspend fun startRecording(ws: WebSocket) = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            ASRConfig.SAMPLE_RATE,
            ASRConfig.CHANNEL_CONFIG,
            ASRConfig.AUDIO_FORMAT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            ASRConfig.SAMPLE_RATE,
            ASRConfig.CHANNEL_CONFIG,
            ASRConfig.AUDIO_FORMAT,
            bufferSize
        )

        audioRecord?.startRecording()

        val buffer = ByteArray(bufferSize)

        // 循环录音并发送
        while (isActive && isListening) {
            val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readBytes > 0) {
                // 发送音频数据
                val byteBuffer = buffer.copyOfRange(0, readBytes)
                ws.send(byteBuffer.toByteString())
            }
        }
    }

    // ============================================================
    // 📝 处理识别结果
    // ============================================================
    private fun handleASRResult(json: String) {
        // TODO: 解析 JSON 提取识别文本
        // 这里是示例，实际需要根据火山引擎的返回格式解析

        // 示例：假设返回格式是 {"result": "识别的文本"}
        // 你需要根据实际 API 返回格式修改这里

        scope.launch(Dispatchers.Main) {
            // 解析并显示临时结果
            // tempCommitText(text)

            // 保存最终结果
            // resultBuffer += text
        }
    }

    // ============================================================
    // ✏️ 提交文本到输入框
    // ============================================================
    private fun commitText(text: String) {
        scope.launch(Dispatchers.Main) {
            currentInputConnection?.commitText(text, 1)
        }
    }

    // 临时提交（不换行，可以替换之前的临时结果）
    private fun tempCommitText(text: String) {
        // 需要实现删除临时文本再提交的逻辑
        // 这里简化处理
    }

    // ============================================================
    // 🔐 权限管理
    // ============================================================
    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        // IME 需要通过 Activity 请求权限
        // 这里简化处理，实际需要启动一个 Activity 来请求
        Toast.makeText(this, "请在设置中授予录音权限", Toast.LENGTH_LONG).show()
    }

    // ============================================================
    // 🎨 UI 更新
    // ============================================================
    private fun updateButtonText(text: String) {
        micButton?.text = text
    }

    // ============================================================
    // 🔄 生命周期
    // ============================================================
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateButtonText(if (isListening) "🔴 录音中..." else "🎤 开始录音")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpeechRecognition()
        scope.cancel()
        okHttpClient?.dispatcher?.executorService?.shutdown()
    }
}