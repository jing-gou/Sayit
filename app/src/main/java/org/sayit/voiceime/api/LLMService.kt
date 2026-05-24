package org.sayit.voiceime.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.sayit.voiceime.AppSettings

interface StreamCallback {
    fun onStart() {}
    fun onDelta(delta: String)
    fun onComplete(fullText: String)
    fun onError(e: Exception)
}

class LLMService(private val client: OkHttpClient) {

    private val gson = Gson()
    private val apiUrl get() = AppSettings.llmApiUrl
    private val apiKey get() = AppSettings.llmApiKey
    private val model get() = AppSettings.llmModel

    fun askStreaming(question: String, callback: StreamCallback) {
        val body = buildRequestBody(
            systemPrompt = AppSettings.llmPrompt,
            userMessage = question,
            stream = true
        )
        val request = Request.Builder()
            .url(apiUrl)
            .header("api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val factory = EventSources.createFactory(client)
        val fullText = StringBuilder()

        factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                callback.onStart()
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    eventSource.cancel()
                    callback.onComplete(fullText.toString())
                    return
                }
                try {
                    val json = gson.fromJson(data, JsonObject::class.java)
                    val delta = json.getAsJsonArray("choices")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("delta")
                        ?.get("content")?.asString
                    if (delta != null) {
                        fullText.append(delta)
                        callback.onDelta(delta)
                    }
                } catch (_: Exception) {
                    // Ignore parse errors for partial chunks
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                callback.onError(Exception("SSE failed: ${response?.code} ${t?.message}", t))
            }

            override fun onClosed(eventSource: EventSource) {
                if (fullText.isNotEmpty()) {
                    callback.onComplete(fullText.toString())
                }
            }
        })
    }

    fun translateStreaming(text: String, targetLang: String = AppSettings.translationLang, callback: StreamCallback) {
        val body = buildRequestBody(
            systemPrompt = "你是一个专业的翻译助手。请将用户输入的文本翻译成$targetLang。只输出翻译结果，不要添加任何解释。",
            userMessage = text,
            stream = true
        )
        val request = Request.Builder()
            .url(apiUrl)
            .header("api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val factory = EventSources.createFactory(client)
        val fullText = StringBuilder()

        factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                callback.onStart()
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    eventSource.cancel()
                    callback.onComplete(fullText.toString())
                    return
                }
                try {
                    val json = gson.fromJson(data, JsonObject::class.java)
                    val delta = json.getAsJsonArray("choices")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("delta")
                        ?.get("content")?.asString
                    if (delta != null) {
                        fullText.append(delta)
                        callback.onDelta(delta)
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                callback.onError(Exception("SSE failed: ${response?.code} ${t?.message}", t))
            }

            override fun onClosed(eventSource: EventSource) {
                if (fullText.isNotEmpty()) {
                    callback.onComplete(fullText.toString())
                }
            }
        })
    }

    private fun buildRequestBody(systemPrompt: String, userMessage: String, stream: Boolean = false): okhttp3.RequestBody {
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
