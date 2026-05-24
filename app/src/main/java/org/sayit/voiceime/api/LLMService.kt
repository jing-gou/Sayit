package org.sayit.voiceime.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.sayit.voiceime.AppSettings
import java.util.concurrent.TimeUnit

interface StreamCallback {
    fun onStart() {}
    fun onDelta(delta: String)
    fun onComplete(fullText: String)
    fun onError(e: Exception)
}

class LLMService(sharedClient: OkHttpClient) {

    private val gson = Gson()
    private val apiUrl get() = AppSettings.llmApiUrl
    private val apiKey get() = AppSettings.llmApiKey
    private val model get() = AppSettings.llmModel

    // SSE streams can idle between tokens — use no read timeout; don't share ASR client's 30s limit
    private val sseClient = sharedClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun askStreaming(question: String, callback: StreamCallback) {
        streamChat(
            systemPrompt = AppSettings.llmPrompt,
            userMessage = question,
            callback = callback
        )
    }

    fun translateStreaming(
        text: String,
        targetLang: String = AppSettings.translationLang,
        callback: StreamCallback
    ) {
        streamChat(
            systemPrompt = "你是一个专业的翻译助手。请将用户输入的文本翻译成$targetLang。只输出翻译结果，不要添加任何解释。",
            userMessage = text,
            callback = callback
        )
    }

    private fun streamChat(systemPrompt: String, userMessage: String, callback: StreamCallback) {
        val body = buildRequestBody(systemPrompt, userMessage, stream = true)
        val request = Request.Builder()
            .url(apiUrl)
            .header("api-key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val fullText = StringBuilder()
        var completed = false

        fun finish() {
            if (completed) return
            completed = true
            callback.onComplete(fullText.toString())
        }

        EventSources.createFactory(sseClient).newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                callback.onStart()
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (completed) return

                if (data == "[DONE]") {
                    finish()
                    return
                }

                try {
                    val json = gson.fromJson(data, JsonObject::class.java)
                    val choice = json.getAsJsonArray("choices")?.get(0)?.asJsonObject ?: return

                    val delta = choice.getAsJsonObject("delta")?.get("content")?.asString
                    if (delta != null) {
                        fullText.append(delta)
                        callback.onDelta(delta)
                    }

                    val finishReason = choice.get("finish_reason")?.takeIf { !it.isJsonNull }?.asString
                    if (finishReason == "stop" || finishReason == "length") {
                        finish()
                    }
                } catch (_: Exception) {
                    // Ignore malformed partial chunks
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!completed && fullText.isNotEmpty()) {
                    finish()
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                if (completed) return

                // OkHttp reports CANCEL when the stream is closed after normal completion — not a real error
                val msg = t?.message.orEmpty()
                if (msg.contains("CANCEL", ignoreCase = true) && fullText.isNotEmpty()) {
                    finish()
                    return
                }

                callback.onError(
                    Exception("SSE failed: ${response?.code} $msg", t)
                )
            }
        })
    }

    private fun buildRequestBody(systemPrompt: String, userMessage: String, stream: Boolean): okhttp3.RequestBody {
        val json = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_completion_tokens", 1024)
            addProperty("stream", stream)

            val messages = JsonArray()
            messages.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemPrompt)
            })
            messages.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", userMessage)
            })
            add("messages", messages)
        }
        return json.toString().toRequestBody("application/json".toMediaType())
    }
}
