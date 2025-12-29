package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.OpenAiChatClient
import com.example.operit.autoglm.AutoGlmOneClickFragment
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoGLMFragment : Fragment() {
    private var inFlightCall: Call? = null
    private var isExecuting = false
    private val logBuilder = StringBuilder()
    private val chatClient = OpenAiChatClient()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_autoglm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<MaterialButton>(R.id.btnOneClick).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AutoGlmOneClickFragment())
                .addToBackStack(null)
                .commit()
        }

        val etTask = view.findViewById<EditText>(R.id.etTask)
        val btnExecute = view.findViewById<MaterialButton>(R.id.btnExecute)
        val tvLog = view.findViewById<TextView>(R.id.tvLog)
        val scrollLog = view.findViewById<ScrollView>(R.id.scrollLog)

        btnExecute.setOnClickListener {
            if (isExecuting) {
                cancelExecution("用户取消")
                btnExecute.text = "执行"
                return@setOnClickListener
            }

            val task = etTask.text.toString().trim()
            if (task.isBlank()) return@setOnClickListener

            val ctx = context ?: return@setOnClickListener
            val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)
            if (settings.apiKey.isBlank()) {
                appendLog("未配置 UI 控制器模型。请先点击右上角“一键配置”。")
                renderLog(tvLog, scrollLog)
                Toast.makeText(ctx, "请先完成 AutoGLM 一键配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startExecution(
                task = task,
                onLogChanged = { renderLog(tvLog, scrollLog) },
                onFinish = {
                    isExecuting = false
                    btnExecute.text = "执行"
                },
            )
            isExecuting = true
            btnExecute.text = "取消"
        }

        if (logBuilder.isEmpty()) {
            appendLog("就绪：请输入任务并点击“执行”。（注意：当前阶段仅调用模型输出执行步骤，手机自动化执行链路后续迁移。）")
            renderLog(tvLog, scrollLog)
        }
    }

    private fun startExecution(task: String, onLogChanged: () -> Unit, onFinish: () -> Unit) {
        val ctx = context ?: return
        val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)

        appendLog("==================================================")
        appendLog("Task: $task")
        appendLog("Model: ${settings.model}")
        appendLog("==================================================")
        onLogChanged()

        val systemPrompt = buildSystemPrompt()
        val messages = listOf(
            OpenAiChatClient.Message(role = "system", content = systemPrompt),
            OpenAiChatClient.Message(
                role = "user",
                content = task,
            ),
        )

        inFlightCall?.cancel()
        inFlightCall =
            chatClient.chat(
                settings = settings,
                messages = messages,
                onResult = { result ->
                    activity?.runOnUiThread {
                        val reply = result.getOrElse { e ->
                            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                            "请求失败：$msg"
                        }

                        appendLog("")
                        appendLog("=== 模型输出 ===")
                        appendLog(reply)
                        appendLog("================")
                        onLogChanged()

                        if (result.isFailure) {
                            Toast.makeText(ctx, "AutoGLM 调用失败（检查 API Key/Endpoint）", Toast.LENGTH_SHORT).show()
                        }
                        onFinish()
                    }
                },
            )
    }

    private fun buildSystemPrompt(): String {
        val date = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(Date())
        return """
你是 AutoGLM 手机自动化代理（UI 控制器）。
今天是：$date

请把用户任务分解为最多 25 个清晰的步骤，并为每一步给出：
1) 目标（要做什么）
2) 动作（tap/swipe/input/press_key/screenshot 等）
3) 预期结果（如何判断成功）

输出格式尽量接近 CLI 日志，便于后续自动化执行链路接入。
""".trim()
    }

    private fun appendLog(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logBuilder.append('[').append(time).append("] ").appendLine(line)
    }

    private fun renderLog(tvLog: TextView, scrollLog: ScrollView) {
        tvLog.text = logBuilder.toString().trimEnd()
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun cancelExecution(reason: String) {
        inFlightCall?.cancel()
        appendLog("[Execution Cancelled] $reason")
        isExecuting = false
    }

    override fun onDestroyView() {
        inFlightCall?.cancel()
        super.onDestroyView()
    }
}
