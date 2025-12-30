package com.example.operit.autoglm.runtime

import com.example.operit.ai.AiSettings
import com.example.operit.ai.EndpointCompleter
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AutoGlmVisionClient(
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build(),
) {
    private fun isAutoGlmModel(model: String): Boolean {
        val m = model.trim().lowercase()
        return m == "autoglm-phone" || m.startsWith("autoglm-")
    }

    fun chatOnce(
        settings: AiSettings,
        systemPrompt: String,
        userText: String,
        imageDataUrl: String,
    ): Result<String> {
        return runCatching {
            val endpoint = EndpointCompleter.complete(settings.endpoint)

            val messages =
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "image_url")
                                            .put("image_url", JSONObject().put("url", imageDataUrl)),
                                    )
                                    .put(JSONObject().put("type", "text").put("text", userText)),
                            ),
                    )

            val body =
                JSONObject()
                    .put("model", settings.model)
                    .put("temperature", settings.temperature.toDouble())
                    .put("top_p", settings.topP.toDouble())
                    .put("messages", messages)

            // BigModel 的 autoglm-phone 对部分参数更严格；过大的 max_tokens 可能触发 400/1210（参数错误）。
            // 这里对 AutoGLM 模型不主动传 max_tokens，交由服务端默认值处理。
            if (!isAutoGlmModel(settings.model)) {
                body.put("max_tokens", settings.maxTokens)
            }

            val requestBuilder =
                Request.Builder()
                    .url(endpoint)
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            val apiKey = settings.apiKey.trim()
            if (apiKey.isNotEmpty()) requestBuilder.header("Authorization", "Bearer $apiKey")

            httpClient.newCall(requestBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = decodeErrorDetail(raw)
                    throw IOException("HTTP ${resp.code}: ${detail.ifBlank { raw }}")
                }
                decodeContent(raw)
            }
        }
    }

    private fun decodeErrorDetail(raw: String): String {
        return runCatching {
            val json = JSONObject(raw)
            val errObj = json.optJSONObject("error")
            val code = (errObj?.opt("code") ?: json.opt("code"))?.toString().orEmpty()
            val message =
                errObj?.optString("message")
                    ?: json.optString("message")
                    ?: json.optString("msg")
                    ?: json.optString("error")
            val details =
                errObj?.optString("details")
                    ?: errObj?.optString("detail")
                    ?: json.optString("detail")
                    ?: json.optString("details")
            buildString {
                if (code.isNotBlank()) append(code).append(": ")
                if (message.isNotBlank()) append(message)
                if (details.isNotBlank() && details != message) {
                    if (isNotEmpty()) append(" | ")
                    append(details)
                }
            }.trim()
        }.getOrNull().orEmpty()
    }

    private fun decodeContent(raw: String): String {
        val json = JSONObject(raw)
        val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val content = message.opt("content") ?: return ""
        return when (content) {
            is String -> content.trim()
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    val type = part.optString("type")
                    if (type == "text") sb.append(part.optString("text"))
                }
                sb.toString().trim()
            }
            else -> content.toString().trim()
        }
    }
}
