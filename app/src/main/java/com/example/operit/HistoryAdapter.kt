package com.example.operit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.chat.ChatStore

class HistoryAdapter(
    private var items: List<ChatStore.SessionMeta>,
    private val formatTime: (Long) -> String,
    private val onClick: (ChatStore.SessionMeta) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    fun submitList(newItems: List<ChatStore.SessionMeta>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view, formatTime, onClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class HistoryViewHolder(
        itemView: View,
        private val formatTime: (Long) -> String,
        private val onClick: (ChatStore.SessionMeta) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvPreview)

        fun bind(item: ChatStore.SessionMeta) {
            tvDate.text = formatTime(item.updatedAt)
            tvPreview.text = item.preview.ifBlank { "新对话" }
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
