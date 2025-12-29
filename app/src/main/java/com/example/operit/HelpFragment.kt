package com.example.operit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class HelpFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_help, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val versionName =
            try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
            } catch (_: Exception) {
                "-"
            }
        view.findViewById<TextView>(R.id.tvVersion).text = "版本：$versionName"

        view.findViewById<View>(R.id.btnOpenRepo).setOnClickListener {
            openUrl("https://github.com/king0929zion/Pikaso-AI")
        }
        view.findViewById<View>(R.id.btnOpenIssues).setOnClickListener {
            openUrl("https://github.com/king0929zion/Pikaso-AI/issues")
        }
        view.findViewById<View>(R.id.btnOpenPermissions).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsPermissionsFragment())
                .addToBackStack(null)
                .commit()
        }
        view.findViewById<View>(R.id.btnOpenTools).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ToolsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
