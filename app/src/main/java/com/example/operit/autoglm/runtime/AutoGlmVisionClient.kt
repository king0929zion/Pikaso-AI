package com.example.operit.autoglm.runtime

import com.example.operit.ai.AiSettings
import com.example.operit.ai.EndpointCompleter
import com.example.operit.logging.AppLog
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class AutoGlmVisionClient(
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
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
            val isAutoGlm = isAutoGlmModel(settings.model)

            val messages = JSONArray()
            // 对齐官方 Open-AutoGLM：使用 system role
            if (systemPrompt.isNotBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt.trim()))
            }

            val userContent =
                JSONArray()
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageDataUrl)))
                    .put(JSONObject().put("type", "text").put("text", userText.trim()))
            messages.put(JSONObject().put("role", "user").put("content", userContent))

            val body = JSONObject()
            body.put("model", settings.model)
            // 对齐 Open-AutoGLM：autoglm-phone 默认走 stream=true（SSE）
            body.put("stream", isAutoGlm)
            body.put("messages", messages)

            body.put("temperature", settings.temperature.toDouble())
            body.put("top_p", settings.topP.toDouble())

            if (isAutoGlm) {
                val maxTokens =
                    settings.maxTokens
                        .takeIf { it > 0 }
                        ?.coerceIn(1, 8192)
                        ?: 3000
                body.put("max_tokens", maxTokens)
                body.put("frequency_penalty", 0.2)
            } else {
                body.put("max_tokens", settings.maxTokens)
            }

            val requestBuilder =
                Request.Builder()
                    .url(endpoint)
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            val apiKey = settings.apiKey.trim()
            if (apiKey.isNotEmpty()) requestBuilder.header("Authorization", "Bearer $apiKey")

            AppLog.d("AutoGLM", "request endpoint=$endpoint model=${settings.model} body=${sanitizeForLog(body)}")

            httpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    val detail = decodeErrorDetail(raw)
                    throw IOException("HTTP ${resp.code}: ${detail.ifBlank { raw }}")
                }

                if (isAutoGlm && body.optBoolean("stream", false)) {
                    decodeContentFromSse(resp)
                } else {
                    val raw = resp.body?.string().orEmpty()
                    decodeContent(raw)
                }
            }
        }
    }

    private fun sanitizeForLog(body: JSONObject): String {
        return runCatching {
            val copy = JSONObject(body.toString())
            val messages = copy.optJSONArray("messages") ?: return@runCatching copy.toString()
            for (i in 0 until messages.length()) {
                val msg = messages.optJSONObject(i) ?: continue
                val content = msg.opt("content")
                if (content !is JSONArray) continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    if (part.optString("type") != "image_url") continue
                    val imageUrl = part.optJSONObject("image_url") ?: continue
                    val url = imageUrl.optString("url")
                    if (!url.startsWith("data:", ignoreCase = true)) continue
                    val commaIdx = url.indexOf(',')
                    val meta = if (commaIdx > 0) url.substring(0, commaIdx) else "data:<unknown>"
                    val b64Len = if (commaIdx > 0) (url.length - commaIdx - 1).coerceAtLeast(0) else url.length
                    imageUrl.put("url", "$meta,<base64_len=$b64Len>")
                }
            }
            copy.toString()
        }.getOrElse { "<sanitize_failed>" }
    }

    private fun decodeContentFromSse(resp: Response): String {
        val body = resp.body ?: return ""
        val source = body.source()
        val sb = StringBuilder()

        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue
            if (!line.startsWith("data:", ignoreCase = true)) continue

            val data = line.substringAfter("data:").trim()
            if (data.isBlank()) continue
            if (data == "[DONE]") break

            val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
            val choices = json.optJSONArray("choices") ?: continue
            val choice0 = choices.optJSONObject(0) ?: continue

            val delta = choice0.optJSONObject("delta")
            val msg = choice0.optJSONObject("message")

            val piece =
                when {
                    delta != null -> delta.optString("content").takeIf { it.isNotBlank() }
                    msg != null -> extractTextFromContent(msg.opt("content")).takeIf { it.isNotBlank() }
                    else -> null
                }
            if (piece != null) sb.append(piece)
        }

        return sb.toString().trim()
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

    private fun extractTextFromContent(content: Any?): String {
        return when (content) {
            is String -> content
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    if (part.optString("type") == "text") sb.append(part.optString("text"))
                }
                sb.toString()
            }
            else -> content?.toString().orEmpty()
        }
    }

    private fun decodeContent(raw: String): String {
        val json = JSONObject(raw)
        val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        return extractTextFromContent(message.opt("content")).trim()
    }
}
