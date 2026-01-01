package com.example.operit.scripts.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.operit.R
import com.example.operit.scripts.ScriptStore

class ScriptsFragment : Fragment() {
    private lateinit var adapter: ScriptsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scripts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.scriptsList)
        list.layoutManager = LinearLayoutManager(context)

        adapter =
            ScriptsAdapter { meta ->
                openEditor(meta.id)
            }
        list.adapter = adapter

        view.findViewById<View>(R.id.btnNewScript).setOnClickListener {
            val store = ScriptStore.get(requireContext())
            val meta = store.createNew()
            openEditor(meta.id)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val ctx = context ?: return
        val store = ScriptStore.get(ctx)
        val rows =
            store.list().map { meta ->
                ScriptsAdapter.ScriptRow(
                    meta = meta,
                    sizeText = formatSize(store.contentSizeBytes(meta.id)),
                )
            }
        adapter.submit(rows)
    }

    private fun openEditor(scriptId: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ScriptEditorFragment.newInstance(scriptId))
            .addToBackStack(null)
            .commit()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1fKB", kb)
        val mb = kb / 1024.0
        return String.format("%.1fMB", mb)
    }
}
