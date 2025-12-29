package com.example.operit

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvConsole = view.findViewById<TextView>(R.id.tvConsole)
        
        // Mock Logs with coloring
        val logs = listOf(
            Triple("INFO", "System", "Service started"),
            Triple("INFO", "AutoGLM", "Model loaded successfully"),
            Triple("WARN", "Network", "Slow connection detected"),
            Triple("DEBUG", "Input", "IME switched to Operit")
        )

        val sb = SpannableString(buildString {
            logs.forEach { (level, tag, msg) ->
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                append("[$time] [$tag] $msg\n")
            }
        })

        // Apply simplistic coloring (in real app, calculate ranges)
        // Here we just set text for demo
        tvConsole.text = sb
    }
}
