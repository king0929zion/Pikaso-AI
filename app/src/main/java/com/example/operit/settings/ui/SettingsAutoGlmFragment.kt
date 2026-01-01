package com.example.operit.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.AiProvider
import com.example.operit.ai.AiSettings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

class SettingsAutoGlmFragment : Fragment() {

    private lateinit var etApiKey: TextInputEditText
    private lateinit var tvMaxStepsValue: android.widget.TextView
    private lateinit var sliderMaxSteps: Slider
    private lateinit var prefs: AiPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_autoglm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = AiPreferences.get(requireContext())
        
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        etApiKey = view.findViewById(R.id.etApiKey)
        tvMaxStepsValue = view.findViewById(R.id.tvMaxStepsValue)
        sliderMaxSteps = view.findViewById(R.id.sliderMaxSteps)

        // Load existing key if any
        val currentSettings = prefs.load(AiPreferences.PROFILE_UI_CONTROLLER)
        if (currentSettings.provider == AiProvider.ZHIPU) {
            etApiKey.setText(currentSettings.apiKey)
        }

        val currentMaxSteps = prefs.loadUiControllerMaxSteps()
        sliderMaxSteps.value = currentMaxSteps.toFloat().coerceIn(1f, 100f)
        tvMaxStepsValue.text = sliderMaxSteps.value.toInt().coerceIn(1, 100).toString()
        sliderMaxSteps.addOnChangeListener { _, value, _ ->
            tvMaxStepsValue.text = value.toInt().coerceIn(1, 100).toString()
        }

        view.findViewById<MaterialButton>(R.id.btnGetApiKey).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.bigmodel.cn/usercenter/apikeys"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            save()
        }
    }

    private fun save() {
        val apiKeyInput = etApiKey.text?.toString().orEmpty().trim()
        val existing = prefs.load(AiPreferences.PROFILE_UI_CONTROLLER)
        val apiKey = if (apiKeyInput.isNotBlank()) apiKeyInput else existing.apiKey
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val maxSteps = sliderMaxSteps.value.toInt().coerceIn(1, 100)
        prefs.saveUiControllerMaxSteps(maxSteps)

        // Auto confirm default settings for AutoGLM
        val settings = AiSettings(
            provider = AiProvider.ZHIPU,
            endpoint = AiProvider.ZHIPU.defaultEndpoint,
            apiKey = apiKey,
            model = "autoglm-phone",
            temperature = 0.0f,
            topP = 0.85f,
            maxTokens = 4096,
        )
        
        prefs.save(AiPreferences.PROFILE_UI_CONTROLLER, settings)
        Toast.makeText(requireContext(), "AutoGLM 配置已更新（默认步数：$maxSteps）", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }
}
