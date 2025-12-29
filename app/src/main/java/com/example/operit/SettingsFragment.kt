package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.settingsList)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val items = listOf(
            SettingsAdapter.SettingItem("language", getString(R.string.settings_language), getString(R.string.settings_language_sub), R.drawable.ic_language),
            SettingsAdapter.SettingItem("ai", getString(R.string.settings_ai), getString(R.string.settings_ai_sub), R.drawable.ic_settings_fill), // Using settings icon as psychology
            SettingsAdapter.SettingItem("features", getString(R.string.settings_features), getString(R.string.settings_features_sub), R.drawable.ic_extension),
            SettingsAdapter.SettingItem("prompts", getString(R.string.settings_prompts), getString(R.string.settings_prompts_sub), R.drawable.ic_edit_note),
            SettingsAdapter.SettingItem("permissions", getString(R.string.settings_permissions), getString(R.string.settings_permissions_sub), R.drawable.ic_security)
        )

        recyclerView.adapter = SettingsAdapter(items) { item ->
            if (item.id == "ai") {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, SettingsAiFragment())
                    .addToBackStack(null)
                    .commit()
            } else {
                Toast.makeText(context, "${item.title} clicked", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
