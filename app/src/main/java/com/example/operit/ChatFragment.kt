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
import com.example.operit.ai.OpenAiChatClient
import com.google.android.material.button.MaterialButton
import okhttp3.Call

class ChatFragment : Fragment() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private var inFlightCall: Call? = null
    private var isSending = false
    private val chatClient = OpenAiChatClient()
    private val conversation = mutableListOf<OpenAiChatClient.Message>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.chatRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)
        val input = view.findViewById<EditText>(R.id.chatInput)
        val btnSend = view.findViewById<MaterialButton>(R.id.btnSend)

        adapter = ChatAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        btnSend.setOnClickListener {
            val text = input.text.toString()
            if (text.isNotBlank()) {
                sendMessage(text)
                input.text.clear()
            }
        }
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
            return
        }
        if (settings.apiKey.isBlank()) {
            adapter.addMessage(ChatAdapter.Message("提示：当前未填写 API Key，可能会导致鉴权失败。", false))
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }

        if (conversation.isEmpty()) {
            conversation.add(
                OpenAiChatClient.Message(
                    role = "system",
                    content = "你是 Pikaso，一个可靠、简洁的中文 AI 助手。优先给出可执行的步骤与结论。",
                ),
            )
        }
        conversation.add(OpenAiChatClient.Message(role = "user", content = text))

        isSending = true
        adapter.addMessage(ChatAdapter.Message("正在思考...", false))
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)

        inFlightCall?.cancel()
        inFlightCall =
            chatClient.chat(
                settings = settings,
                messages = conversation.toList(),
                onResult = { result ->
                    activity?.runOnUiThread {
                        isSending = false
                        val reply = result.getOrElse { e ->
                            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                            "请求失败：$msg"
                        }
                        conversation.add(OpenAiChatClient.Message(role = "assistant", content = reply))
                        adapter.addMessage(ChatAdapter.Message(reply, false))
                        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                        if (result.isFailure) {
                            Toast.makeText(ctx, "AI 调用失败（可检查 API Key/Endpoint）", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
    }

    override fun onDestroyView() {
        inFlightCall?.cancel()
        super.onDestroyView()
    }
}
