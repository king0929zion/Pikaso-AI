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
    data class ToolDefinition(
        val name: String,
        val description: String,
        val parameters: JSONObject,
    )

    data class ToolCall(
        val id: String,
        val name: String,
        val argumentsJson: String,
    )

    data class Message(
        val role: String,
        val content: String? = null,
        val toolCallId: String? = null,
        val toolCalls: List<ToolCall>? = null,
    )

    data class ChatResult(
        val assistantMessage: Message,
        val content: String,
        val toolCalls: List<ToolCall>,
    )

    fun chat(
        settings: AiSettings,
        messages: List<Message>,
        onResult: (Result<String>) -> Unit,
    ): Call {
        return chatWithTools(
            settings = settings,
            messages = messages,
            tools = emptyList(),
            onResult = { result ->
                onResult(result.map { it.content })
            },
        )
    }

    fun chatWithTools(
        settings: AiSettings,
        messages: List<Message>,
        tools: List<ToolDefinition>,
        onResult: (Result<ChatResult>) -> Unit,
    ): Call {
        val endpoint = EndpointCompleter.complete(settings.endpoint)

        val bodyJson = JSONObject()
            .put("model", settings.model)
            .put("temperature", settings.temperature.toDouble())
            .put("top_p", settings.topP.toDouble())
            .put("max_tokens", settings.maxTokens)
            .put("messages", encodeMessages(messages))

        if (tools.isNotEmpty()) {
            bodyJson.put(
                "tools",
                JSONArray().apply {
                    tools.forEach { tool ->
                        put(
                            JSONObject()
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", tool.name)
                                        .put("description", tool.description)
                                        .put("parameters", tool.parameters),
                                ),
                        )
                    }
                },
            )
            bodyJson.put("tool_choice", "auto")
        }

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
                            val message =
                                json
                                    .getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")

                            val content =
                                if (message.has("content")) {
                                    message.optString("content", "").trim()
                                } else {
                                    ""
                                }

                            val toolCalls = decodeToolCalls(message.optJSONArray("tool_calls"))
                            val assistantMessage =
                                Message(
                                    role = "assistant",
                                    content = content.ifBlank { null },
                                    toolCalls = toolCalls.takeIf { it.isNotEmpty() },
                                )

                            onResult(
                                Result.success(
                                    ChatResult(
                                        assistantMessage = assistantMessage,
                                        content = content,
                                        toolCalls = toolCalls,
                                    ),
                                ),
                            )
                        } catch (e: Exception) {
                            onResult(Result.failure(IOException("解析失败：${e.message}\n$raw", e)))
                        }
                    }
                }
            },
        )

        return call
    }

    private fun encodeMessages(messages: List<Message>): JSONArray {
        return JSONArray().apply {
            messages.forEach { msg ->
                val obj = JSONObject().put("role", msg.role)
                if (msg.content != null) obj.put("content", msg.content)

                if (msg.role == "tool") {
                    val id = msg.toolCallId
                    if (!id.isNullOrBlank()) obj.put("tool_call_id", id)
                }

                val toolCalls = msg.toolCalls
                if (msg.role == "assistant" && !toolCalls.isNullOrEmpty()) {
                    obj.put(
                        "tool_calls",
                        JSONArray().apply {
                            toolCalls.forEach { tc ->
                                put(
                                    JSONObject()
                                        .put("id", tc.id)
                                        .put("type", "function")
                                        .put(
                                            "function",
                                            JSONObject()
                                                .put("name", tc.name)
                                                .put("arguments", tc.argumentsJson),
                                        ),
                                )
                            }
                        },
                    )
                }

                put(obj)
            }
        }
    }

    private fun decodeToolCalls(arr: JSONArray?): List<ToolCall> {
        if (arr == null) return emptyList()
        val list = ArrayList<ToolCall>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val fn = obj.optJSONObject("function")
            val name = fn?.optString("name").orEmpty()
            val args = fn?.optString("arguments").orEmpty()
            if (id.isBlank() || name.isBlank()) continue
            list.add(ToolCall(id = id, name = name, argumentsJson = args))
        }
        return list
    }
}

