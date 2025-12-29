package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.AiSettings
import com.example.operit.ai.OpenAiChatClient
import com.example.operit.chat.ChatStore
import com.example.operit.logging.AppLog
import com.example.operit.prompts.PromptPreferences
import com.example.operit.toolsystem.ChatToolRegistry
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import org.json.JSONObject

class ChatFragment : Fragment() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View

    private var inFlightCall: Call? = null
    private var isSending = false

    private val chatClient = OpenAiChatClient()
    private val conversation = mutableListOf<OpenAiChatClient.Message>()
    private val tools by lazy { ChatToolRegistry.defaultTools() }

    private val maxToolRounds = 4

    private var sessionId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.chatRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)
        val input = view.findViewById<EditText>(R.id.chatInput)
        val btnSend = view.findViewById<MaterialButton>(R.id.btnSend)
        val btnTools = view.findViewById<View>(R.id.btnTools)
        val btnAttach = view.findViewById<View>(R.id.btnAttach)

        adapter = ChatAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        btnTools.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ToolsFragment())
                .addToBackStack(null)
                .commit()
        }
        btnAttach.setOnClickListener {
            Toast.makeText(requireContext(), "附件功能后续迁移", Toast.LENGTH_SHORT).show()
        }

        btnSend.setOnClickListener {
            val text = input.text.toString()
            if (text.isNotBlank()) {
                sendMessage(text)
                input.text.clear()
            }
        }

        ensureSessionLoaded()
    }

    private fun ensureSessionLoaded() {
        if (sessionId != null) return

        val ctx = context ?: return
        val store = ChatStore.get(ctx)
        val idFromArgs = arguments?.getString(ARG_SESSION_ID).orEmpty().trim()
        val id =
            if (idFromArgs.isNotBlank()) {
                idFromArgs
            } else {
                store.createSession().id
            }
        sessionId = id

        val saved = store.loadMessages(id)
        if (saved.isNotEmpty()) {
            conversation.clear()
            conversation.addAll(saved)
            renderConversation()
            emptyState.visibility = View.GONE
        }
    }

    private fun renderConversation() {
        conversation.forEach { m ->
            when (m.role) {
                "user" -> adapter.addMessage(ChatAdapter.Message(m.content.orEmpty(), true))
                "assistant" -> {
                    val text =
                        when {
                            !m.content.isNullOrBlank() -> m.content
                            !m.toolCalls.isNullOrEmpty() -> "（正在调用工具…）"
                            else -> "（无内容）"
                        }
                    adapter.addMessage(ChatAdapter.Message(text ?: "", false))
                }
                // system/tool 不直接展示在对话列表里
            }
        }
        recyclerView.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
    }

    private fun persistAsync() {
        val ctx = context ?: return
        val id = sessionId ?: return
        val snapshot = conversation.toList()
        Thread {
            runCatching { ChatStore.get(ctx).saveMessages(id, snapshot) }
            activity?.runOnUiThread {
                (activity as? MainActivity)?.refreshHistory()
            }
        }.start()
    }

    private fun sendMessage(text: String) {
        if (isSending) return

        if (emptyState.visibility == View.VISIBLE) {
            emptyState.visibility = View.GONE
        }
        adapter.addMessage(ChatAdapter.Message(text, true))
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

        val ctx = context ?: return
        val settings = AiPreferences.get(ctx).load()
        if (settings.endpoint.isBlank() || settings.model.isBlank()) {
            adapter.addMessage(ChatAdapter.Message("请先在“设置 -> AI 配置”中填写 Endpoint 和模型名。", false))
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            AppLog.w("Chat", "missing endpoint/model")
            return
        }
        if (settings.apiKey.isBlank()) {
            adapter.addMessage(ChatAdapter.Message("提示：当前未填写 API Key，可能会导致鉴权失败。", false))
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            AppLog.w("Chat", "missing api key")
        }

        if (conversation.isEmpty()) {
            val systemPromptBase = PromptPreferences.get(requireContext()).getChatSystemPrompt()
            val systemPrompt =
                systemPromptBase +
                    "\n\n你可以在需要时调用工具函数来操作应用内模块（脚本/日志/虚拟屏幕）。" +
                    "对可能破坏性操作（如清空日志、覆盖脚本）应先向用户确认。"
            conversation.add(OpenAiChatClient.Message(role = "system", content = systemPrompt))
        }
        conversation.add(OpenAiChatClient.Message(role = "user", content = text))
        persistAsync()

        isSending = true
        val placeholderPos = adapter.addMessage(ChatAdapter.Message("正在思考...", false))
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

        runToolLoop(settings = settings, placeholderPos = placeholderPos, round = 0)
    }

    private fun runToolLoop(
        settings: AiSettings,
        placeholderPos: Int,
        round: Int,
    ) {
        val ctx = context ?: return

        if (round >= maxToolRounds) {
            isSending = false
            adapter.updateMessage(placeholderPos, "已达到工具调用轮次上限，请把下一步需求说得更具体一些。")
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            persistAsync()
            return
        }

        inFlightCall?.cancel()
        inFlightCall =
            chatClient.chatWithTools(
                settings = settings,
                messages = conversation.toList(),
                tools = tools,
                onResult = { result ->
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread

                        result.fold(
                            onSuccess = { r ->
                                conversation.add(r.assistantMessage)
                                persistAsync()

                                val hasToolCalls = r.toolCalls.isNotEmpty()
                                val contentToShow =
                                    when {
                                        r.content.isNotBlank() -> r.content
                                        hasToolCalls -> "正在调用工具..."
                                        else -> "（无内容）"
                                    }
                                adapter.updateMessage(placeholderPos, contentToShow)
                                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

                                if (!hasToolCalls) {
                                    isSending = false
                                    AppLog.i("Chat", "request ok (no tools)")
                                    persistAsync()
                                    return@fold
                                }

                                AppLog.i("Chat", "tool_calls=${r.toolCalls.size}")

                                Thread {
                                    val toolMessages = ArrayList<OpenAiChatClient.Message>(r.toolCalls.size)
                                    val summary = StringBuilder()

                                    r.toolCalls.forEach { tc ->
                                        val toolResult =
                                            ChatToolRegistry.execute(ctx, tc.name, tc.argumentsJson)
                                                .getOrElse { e ->
                                                    JSONObject()
                                                        .put("ok", false)
                                                        .put("error", e.message ?: e.javaClass.simpleName)
                                                        .toString()
                                                }
                                        toolMessages.add(
                                            OpenAiChatClient.Message(
                                                role = "tool",
                                                content = toolResult,
                                                toolCallId = tc.id,
                                            ),
                                        )
                                        summary.append("【工具】").append(tc.name).append(" 已执行").append('\n')
                                    }

                                    activity?.runOnUiThread {
                                        if (!isAdded) return@runOnUiThread

                                        toolMessages.forEach { conversation.add(it) }
                                        persistAsync()

                                        if (summary.isNotBlank()) {
                                            adapter.addMessage(ChatAdapter.Message(summary.toString().trimEnd(), false))
                                        }
                                        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

                                        val nextPlaceholder = adapter.addMessage(ChatAdapter.Message("正在思考...", false))
                                        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                                        runToolLoop(settings, nextPlaceholder, round + 1)
                                    }
                                }.start()
                            },
                            onFailure = { e ->
                                isSending = false
                                val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                                val reply = "请求失败：$msg"
                                AppLog.e("Chat", "request failed: $reply", e)
                                adapter.updateMessage(placeholderPos, reply)
                                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                                Toast.makeText(ctx, "AI 调用失败（可检查 API Key/Endpoint）", Toast.LENGTH_SHORT).show()
                                persistAsync()
                            },
                        )
                    }
                },
            )
    }

    override fun onDestroyView() {
        inFlightCall?.cancel()
        persistAsync()
        super.onDestroyView()
    }

    companion object {
        const val ARG_SESSION_ID = "session_id"
    }
}

