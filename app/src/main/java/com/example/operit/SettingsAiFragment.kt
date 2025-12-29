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

    private lateinit var tvProfileHint: TextView
    private lateinit var actProvider: MaterialAutoCompleteTextView
    private lateinit var etEndpoint: TextInputEditText
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etModel: TextInputEditText
    private lateinit var sliderTemp: Slider
    private lateinit var sliderTopP: Slider
    private lateinit var sliderMaxTokens: Slider
    private lateinit var tvTempValue: TextView
    private lateinit var tvTopPValue: TextView
    private lateinit var tvMaxTokensValue: TextView

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

        tvProfileHint = view.findViewById(R.id.tvProfileHint)
        actProvider = view.findViewById(R.id.actProvider)
        etEndpoint = view.findViewById(R.id.etEndpoint)
        etApiKey = view.findViewById(R.id.etApiKey)
        etModel = view.findViewById(R.id.etModel)

        setupProviderDropdown()
        bindSliders(view)

        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.profileToggle)
        toggle.check(R.id.btnProfileChat)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val profile =
                when (checkedId) {
                    R.id.btnProfileUiController -> AiPreferences.PROFILE_UI_CONTROLLER
                    else -> AiPreferences.PROFILE_CHAT
                }
            loadProfile(profile)
        }

        loadProfile(AiPreferences.PROFILE_CHAT)
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

    private fun bindSliders(root: View) {
        val tempContainer = root.findViewById<View>(R.id.sliderTemp)
        val topPContainer = root.findViewById<View>(R.id.sliderTopP)
        val maxTokensContainer = root.findViewById<View>(R.id.sliderMaxTokens)

        tempContainer.findViewById<TextView>(R.id.tvLabel).text = "Temperature"
        topPContainer.findViewById<TextView>(R.id.tvLabel).text = "Top P"
        maxTokensContainer.findViewById<TextView>(R.id.tvLabel).text = "Max Tokens"

        tvTempValue = tempContainer.findViewById(R.id.tvValue)
        tvTopPValue = topPContainer.findViewById(R.id.tvValue)
        tvMaxTokensValue = maxTokensContainer.findViewById(R.id.tvValue)

        sliderTemp = tempContainer.findViewById(R.id.slider)
        sliderTopP = topPContainer.findViewById(R.id.slider)
        sliderMaxTokens = maxTokensContainer.findViewById(R.id.slider)

        sliderTemp.valueFrom = 0.0f
        sliderTemp.valueTo = 2.0f
        sliderTemp.stepSize = 0.1f
        sliderTemp.addOnChangeListener { _, value, _ -> tvTempValue.text = String.format("%.1f", value) }

        sliderTopP.valueFrom = 0.0f
        sliderTopP.valueTo = 1.0f
        sliderTopP.stepSize = 0.05f
        sliderTopP.addOnChangeListener { _, value, _ -> tvTopPValue.text = String.format("%.2f", value) }

        sliderMaxTokens.valueFrom = 256f
        sliderMaxTokens.valueTo = 32000f
        sliderMaxTokens.stepSize = 256f
        sliderMaxTokens.addOnChangeListener { _, value, _ -> tvMaxTokensValue.text = value.toInt().toString() }
    }

    private fun loadProfile(profile: String) {
        currentProfile = profile
        val settings = prefs.load(profile)
        selectedProvider = settings.provider

        actProvider.setText(settings.provider.displayName, false)
        etEndpoint.setText(settings.endpoint)
        etApiKey.setText(settings.apiKey)
        etModel.setText(settings.model)

        tvProfileHint.text =
            if (profile == AiPreferences.PROFILE_UI_CONTROLLER) {
                "AutoGLM 使用独立模型配置（UI 控制器），不影响主对话。推荐在工具箱 -> AutoGLM 一键配置中只填智谱 API Key。"
            } else {
                "主对话模型用于 Chat。你也可以在这里切换 AutoGLM 的独立配置。"
            }

        sliderTemp.value = settings.temperature.coerceIn(sliderTemp.valueFrom, sliderTemp.valueTo)
        tvTempValue.text = String.format("%.1f", sliderTemp.value)

        sliderTopP.value = settings.topP.coerceIn(sliderTopP.valueFrom, sliderTopP.valueTo)
        tvTopPValue.text = String.format("%.2f", sliderTopP.value)

        sliderMaxTokens.value = settings.maxTokens.toFloat().coerceIn(sliderMaxTokens.valueFrom, sliderMaxTokens.valueTo)
        tvMaxTokensValue.text = sliderMaxTokens.value.toInt().toString()
    }

    private fun save() {
        val root = view ?: return

        val endpoint = etEndpoint.text?.toString().orEmpty().trim()
        val apiKey = etApiKey.text?.toString().orEmpty().trim()
        val model = etModel.text?.toString().orEmpty().trim()

        val temperature = sliderTemp.value
        val topP = sliderTopP.value
        val maxTokens = sliderMaxTokens.value.toInt()

        val newSettings =
            AiSettings(
                provider = selectedProvider,
                endpoint = endpoint,
                apiKey = apiKey,
                model = model,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
            )
        prefs.save(currentProfile, newSettings)

        if (currentProfile == AiPreferences.PROFILE_CHAT) {
            (activity as? MainActivity)?.refreshHeaderModelName()
        }

        Toast.makeText(requireContext(), "已保存 AI 配置", Toast.LENGTH_SHORT).show()
    }
}
