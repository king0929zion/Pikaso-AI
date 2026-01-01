package com.example.operit.settings.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.prompts.PromptPreferences

class SettingsPromptsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_prompts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val prefs = PromptPreferences.get(requireContext())
        val et = view.findViewById<EditText>(R.id.etSystemPrompt)
        et.setText(prefs.getChatSystemPrompt())

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            prefs.setChatSystemPrompt(et.text?.toString().orEmpty().trim())
            Toast.makeText(requireContext(), "已保存提示词", Toast.LENGTH_SHORT).show()
        }
    }
}
