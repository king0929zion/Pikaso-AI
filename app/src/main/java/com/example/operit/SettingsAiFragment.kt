package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.AiProvider
import com.example.operit.ai.AiSettings
import com.google.android.material.slider.Slider

class SettingsAiFragment : Fragment() {

    private var selectedProvider: AiProvider = AiProvider.ZHIPU

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Handle Back
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val tvProvider = view.findViewById<TextView>(R.id.tvProvider)
        val etEndpoint = view.findViewById<EditText>(R.id.etEndpoint)
        val etApiKey = view.findViewById<EditText>(R.id.etApiKey)
        val etModel = view.findViewById<EditText>(R.id.etModel)

        val prefs = AiPreferences.get(requireContext())
        val settings = prefs.load()
        selectedProvider = settings.provider

        tvProvider.text = settings.provider.displayName
        etEndpoint.setText(settings.endpoint)
        etApiKey.setText(settings.apiKey)
        etModel.setText(settings.model)

        tvProvider.setOnClickListener {
            showProviderPicker(
                current = selectedProvider,
                onSelect = { newProvider ->
                    val oldProvider = selectedProvider
                    selectedProvider = newProvider
                    tvProvider.text = newProvider.displayName

                    if (newProvider != AiProvider.CUSTOM) {
                        val currentEndpoint = etEndpoint.text.toString().trim()
                        if (currentEndpoint.isBlank() || currentEndpoint == oldProvider.defaultEndpoint) {
                            etEndpoint.setText(newProvider.defaultEndpoint)
                        }

                        val currentModel = etModel.text.toString().trim()
                        if (currentModel.isBlank() || currentModel == oldProvider.defaultModel) {
                            etModel.setText(newProvider.defaultModel)
                        }
                    }
                },
            )
        }

        // Setup Sliders
        setupSlider(
            container = view.findViewById(R.id.sliderTemp),
            label = "Temperature",
            from = 0.0f,
            to = 2.0f,
            step = 0.1f,
            defaultValue = settings.temperature,
            formatter = { String.format("%.1f", it) },
        )

        setupSlider(
            container = view.findViewById(R.id.sliderTopP),
            label = "Top P",
            from = 0.0f,
            to = 1.0f,
            step = 0.05f,
            defaultValue = settings.topP,
            formatter = { String.format("%.2f", it) },
        )

        setupSlider(
            container = view.findViewById(R.id.sliderMaxTokens),
            label = "Max Tokens",
            from = 256f,
            to = 32000f,
            step = 256f,
            defaultValue = settings.maxTokens.toFloat(),
            formatter = { it.toInt().toString() },
        )

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val endpoint = etEndpoint.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()
            val model = etModel.text.toString().trim()

            val temperature = view.findViewById<View>(R.id.sliderTemp)
                .findViewById<Slider>(R.id.slider)
                .value
            val topP = view.findViewById<View>(R.id.sliderTopP)
                .findViewById<Slider>(R.id.slider)
                .value
            val maxTokens = view.findViewById<View>(R.id.sliderMaxTokens)
                .findViewById<Slider>(R.id.slider)
                .value
                .toInt()

            val newSettings = AiSettings(
                provider = selectedProvider,
                endpoint = endpoint,
                apiKey = apiKey,
                model = model,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
            )
            prefs.save(newSettings)
            Toast.makeText(requireContext(), "已保存 AI 配置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSlider(
        container: View,
        label: String,
        from: Float,
        to: Float,
        step: Float,
        defaultValue: Float,
        formatter: (Float) -> String,
    ) {
        val tvLabel = container.findViewById<TextView>(R.id.tvLabel)
        val tvValue = container.findViewById<TextView>(R.id.tvValue)
        val slider = container.findViewById<Slider>(R.id.slider)

        tvLabel.text = label
        slider.valueFrom = from
        slider.valueTo = to
        slider.stepSize = step
        slider.value = defaultValue.coerceIn(from, to)
        tvValue.text = formatter(slider.value)

        slider.addOnChangeListener { _, value, _ ->
            tvValue.text = formatter(value)
        }
    }

    private fun showProviderPicker(current: AiProvider, onSelect: (AiProvider) -> Unit) {
        val ctx = requireContext()
        val providers = AiProvider.entries
        val items = providers.map { it.displayName }.toTypedArray()
        val checked = providers.indexOf(current).coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("选择模型服务")
            .setSingleChoiceItems(items, checked) { dialog, which ->
                onSelect(providers[which])
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
