package com.example.operit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    data class HistoryItem(val date: String, val preview: String)

    private val items = listOf(
        HistoryItem("今天 10:23", "帮我打开 Bilibili 并搜索..."),
        HistoryItem("昨天 15:45", "如何配置 AutoGLM 环境？"),
        HistoryItem("12月27日", "测试无障碍服务权限")
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvPreview)

        fun bind(item: HistoryItem) {
            tvDate.text = item.date
            tvPreview.text = item.preview
        }
    }
}
