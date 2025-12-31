package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.AiProvider
import com.example.operit.ai.AiSettings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class SettingsAiFragment : Fragment() {

    private var selectedProvider: AiProvider = AiProvider.ZHIPU
    private var currentProfile: String = AiPreferences.PROFILE_CHAT

    private lateinit var prefs: AiPreferences

    private lateinit var actProvider: MaterialAutoCompleteTextView
    private lateinit var etEndpoint: TextInputEditText
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etModel: TextInputEditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = AiPreferences.get(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_save) {
                save()
                true
            } else {
                false
            }
        }

        actProvider = view.findViewById(R.id.actProvider)
        etEndpoint = view.findViewById(R.id.etEndpoint)
        etApiKey = view.findViewById(R.id.etApiKey)
        etModel = view.findViewById(R.id.etModel)

        setupProviderDropdown()
        loadProfile()
    }

    private fun setupProviderDropdown() {
        val providers = AiProvider.entries
        val items = providers.map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        actProvider.setAdapter(adapter)

        actProvider.setOnItemClickListener { _, _, position, _ ->
            val newProvider = providers.getOrNull(position) ?: return@setOnItemClickListener
            val oldProvider = selectedProvider
            selectedProvider = newProvider

            if (newProvider != AiProvider.CUSTOM) {
                val currentEndpoint = etEndpoint.text?.toString().orEmpty().trim()
                if (currentEndpoint.isBlank() || currentEndpoint == oldProvider.defaultEndpoint) {
                    etEndpoint.setText(newProvider.defaultEndpoint)
                }

                val currentModel = etModel.text?.toString().orEmpty().trim()
                if (currentModel.isBlank() || currentModel == oldProvider.defaultModel) {
                    etModel.setText(newProvider.defaultModel)
                }
            }
        }
    }

    private fun loadProfile() {
        // Always load CHAT profile
        val settings = prefs.load(AiPreferences.PROFILE_CHAT)
        selectedProvider = settings.provider

        actProvider.setText(settings.provider.displayName, false)
        etEndpoint.setText(settings.endpoint)
        etApiKey.setText(settings.apiKey)
        etModel.setText(settings.model)
    }

    private fun save() {
        val endpoint = etEndpoint.text?.toString().orEmpty().trim()
        val apiKey = etApiKey.text?.toString().orEmpty().trim()
        val model = etModel.text?.toString().orEmpty().trim()

        // Preserve existing params since UI doesn't allow editing them anymore
        val currentSettings = prefs.load(AiPreferences.PROFILE_CHAT)

        val newSettings =
            currentSettings.copy(
                provider = selectedProvider,
                endpoint = endpoint,
                apiKey = apiKey,
                model = model
            )
        
        prefs.save(AiPreferences.PROFILE_CHAT, newSettings)

        (activity as? MainActivity)?.refreshHeaderModelName()

        Toast.makeText(requireContext(), "已保存配置", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }
}
