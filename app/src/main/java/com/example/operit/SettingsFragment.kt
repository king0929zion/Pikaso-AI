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
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.settingsList)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val items = listOf(
            SettingsAdapter.SettingItem("language", getString(R.string.settings_language), getString(R.string.settings_language_sub), R.drawable.ic_language),
            SettingsAdapter.SettingItem("ai", getString(R.string.settings_ai), getString(R.string.settings_ai_sub), R.drawable.ic_settings_fill),
            SettingsAdapter.SettingItem("autoglm", "AutoGLM 配置", "配置智能体控制模型 (需智谱 API)", R.drawable.ic_robot_2), // Using robot icon if available, or extension
            SettingsAdapter.SettingItem("features", getString(R.string.settings_features), getString(R.string.settings_features_sub), R.drawable.ic_extension),
            SettingsAdapter.SettingItem("prompts", getString(R.string.settings_prompts), getString(R.string.settings_prompts_sub), R.drawable.ic_edit_note),
            SettingsAdapter.SettingItem("permissions", getString(R.string.settings_permissions), getString(R.string.settings_permissions_sub), R.drawable.ic_security)
        )

        recyclerView.adapter = SettingsAdapter(items) { item ->
            val fragment = when (item.id) {
                "language" -> SettingsLanguageFragment()
                "ai" -> SettingsAiFragment()
                "autoglm" -> SettingsAutoGlmFragment()
                "features" -> SettingsFeaturesFragment()
                "prompts" -> SettingsPromptsFragment()
                "permissions" -> SettingsPermissionsFragment()
                else -> null
            }

            if (fragment != null) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            } else {
                Toast.makeText(context, "${item.title} clicked", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
