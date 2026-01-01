package com.example.operit.tools.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.R

class ToolsAdapter(private val onItemClick: (String) -> Unit) :
    RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    data class Tool(val id: String, val title: String, val desc: String, val iconRes: Int)

    private val tools =
        listOf(
            Tool("autoglm_test", "AutoGLM 连接测试", "截图 + 调用 autoglm-phone，快速定位 API/权限问题。", R.drawable.ic_build),
            Tool("config", "一键配置", "快速设置 AutoGLM 环境参数。", R.drawable.ic_tune),
            Tool("virtual_screen", "虚拟屏幕", "创建虚拟显示并截图预览（实验）。", R.drawable.ic_grid),
            Tool("process", "解除进程限制", "移除 Android 12+ 幻象进程杀手。", R.drawable.ic_no_encryption),
            Tool("web2apk", "网页转 APK", "将任意网页封装为独立应用。", R.drawable.ic_android),
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

        fun bind(tool: Tool, onClick: (String) -> Unit) {
            tvTitle.text = tool.title
            tvDesc.text = tool.desc
            ivIcon.setImageResource(tool.iconRes)
            itemView.setOnClickListener { onClick(tool.id) }

            val iconContainer = itemView.findViewById<View>(R.id.iconContainer)
            iconContainer.background.setTint(itemView.context.getColor(R.color.md_theme_light_surfaceVariant))
            ivIcon.setColorFilter(itemView.context.getColor(R.color.md_theme_light_onSurfaceVariant))
        }
    }
}
