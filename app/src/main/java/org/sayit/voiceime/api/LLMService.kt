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
import org.sayit.voiceime.BuildConfig

class LLMService(private val client: OkHttpClient) {

    private val gson = Gson()
    private val apiUrl = BuildConfig.LLM_API_URL
    private val apiKey = BuildConfig.LLM_API_KEY

    suspend fun ask(question: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = buildRequestBody(
                systemPrompt = "你是一个智能助手，请简洁回答用户的问题。回答要准确、有帮助。",
                userMessage = question
            )
            val request = Request.Builder()
                .url(apiUrl)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val result = parseResponse(response.body?.string())
                Result.success(result)
            } else {
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun translate(text: String, targetLang: String = "英文"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = buildRequestBody(
                systemPrompt = "你是一个专业的翻译助手。请将用户输入的文本翻译成$targetLang。只输出翻译结果，不要添加任何解释。",
                userMessage = text
            )
            val request = Request.Builder()
                .url(apiUrl)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val result = parseResponse(response.body?.string())
                Result.success(result)
            } else {
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRequestBody(systemPrompt: String, userMessage: String): okhttp3.RequestBody {
        val json = JsonObject().apply {
            addProperty("model", BuildConfig.LLM_MODEL)
            addProperty("max_completion_tokens", 1024)
            addProperty("stream", false)

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

    private fun parseResponse(responseBody: String?): String {
        if (responseBody == null) return "无响应"
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            json.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?: "解析失败"
        } catch (e: Exception) {
            "解析错误: ${e.message}"
        }
    }
}