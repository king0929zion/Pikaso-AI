package com.example.operit.chat.ui

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.R
import com.example.operit.markdown.MarkdownRenderer
import com.example.operit.virtualdisplay.shower.ShowerViewerActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Item {
        data class Text(val text: String, val isUser: Boolean) : Item

        data class Card(
            val title: String,
            val subtitle: String,
            val action: CardAction,
            val chatSessionId: String? = null,
        ) : Item
    }

    enum class CardAction {
        OPEN_SHOWER_VIEWER,
    }

    data class Message(val text: String, val isUser: Boolean)

    private val items = mutableListOf<Item>()

    fun addMessage(message: Message): Int {
        items.add(Item.Text(message.text, message.isUser))
        notifyItemInserted(items.size - 1)
        return items.size - 1
    }

    fun addCard(title: String, subtitle: String, action: CardAction, chatSessionId: String? = null): Int {
        items.add(Item.Card(title = title, subtitle = subtitle, action = action, chatSessionId = chatSessionId))
        notifyItemInserted(items.size - 1)
        return items.size - 1
    }

    fun updateMessage(position: Int, newText: String) {
        if (position < 0 || position >= items.size) return
        val old = items[position]
        if (old !is Item.Text) return
        items[position] = old.copy(text = newText)
        notifyItemChanged(position)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Item.Text -> if ((items[position] as Item.Text).isUser) 1 else 0
            is Item.Card -> 2
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> MessageViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
            1 -> MessageViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
            else -> CardViewHolder(inflater.inflate(R.layout.item_chat_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Text -> (holder as MessageViewHolder).bind(item)
            is Item.Card -> (holder as CardViewHolder).bind(item)
        }
    }

    override fun getItemCount() = items.size

    private class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)

        fun bind(message: Item.Text) {
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

    private class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val btnPrimary: MaterialButton = itemView.findViewById(R.id.btnPrimary)

        fun bind(card: Item.Card) {
            tvTitle.text = card.title
            tvSubtitle.text = card.subtitle

            val click = View.OnClickListener { v ->
                val ctx = v.context
                performAction(ctx, card)
            }
            itemView.setOnClickListener(click)
            btnPrimary.setOnClickListener(click)
        }

        private fun performAction(context: Context, card: Item.Card) {
            when (card.action) {
                CardAction.OPEN_SHOWER_VIEWER -> {
                    val intent = Intent(context, ShowerViewerActivity::class.java)
                    card.chatSessionId?.takeIf { it.isNotBlank() }?.let { intent.putExtra(ShowerViewerActivity.EXTRA_CHAT_SESSION_ID, it) }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }
}
