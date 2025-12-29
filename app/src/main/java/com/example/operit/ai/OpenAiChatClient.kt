package com.example.operit.ai

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiChatClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    data class Message(val role: String, val content: String)

    fun chat(
        settings: AiSettings,
        messages: List<Message>,
        onResult: (Result<String>) -> Unit,
    ): Call {
        val endpoint = EndpointCompleter.complete(settings.endpoint)

        val bodyJson = JSONObject()
            .put("model", settings.model)
            .put("temperature", settings.temperature.toDouble())
            .put("top_p", settings.topP.toDouble())
            .put("max_tokens", settings.maxTokens)
            .put(
                "messages",
                JSONArray().apply {
                    messages.forEach { msg ->
                        put(JSONObject().put("role", msg.role).put("content", msg.content))
                    }
                },
            )

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        val apiKey = settings.apiKey.trim()
        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val call = httpClient.newCall(requestBuilder.build())
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onResult(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            onResult(Result.failure(IOException("HTTP ${response.code}: $raw")))
                            return
                        }

                        try {
                            val json = JSONObject(raw)
                            val content = json
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .optString("content", "")
                                .trim()

                            if (content.isBlank()) {
                                onResult(Result.failure(IOException("模型返回为空：$raw")))
                            } else {
                                onResult(Result.success(content))
                            }
                        } catch (e: Exception) {
                            onResult(Result.failure(IOException("解析失败：${e.message}\n$raw", e)))
                        }
                    }
                }
            },
        )

        return call
    }
}

