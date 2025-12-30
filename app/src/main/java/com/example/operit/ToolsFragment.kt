package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.autoglm.AutoGlmOneClickFragment
import com.example.operit.autoglm.AutoGlmConnectionTestFragment
import com.example.operit.tools.ProcessLimitFragment
import com.example.operit.virtualdisplay.VirtualScreenFragment

class ToolsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.toolsGrid)
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        
        recyclerView.adapter = ToolsAdapter { id ->
            val fragment = when (id) {
                "web2apk" -> Web2ApkFragment()
                "autoglm" -> AutoGLMFragment()
                "config" -> AutoGlmOneClickFragment()
                "autoglm_test" -> AutoGlmConnectionTestFragment()
                "virtual_screen" -> VirtualScreenFragment()
                "process" -> ProcessLimitFragment()
                else -> null
            }
            
            if (fragment != null) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            } else {
                Toast.makeText(requireContext(), "该模块尚未迁移：$id", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
