package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.operit.settings.FeaturePreferences
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFeaturesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_features, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val prefs = FeaturePreferences.get(requireContext())

        val switchAutoScreenshot = view.findViewById<SwitchMaterial>(R.id.switchAutoScreenshotAnalysis)
        val switchBackgroundRun = view.findViewById<SwitchMaterial>(R.id.switchBackgroundRun)

        switchAutoScreenshot.isChecked = prefs.isAutoScreenshotAnalysisEnabled()
        switchBackgroundRun.isChecked = prefs.isBackgroundRunEnabled()

        switchAutoScreenshot.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAutoScreenshotAnalysisEnabled(isChecked)
        }

        switchBackgroundRun.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBackgroundRunEnabled(isChecked)
        }
    }
}
