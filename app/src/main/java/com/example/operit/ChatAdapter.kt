package com.example.operit

import android.content.ClipData
import android.content.ClipboardManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.markdown.MarkdownRenderer
import com.google.android.material.snackbar.Snackbar

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()

    data class Message(val text: String, val isUser: Boolean)

    fun addMessage(message: Message): Int {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
        return messages.size - 1
    }

    fun updateMessage(position: Int, newText: String) {
        if (position < 0 || position >= messages.size) return
        messages[position] = messages[position].copy(text = newText)
        notifyItemChanged(position)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == 1) R.layout.item_chat_user else R.layout.item_chat_ai
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as MessageViewHolder).bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        fun bind(message: Message) {
            if (message.isUser) {
                tvMessage.text = message.text
            } else {
                MarkdownRenderer.render(tvMessage, message.text)
            }

            itemView.setOnLongClickListener {
                val text = message.text.trim()
                if (text.isNotEmpty()) {
                    val clipboard = it.context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("message", text))
                    Snackbar.make(it, "已复制到剪贴板", Snackbar.LENGTH_SHORT).show()
                }
                true
            }
        }
    }
}
