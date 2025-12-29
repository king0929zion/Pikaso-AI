package com.example.operit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScriptsAdapter(private val onItemClick: (Script) -> Unit) : RecyclerView.Adapter<ScriptsAdapter.ScriptViewHolder>() {

    data class Script(val name: String, val desc: String, val size: String)

    private val scripts = listOf(
        Script("12306.js", "自动抢票脚本", "2.4KB"),
        Script("bilibili_tools.ts", "B站视频下载与签到", "5.1KB"),
        Script("douyin_download.js", "抖音无水印下载", "3.2KB"),
        Script("jd_auto_buy.js", "京东自动下单", "4.0KB"),
        Script("wechat_reply.ts", "微信自动回复助手", "6.8KB")
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return ScriptViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScriptViewHolder, position: Int) {
        val script = scripts[position]
        holder.bind(script, onItemClick)
    }

    override fun getItemCount() = scripts.size

    class ScriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvScriptName)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvScriptDesc)

        fun bind(script: Script, onClick: (Script) -> Unit) {
            tvName.text = script.name
            tvDesc.text = "${script.desc} • ${script.size}"
            itemView.setOnClickListener { onClick(script) }
        }
    }
}
