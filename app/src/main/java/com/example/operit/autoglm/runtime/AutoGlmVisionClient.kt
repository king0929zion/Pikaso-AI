package com.example.operit.autoglm.runtime

import com.example.operit.ai.AiSettings
import com.example.operit.ai.EndpointCompleter
import com.example.operit.logging.AppLog
import java.io.IOException
import kotlin.math.round
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
    private fun round2(value: Float): Double {
        if (!value.isFinite()) return 0.0
        val v = value.toDouble()
        return round(v * 100.0) / 100.0
    }

    private fun clampTemperature(value: Float): Double = round2(value).coerceIn(0.0, 1.0)

    private fun clampTopP(value: Float): Double = round2(value).coerceIn(0.01, 1.0)

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

            val normalizedImage = normalizeBigModelImageUrl(endpoint, imageDataUrl)
            val originalImage = imageDataUrl.trim()
            val baseBodies =
                buildList {
                    add(buildBaseBody(settings, systemPrompt, userText, normalizedImage, isAutoGlm))
                    if (normalizedImage != originalImage) {
                        add(buildBaseBody(settings, systemPrompt, userText, originalImage, isAutoGlm))
                    }
                }

            val attempts =
                if (!isAutoGlm) {
                    listOf(Attempt("default", JSONObject(baseBodies.first().toString()).put("stream", false)))
                } else {
                    val maxTokens =
                        settings.maxTokens
                            .takeIf { it > 0 }
                            ?.coerceIn(1, 4096)
                            ?: 3000

                    baseBodies.flatMapIndexed { idx, base0 ->
                        val base = JSONObject(base0.toString()).put("max_tokens", maxTokens)
                        val minimal =
                            JSONObject(base.toString()).apply {
                                remove("temperature")
                                remove("top_p")
                                remove("do_sample")
                                put("do_sample", false)
                                put("temperature", 0.0)
                                put("stream", false)
                            }
                        val suffix = if (idx == 0) "normalized" else "original"
                        listOf(
                            Attempt("autoglm_${suffix}_stream_true", JSONObject(base.toString()).put("stream", true)),
                            Attempt("autoglm_${suffix}_stream_false", JSONObject(base.toString()).put("stream", false)),
                            Attempt("autoglm_${suffix}_minimal", minimal),
                        )
                    }
                }

            var lastError: Throwable? = null
            for (attempt in attempts) {
                try {
                    return@runCatching executeOnce(endpoint, settings, attempt)
                } catch (e: Throwable) {
                    lastError = e
                    if (!shouldRetry(e)) throw e
                    AppLog.w("AutoGLM", "attempt failed name=${attempt.name} err=${e.message ?: e.javaClass.simpleName}")
                }
            }

            throw (lastError ?: IllegalStateException("模型调用失败：未知错误"))
        }
    }

    private data class Attempt(val name: String, val body: JSONObject)

    private fun executeOnce(endpoint: String, settings: AiSettings, attempt: Attempt): String {
        val requestBuilder =
            Request.Builder()
                .url(endpoint)
                .post(attempt.body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        val apiKey = settings.apiKey.trim()
        if (apiKey.isNotEmpty()) requestBuilder.header("Authorization", "Bearer $apiKey")

        if (attempt.body.optBoolean("stream", false)) {
            requestBuilder.header("Accept", "text/event-stream")
            requestBuilder.header("Cache-Control", "no-cache")
        }

        AppLog.d(
            "AutoGLM",
            "request attempt=${attempt.name} endpoint=$endpoint model=${settings.model} body=${sanitizeForLog(attempt.body)}",
        )

        httpClient.newCall(requestBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                val detail = decodeErrorDetail(raw)
                throw IOException("HTTP ${resp.code}: ${detail.ifBlank { raw }}")
            }

            return if (attempt.body.optBoolean("stream", false)) {
                decodeContentFromSse(resp)
            } else {
                val raw = resp.body?.string().orEmpty()
                decodeContent(raw)
            }
        }
    }

    private fun shouldRetry(e: Throwable): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("1210") || msg.contains("API 调用参数有误") || msg.contains("API调用参数有误")
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
                    if (url.isBlank()) continue
                    if (url.startsWith("data:", ignoreCase = true)) {
                        val commaIdx = url.indexOf(',')
                        val meta = if (commaIdx > 0) url.substring(0, commaIdx) else "data:<unknown>"
                        val b64Len = if (commaIdx > 0) (url.length - commaIdx - 1).coerceAtLeast(0) else url.length
                        imageUrl.put("url", "$meta,<base64_len=$b64Len>")
                    } else if (url.length > 120) {
                        imageUrl.put("url", "<len=${url.length}>")
                    }
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

    private fun normalizeBigModelImageUrl(endpoint: String, input: String): String {
        val trimmed = input.trim()
        if (!endpoint.contains("open.bigmodel.cn", ignoreCase = true)) return trimmed

        // BigModel OpenAPI 的 image_url.url 描述为“URL 地址或 Base64 编码”
        // 优先传纯 Base64，避免 data:image/...;base64,<...> 触发 400/1210。
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            val commaIdx = trimmed.indexOf(',')
            if (commaIdx > 0 && commaIdx < trimmed.length - 1) {
                return trimmed.substring(commaIdx + 1).trim()
            }
        }
        return trimmed
    }

    private fun buildBaseBody(
        settings: AiSettings,
        systemPrompt: String,
        userText: String,
        imageUrl: String,
        isAutoGlm: Boolean,
    ): JSONObject {
        val messages = JSONArray()

        // 对齐官方 OpenAPI：system role 可选
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt.trim()))
        }

        val userContent =
            JSONArray()
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", imageUrl)))
                .put(JSONObject().put("type", "text").put("text", userText.trim()))
        messages.put(JSONObject().put("role", "user").put("content", userContent))

        return JSONObject().apply {
            put("model", settings.model)
            put("messages", messages)
            if (isAutoGlm) {
                // AutoGLM-Phone：按官方默认值与范围规范化，避免 1210（参数错误）
                put("do_sample", false)
                put("temperature", 0.0)
                put("top_p", 0.85)
            } else {
                put("temperature", clampTemperature(settings.temperature))
                put("top_p", clampTopP(settings.topP))
            }
            settings.maxTokens.takeIf { it > 0 }?.let { put("max_tokens", it) }
        }
    }
}
