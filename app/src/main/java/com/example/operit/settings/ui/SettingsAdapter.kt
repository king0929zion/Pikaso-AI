package com.example.operit.settings.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.R
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsAdapter(
    private val items: List<SettingItem>,
    private val onItemClick: (SettingItem) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    data class SettingItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val hasSwitch: Boolean = false,
        var isSwitchChecked: Boolean = false
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_settings, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount() = items.size

    class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val ivArrow: ImageView = itemView.findViewById(R.id.ivArrow)
        private val switchWidget: SwitchMaterial = itemView.findViewById(R.id.switchWidget)

        fun bind(item: SettingItem, onClick: (SettingItem) -> Unit) {
            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle
            ivIcon.setImageResource(item.iconRes)
            
            if (item.hasSwitch) {
                ivArrow.visibility = View.GONE
                switchWidget.visibility = View.VISIBLE
                switchWidget.isChecked = item.isSwitchChecked
                switchWidget.setOnCheckedChangeListener { _, isChecked ->
                    item.isSwitchChecked = isChecked
                }
                // Toggle switch when clicking the item row
                itemView.setOnClickListener { switchWidget.toggle() }
            } else {
                ivArrow.visibility = View.VISIBLE
                switchWidget.visibility = View.GONE
                itemView.setOnClickListener { onClick(item) }
            }
        }
    }
}
