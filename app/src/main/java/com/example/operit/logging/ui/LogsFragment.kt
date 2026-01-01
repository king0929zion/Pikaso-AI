package com.example.operit.logging.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.logging.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsFragment : Fragment() {

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = context ?: return@registerForActivityResult
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(AppLog.readAll().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(ctx, "已导出日志", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvConsole = view.findViewById<TextView>(R.id.tvConsole)

        view.findViewById<View>(R.id.btnClear).setOnClickListener {
            AppLog.clear()
            render(tvConsole)
            Toast.makeText(requireContext(), "已清除", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnExport).setOnClickListener {
            val fileName =
                "pikaso_log_" +
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
                    ".txt"
            exportLauncher.launch(fileName)
        }

        render(tvConsole)
    }

    private fun render(tvConsole: TextView) {
        val content = AppLog.readAll()
        tvConsole.text =
            if (content.isBlank()) {
                "[暂无日志]\n\n提示：后续会逐步把各模块关键事件写入这里。"
            } else {
                content.trimEnd()
            }
    }
}
