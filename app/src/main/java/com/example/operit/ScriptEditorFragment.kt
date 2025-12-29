package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.scripts.ScriptStore

class ScriptEditorFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_script_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val scriptId = requireArguments().getString(ARG_SCRIPT_ID).orEmpty()
        if (scriptId.isBlank()) {
            Toast.makeText(requireContext(), "脚本ID为空", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        val store = ScriptStore.get(requireContext())
        val meta = store.getMeta(scriptId)
        if (meta == null) {
            Toast.makeText(requireContext(), "脚本不存在", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val etContent = view.findViewById<EditText>(R.id.etContent)
        tvTitle.text = meta.name
        etContent.setText(store.readContent(scriptId))

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val content = etContent.text?.toString().orEmpty()
            store.writeContent(scriptId, content)
            store.saveMeta(meta.copy(updatedAt = System.currentTimeMillis()))
            Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_SCRIPT_ID = "script_id"

        fun newInstance(scriptId: String): ScriptEditorFragment {
            return ScriptEditorFragment().apply {
                arguments = Bundle().apply { putString(ARG_SCRIPT_ID, scriptId) }
            }
        }
    }
}
