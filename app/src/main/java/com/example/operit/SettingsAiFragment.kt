package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider

class SettingsAiFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Handle Back
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Setup Sliders
        setupSlider(view.findViewById(R.id.sliderTemp), "Temperature", 0.0f, 2.0f, 0.7f)
        setupSlider(view.findViewById(R.id.sliderTopP), "Top P", 0.0f, 1.0f, 1.0f)
        setupSlider(view.findViewById(R.id.sliderMaxTokens), "Max Tokens", 256f, 32000f, 4096f)
    }

    private fun setupSlider(container: View, label: String, from: Float, to: Float, default: Float) {
        val tvLabel = container.findViewById<TextView>(R.id.tvLabel)
        val tvValue = container.findViewById<TextView>(R.id.tvValue)
        val slider = container.findViewById<Slider>(R.id.slider)

        tvLabel.text = label
        slider.valueFrom = from
        slider.valueTo = to
        slider.value = default
        tvValue.text = default.toString()

        slider.addOnChangeListener { _, value, _ ->
            tvValue.text = String.format("%.1f", value)
        }
    }
}
