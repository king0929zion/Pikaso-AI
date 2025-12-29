package com.example.operit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ToolsAdapter(private val onItemClick: (String) -> Unit) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    data class Tool(val id: String, val title: String, val desc: String, val iconRes: Int)

    private val tools = listOf(
        Tool("autoglm", "AutoGLM 执行器", "自动化执行复杂任务，支持多模态交互。", R.drawable.ic_smart_toy),
        Tool("config", "一键配置", "快速设置 AutoGLM 环境参数。", R.drawable.ic_tune),
        Tool("process", "解除进程限制", "移除 Android 12+ 幻象进程杀手。", R.drawable.ic_no_encryption),
        Tool("web2apk", "网页转 APK", "将任意网页封装为独立应用。", R.drawable.ic_android)
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
        return ToolViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position], onItemClick)
    }

    override fun getItemCount() = tools.size

    class ToolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvDesc)
        private val container: View = itemView.findViewById<View>(R.id.iconContainer).parent.parent as View // CardView

        fun bind(tool: Tool, onClick: (String) -> Unit) {
            tvTitle.text = tool.title
            tvDesc.text = tool.desc
            ivIcon.setImageResource(tool.iconRes)
            itemView.setOnClickListener { onClick(tool.id) }
            
            // Special styling for primary (first item)
            if (tool.id == "autoglm") {
                val iconContainer = itemView.findViewById<View>(R.id.iconContainer)
                iconContainer.background.setTint(itemView.context.getColor(R.color.md_theme_light_secondaryContainer))
                ivIcon.setColorFilter(itemView.context.getColor(R.color.md_theme_light_onSecondaryContainer))
            } else {
                 val iconContainer = itemView.findViewById<View>(R.id.iconContainer)
                iconContainer.background.setTint(itemView.context.getColor(R.color.md_theme_light_surfaceVariant))
                ivIcon.setColorFilter(itemView.context.getColor(R.color.md_theme_light_onSurfaceVariant))
            }
        }
    }
}
