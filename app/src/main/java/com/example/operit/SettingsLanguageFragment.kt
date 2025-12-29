package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.operit.settings.LanguagePreferences

class SettingsLanguageFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_language, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val prefs = LanguagePreferences.get(requireContext())

        val rowZh = view.findViewById<View>(R.id.rowZh)
        val rowEn = view.findViewById<View>(R.id.rowEn)
        val iconZh = view.findViewById<View>(R.id.iconZh)
        val iconEn = view.findViewById<View>(R.id.iconEn)

        fun refresh() {
            when (prefs.getLanguageTag()) {
                LanguagePreferences.TAG_EN -> {
                    iconZh.visibility = View.GONE
                    iconEn.visibility = View.VISIBLE
                }
                else -> {
                    iconZh.visibility = View.VISIBLE
                    iconEn.visibility = View.GONE
                }
            }
        }

        fun applyAndRecreate(tag: String) {
            prefs.setLanguageTag(tag)
            prefs.apply()
            refresh()
            activity?.recreate()
        }

        rowZh.setOnClickListener { applyAndRecreate(LanguagePreferences.TAG_ZH_CN) }
        rowEn.setOnClickListener { applyAndRecreate(LanguagePreferences.TAG_EN) }

        refresh()
    }
}
