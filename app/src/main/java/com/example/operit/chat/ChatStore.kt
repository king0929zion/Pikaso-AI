package com.example.operit.chat

import android.content.Context
import com.example.operit.ai.OpenAiChatClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatStore private constructor(private val context: Context) {
    data class SessionMeta(
        val id: String,
        val updatedAt: Long,
        val preview: String,
    )

    fun listSessions(): List<SessionMeta> {
        val file = indexFile()
        if (!file.exists()) return emptyList()
        val raw = file.readText(Charsets.UTF_8).ifBlank { "[]" }
        val arr = JSONArray(raw)
        val list =
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").trim()
                if (id.isBlank()) return@mapNotNull null
                SessionMeta(
                    id = id,
                    updatedAt = obj.optLong("updatedAt", 0L),
                    preview = obj.optString("preview", ""),
                )
            }
        return list.sortedByDescending { it.updatedAt }
    }

    fun createSession(): SessionMeta {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val meta =
            SessionMeta(
                id = id,
                updatedAt = now,
                preview = "新对话",
            )
        saveIndex(update = meta, remove = null)
        writeMessages(id, emptyList())
        return meta
    }

    fun loadMessages(sessionId: String): List<OpenAiChatClient.Message> {
        val file = sessionFile(sessionId)
        if (!file.exists()) return emptyList()
        val raw = file.readText(Charsets.UTF_8).ifBlank { "[]" }
        val arr = JSONArray(raw)
        val list = ArrayList<OpenAiChatClient.Message>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val role = obj.optString("role").trim()
            if (role.isBlank()) continue
            val content = obj.optString("content", null)
            val toolCallId = obj.optString("tool_call_id", null)
            val toolCalls = decodeToolCalls(obj.optJSONArray("tool_calls"))
            list.add(
                OpenAiChatClient.Message(
                    role = role,
                    content = content,
                    toolCallId = toolCallId,
                    toolCalls = toolCalls.takeIf { it.isNotEmpty() },
                ),
            )
        }
        return list
    }

    fun saveMessages(sessionId: String, messages: List<OpenAiChatClient.Message>) {
        writeMessages(sessionId, messages)

        val now = System.currentTimeMillis()
        val preview = buildPreview(messages).ifBlank { "（无内容）" }
        saveIndex(update = SessionMeta(id = sessionId, updatedAt = now, preview = preview), remove = null)
    }

    fun deleteSession(sessionId: String) {
        try {
            sessionFile(sessionId).delete()
        } catch (_: Exception) {
        }
        saveIndex(update = null, remove = sessionId)
    }

    fun formatTime(ts: Long): String {
        val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return df.format(Date(ts))
    }

    private fun buildPreview(messages: List<OpenAiChatClient.Message>): String {
        val lastUser = messages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
        if (lastUser.isNotBlank()) return lastUser.take(60)
        val lastAssistant = messages.lastOrNull { it.role == "assistant" }?.content?.trim().orEmpty()
        if (lastAssistant.isNotBlank()) return lastAssistant.take(60)
        return ""
    }

    private fun writeMessages(sessionId: String, messages: List<OpenAiChatClient.Message>) {
        val file = sessionFile(sessionId)
        file.parentFile?.mkdirs()
        val arr =
            JSONArray().apply {
                messages.forEach { m ->
                    val obj = JSONObject().put("role", m.role)
                    if (m.content != null) obj.put("content", m.content)
                    if (!m.toolCallId.isNullOrBlank()) obj.put("tool_call_id", m.toolCallId)
                    val toolCalls = m.toolCalls
                    if (!toolCalls.isNullOrEmpty()) {
                        obj.put(
                            "tool_calls",
                            JSONArray().apply {
                                toolCalls.forEach { tc ->
                                    put(
                                        JSONObject()
                                            .put("id", tc.id)
                                            .put("name", tc.name)
                                            .put("arguments", tc.argumentsJson),
                                    )
                                }
                            },
                        )
                    }
                    put(obj)
                }
            }
        file.writeText(arr.toString(), Charsets.UTF_8)
    }

    private fun saveIndex(update: SessionMeta?, remove: String?) {
        val list = listSessions().toMutableList()
        if (remove != null) {
            list.removeAll { it.id == remove }
        }
        if (update != null) {
            list.removeAll { it.id == update.id }
            list.add(update)
        }
        val arr =
            JSONArray().apply {
                list.sortedByDescending { it.updatedAt }.forEach { meta ->
                    put(
                        JSONObject()
                            .put("id", meta.id)
                            .put("updatedAt", meta.updatedAt)
                            .put("preview", meta.preview),
                    )
                }
            }
        val file = indexFile()
        file.parentFile?.mkdirs()
        file.writeText(arr.toString(), Charsets.UTF_8)
    }

    private fun decodeToolCalls(arr: JSONArray?): List<OpenAiChatClient.ToolCall> {
        if (arr == null) return emptyList()
        val list = ArrayList<OpenAiChatClient.ToolCall>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            val args = obj.optString("arguments")
            if (id.isBlank() || name.isBlank()) continue
            list.add(OpenAiChatClient.ToolCall(id = id, name = name, argumentsJson = args))
        }
        return list
    }

    private fun indexFile(): File = File(context.filesDir, "chat/index.json")

    private fun sessionFile(id: String): File = File(context.filesDir, "chat/sessions/$id.json")

    companion object {
        fun get(context: Context): ChatStore = ChatStore(context.applicationContext)
    }
}

