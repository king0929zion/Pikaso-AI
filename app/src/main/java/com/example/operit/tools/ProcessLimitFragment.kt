package com.example.operit.tools

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.google.android.material.button.MaterialButton

class ProcessLimitFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_process_limit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<MaterialButton>(R.id.btnAppDetails).setOnClickListener {
            openAppDetails()
        }
        view.findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            openBatterySettings()
        }
        view.findViewById<MaterialButton>(R.id.btnDevOptions).setOnClickListener {
            openDeveloperOptions()
        }
        view.findViewById<MaterialButton>(R.id.btnShizuku).setOnClickListener {
            openUrl("https://shizuku.rikka.app/")
        }
    }

    private fun openAppDetails() {
        val ctx = context ?: return
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
            })
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法打开应用详情", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBatterySettings() {
        val ctx = context ?: return
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(ctx, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDeveloperOptions() {
        val ctx = context ?: return
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(ctx, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openUrl(url: String) {
        val ctx = context ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法打开链接：$url", Toast.LENGTH_SHORT).show()
        }
    }
}

