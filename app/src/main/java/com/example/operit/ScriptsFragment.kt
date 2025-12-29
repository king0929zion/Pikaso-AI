package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScriptsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scripts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.scriptsList)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = ScriptsAdapter {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ScriptEditorFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
