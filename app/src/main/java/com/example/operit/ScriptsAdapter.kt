package com.example.operit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.scripts.ScriptStore

class ScriptsAdapter(
    private val onItemClick: (ScriptStore.ScriptMeta) -> Unit,
) : RecyclerView.Adapter<ScriptsAdapter.ScriptViewHolder>() {

    data class ScriptRow(
        val meta: ScriptStore.ScriptMeta,
        val sizeText: String,
    )

    private val scripts = mutableListOf<ScriptRow>()

    fun submit(items: List<ScriptRow>) {
        scripts.clear()
        scripts.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return ScriptViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScriptViewHolder, position: Int) {
        holder.bind(scripts[position], onItemClick)
    }

    override fun getItemCount() = scripts.size

    class ScriptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvScriptName)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvScriptDesc)

        fun bind(row: ScriptRow, onClick: (ScriptStore.ScriptMeta) -> Unit) {
            tvName.text = row.meta.name
            tvDesc.text = "${row.meta.desc} · ${row.sizeText}"
            itemView.setOnClickListener { onClick(row.meta) }
        }
    }
}

