package com.example.operit.chat.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.MainActivity
import com.example.operit.R
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.AiSettings
import com.example.operit.ai.OpenAiChatClient
import com.example.operit.autoglm.runtime.AutoGlmSessionManager
import com.example.operit.chat.ChatStore
import com.example.operit.logging.AppLog
import com.example.operit.prompts.PromptPreferences
import com.example.operit.toolsystem.ChatToolRegistry
import com.example.operit.tools.ui.ToolsFragment
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import org.json.JSONObject

class ChatFragment : Fragment() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var input: EditText
    private lateinit var btnSend: MaterialButton

    private var inFlightCall: Call? = null
    private var isSending = false
    private var currentPlaceholderPos: Int? = null
    @Volatile private var runningAutoGlmSessionId: String? = null

    private val chatClient = OpenAiChatClient()
    private val conversation = mutableListOf<OpenAiChatClient.Message>()
    private val tools by lazy { ChatToolRegistry.defaultTools() }

    private val maxToolRounds = 8
    private var sessionId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.chatRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)
        input = view.findViewById(R.id.chatInput)
        btnSend = view.findViewById(R.id.btnSend)
        val btnTools = view.findViewById<View>(R.id.btnTools)
        val btnAttach = view.findViewById<View>(R.id.btnAttach)

        adapter = ChatAdapter()
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
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

        btnSend.setOnClickListener { onSendClicked() }
        input.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
                onSendClicked()
                true
            } else {
                false
            }
        }

        recyclerView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                input.clearFocus()
                hideKeyboard()
            }
            false
        }

        updateSendingUi()
        ensureSessionLoaded()
    }

    private fun onSendClicked() {
        val ctx = context ?: return
        if (isSending) {
            inFlightCall?.cancel()
            isSending = false
            runningAutoGlmSessionId?.let { sid ->
                runCatching { AutoGlmSessionManager.cancel(sid) }
                runningAutoGlmSessionId = null
            }
            currentPlaceholderPos?.let { adapter.updateMessage(it, "已取消") }
            Toast.makeText(ctx, "已取消", Toast.LENGTH_SHORT).show()
            updateSendingUi()
            return
        }

        val text = input.text.toString()
        if (text.isNotBlank()) {
            sendMessage(text)
            input.text.clear()
            hideKeyboard()
        }
    }

    private fun updateSendingUi() {
        if (!::btnSend.isInitialized) return
        btnSend.isEnabled = true
        btnSend.setIconResource(if (isSending) R.drawable.ic_stop else R.drawable.ic_arrow_upward)
        btnSend.contentDescription = if (isSending) "取消" else "发送"
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun ensureSessionLoaded() {
        if (sessionId != null) return

        val ctx = context ?: return
        val store = ChatStore.get(ctx)
        val idFromArgs = arguments?.getString(ARG_SESSION_ID).orEmpty().trim()
        val id = if (idFromArgs.isNotBlank()) idFromArgs else store.createSession().id
        sessionId = id

        val saved = store.loadMessages(id)
        conversation.clear()
        conversation.addAll(saved)

        // system/tool 不直接展示在对话列表里
        saved.forEach { m ->
            when (m.role) {
                "user" -> adapter.addMessage(ChatAdapter.Message(m.content.orEmpty(), true))
                "assistant" -> adapter.addMessage(ChatAdapter.Message(m.content.orEmpty(), false))
            }
        }
        recyclerView.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
        emptyState.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun persistAsync() {
        val ctx = context ?: return
        val id = sessionId ?: return
        val messagesSnapshot = conversation.toList()
        Thread {
            runCatching {
                ChatStore.get(ctx).saveMessages(id, messagesSnapshot)
            }
            activity?.runOnUiThread {
                (activity as? MainActivity)?.refreshHistory()
            }
        }.start()
    }

    private fun sendMessage(text: String) {
        if (text.isBlank()) return

        if (adapter.itemCount == 0) {
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
                    "\n\n你可以在需要时调用工具函数来操作应用内模块（日志/虚拟屏幕等）。" +
                    "对明显破坏性操作（如清空日志）再向用户确认即可。" +
                    "\n\n你也可以调用 AutoGLM 工具执行跨应用的手机自动化任务：" +
                    "先调用 autoglm_run(task=...) 获得 session_id；再用 autoglm_status(session_id=...) 轮询进度；" +
                    "需要停止则调用 autoglm_cancel(session_id=...)." +
                    "\n\n重要：当用户已经明确要求执行某个自动化任务时，默认视为已授权一般操作；" +
                    "不要在每一步反复询问“是否允许”。仅当涉及支付/转账/删除/隐私授权等不可逆或敏感操作时，再进行一次确认。\n\n" +
                    "触发 AutoGLM 的规则：当用户提出跨应用的手机操作需求（打开 App、搜索、点击、滑动、下单等）时，直接调用 autoglm_run(task=...)，" +
                    "把用户意图改写为清晰、可执行的任务描述传入 task，不要先征求许可。"
            conversation.add(OpenAiChatClient.Message(role = "system", content = systemPrompt))
        }

        conversation.add(OpenAiChatClient.Message(role = "user", content = text))
        persistAsync()

        isSending = true
        updateSendingUi()
        currentPlaceholderPos = adapter.addMessage(ChatAdapter.Message("正在思考…", false))
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

        runToolLoop(settings = settings, placeholderPos = currentPlaceholderPos ?: -1, round = 0)
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
            updateSendingUi()
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
                                        hasToolCalls -> "正在调用工具…"
                                        else -> "（无内容）"
                                    }
                                adapter.updateMessage(placeholderPos, contentToShow)
                                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

                                if (!hasToolCalls) {
                                    isSending = false
                                    AppLog.i("Chat", "request ok (no tools)")
                                    persistAsync()
                                    updateSendingUi()
                                    return@fold
                                }

                                AppLog.i("Chat", "tool_calls=${r.toolCalls.size}")

                                Thread {
                                    val chatSessionId = sessionId.orEmpty()
                                    val toolMessages = ArrayList<OpenAiChatClient.Message>(r.toolCalls.size)
                                    val summary = StringBuilder()
                                    var shouldAddVirtualScreenCard = false

                                    r.toolCalls.forEach { tc ->
                                        val injectedArgsJson =
                                            if (tc.name == "autoglm_run" && chatSessionId.isNotBlank()) {
                                                runCatching {
                                                    val obj = JSONObject(tc.argumentsJson)
                                                    obj.put("chat_session_id", chatSessionId)
                                                    obj.toString()
                                                }.getOrElse { tc.argumentsJson }
                                            } else {
                                                tc.argumentsJson
                                            }

                                        // autoglm_run：阻塞等待直到 AutoGLM 完成（SUCCESS/FAILED/CANCELLED），再把最终结果回传给主对话模型。
                                        val toolResult =
                                            if (tc.name == "autoglm_run") {
                                                activity?.runOnUiThread {
                                                    adapter.updateMessage(
                                                        placeholderPos,
                                                        "AutoGLM 执行中…（完成后会自动返回结果；可点右侧停止取消）",
                                                    )
                                                }
                                                executeAutoGlmRunAndWait(ctx, injectedArgsJson).also {
                                                    runningAutoGlmSessionId = null
                                                }
                                            } else {
                                                ChatToolRegistry.execute(ctx, tc.name, injectedArgsJson)
                                                    .getOrElse { e ->
                                                        JSONObject()
                                                            .put("ok", false)
                                                            .put("error", e.message ?: e.javaClass.simpleName)
                                                            .toString()
                                                    }
                                            }

                                        toolMessages.add(
                                            OpenAiChatClient.Message(
                                                role = "tool",
                                                content = toolResult,
                                                toolCallId = tc.id,
                                            ),
                                        )
                                        summary.append("【工具】").append(tc.name).append(" 已执行").append('\n')

                                        if (tc.name == "autoglm_run") {
                                            val ok = runCatching { JSONObject(toolResult).optBoolean("ok", false) }.getOrDefault(false)
                                            if (ok) shouldAddVirtualScreenCard = true
                                        }
                                    }

                                    activity?.runOnUiThread {
                                        if (!isAdded) return@runOnUiThread

                                        toolMessages.forEach { conversation.add(it) }
                                        persistAsync()

                                        if (summary.isNotBlank()) {
                                            adapter.addMessage(ChatAdapter.Message(summary.toString().trimEnd(), false))
                                        }

                                        if (shouldAddVirtualScreenCard) {
                                            adapter.addCard(
                                                title = "虚拟屏幕（AutoGLM）",
                                                subtitle = "点击查看 AutoGLM 当前正在操作的页面",
                                                action = ChatAdapter.CardAction.OPEN_SHOWER_VIEWER,
                                                chatSessionId = chatSessionId,
                                            )
                                        }

                                        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

                                        val nextPlaceholder = adapter.addMessage(ChatAdapter.Message("正在思考…", false))
                                        currentPlaceholderPos = nextPlaceholder
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
                                updateSendingUi()
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

    private fun executeAutoGlmRunAndWait(context: android.content.Context, argsJson: String): String {
        val startRaw =
            ChatToolRegistry.execute(context, "autoglm_run", argsJson)
                .getOrElse { e ->
                    return JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName).toString()
                }

        val startObj = runCatching { JSONObject(startRaw) }.getOrNull() ?: return startRaw
        val ok = startObj.optBoolean("ok", false)
        if (!ok) return startObj.toString()

        val sid = startObj.optString("session_id").trim()
        if (sid.isBlank()) return startObj.put("ok", false).put("error", "autoglm_run 未返回 session_id").toString()

        runningAutoGlmSessionId = sid

        val pollMs = startObj.optInt("recommended_poll_ms", 1200).coerceIn(400, 3000)
        val maxWaitMs = 30L * 60L * 1000L // 30 分钟兜底，避免无限等待
        val startedAt = System.currentTimeMillis()

        var lastStatus: JSONObject? = null
        while (true) {
            val now = System.currentTimeMillis()
            if (now - startedAt > maxWaitMs) {
                return JSONObject()
                    .put("ok", true)
                    .put("note", "等待 AutoGLM 超时（${maxWaitMs / 60000} 分钟），请稍后在虚拟屏幕查看或再次询问。")
                    .put("start", startObj)
                    .put("last_status", lastStatus ?: JSONObject.NULL)
                    .toString()
            }

            // 若用户在 UI 点了“停止”，onSendClicked 会触发 autoglm_cancel 并清空 runningAutoGlmSessionId
            if (runningAutoGlmSessionId == null) {
                return JSONObject()
                    .put("ok", true)
                    .put("note", "用户已取消 AutoGLM")
                    .put("start", startObj)
                    .put("last_status", lastStatus ?: JSONObject.NULL)
                    .toString()
            }

            val statusObj =
                runCatching { AutoGlmSessionManager.status(sessionId = sid, maxChars = 12000) }
                    .getOrElse { e -> JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName) }
            lastStatus = statusObj

            val status = statusObj.optString("status").trim()
            if (status.isNotBlank() && status != "RUNNING") {
                val log = statusObj.optString("log")
                val finalMessage = extractAutoGlmFinalMessage(log)
                return JSONObject()
                    .put("ok", true)
                    .put("start", startObj)
                    .put("final_status", statusObj)
                    .put("final_message", finalMessage)
                    .toString()
            }

            Thread.sleep(pollMs.toLong())
        }
    }

    private fun extractAutoGlmFinalMessage(log: String): String {
        if (log.isBlank()) return ""
        val lines = log.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val keys = listOf("完成：", "中断：", "失败：", "已取消：")
        for (i in lines.size - 1 downTo 0) {
            val line = lines[i]
            if (keys.any { line.startsWith(it) }) return line
        }
        return lines.lastOrNull().orEmpty()
    }
}
