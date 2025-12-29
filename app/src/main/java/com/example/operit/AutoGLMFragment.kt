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
import com.example.operit.accessibility.AccessibilityStatus
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.OpenAiChatClient
import com.example.operit.autoglm.AutoGlmOneClickFragment
import com.example.operit.autoglm.runtime.AutoGlmExecutor
import com.example.operit.autoglm.runtime.AutoGlmPlanParser
import com.example.operit.logging.AppLog
import com.google.android.material.button.MaterialButton
import okhttp3.Call
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoGLMFragment : Fragment() {
    private var inFlightCall: Call? = null
    private var isExecuting = false
    private var executor: AutoGlmExecutor? = null

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
                renderLog(tvLog, scrollLog)
                return@setOnClickListener
            }

            val task = etTask.text.toString().trim()
            if (task.isBlank()) return@setOnClickListener

            val ctx = context ?: return@setOnClickListener
            val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)
            if (settings.endpoint.isBlank() || settings.model.isBlank()) {
                appendLog("未配置 UI 控制器模型，请先在“设置 -> AI 配置”中填写 Endpoint/模型，并在 AutoGLM 一键配置中写入 UI_CONTROLLER。")
                renderLog(tvLog, scrollLog)
                Toast.makeText(ctx, "请先完成 AutoGLM 一键配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!AccessibilityStatus.isServiceEnabled(ctx)) {
                appendLog("无障碍服务未开启：请先在“设置 -> 权限配置”中开启无障碍服务。")
                renderLog(tvLog, scrollLog)
                Toast.makeText(ctx, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, SettingsPermissionsFragment())
                    .addToBackStack(null)
                    .commit()
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
            appendLog("就绪：输入任务并点击“执行”。")
            appendLog("提示：请先完成 AutoGLM 一键配置，并开启无障碍服务。")
            renderLog(tvLog, scrollLog)
        }
    }

    private fun startExecution(task: String, onLogChanged: () -> Unit, onFinish: () -> Unit) {
        val ctx = context ?: return
        val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)

        executor = AutoGlmExecutor(ctx) { com.example.operit.accessibility.OperitAccessibilityService.instance }

        appendLog("==================================================")
        appendLog("Task: $task")
        appendLog("Model: ${settings.model}")
        appendLog("==================================================")
        onLogChanged()

        val systemPrompt = buildSystemPrompt()
        val messages = listOf(
            OpenAiChatClient.Message(role = "system", content = systemPrompt),
            OpenAiChatClient.Message(role = "user", content = task),
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
                        if (result.isFailure) {
                            appendLog("模型调用失败：$reply")
                            AppLog.e("AutoGLM", "request failed: $reply")
                            onLogChanged()
                            Toast.makeText(ctx, "AutoGLM 调用失败（检查 API Key/Endpoint）", Toast.LENGTH_SHORT).show()
                            onFinish()
                            return@runOnUiThread
                        }

                        appendLog("")
                        appendLog("=== 计划(JSON) ===")
                        appendLog(reply)
                        appendLog("==================")
                        onLogChanged()

                        Thread {
                            val exec = executor
                            val execResult =
                                runCatching {
                                    val plan = AutoGlmPlanParser.parseFromModelOutput(reply)
                                    exec?.execute(plan) { line ->
                                        activity?.runOnUiThread {
                                            appendLog(line)
                                            onLogChanged()
                                        }
                                    } ?: Result.failure(IllegalStateException("执行器未初始化"))
                                }.getOrElse { e -> Result.failure(e) }

                            activity?.runOnUiThread {
                                execResult.exceptionOrNull()?.let { e ->
                                    appendLog("执行失败：${e.message ?: e.javaClass.simpleName}")
                                    AppLog.e("AutoGLM", "execute failed", e)
                                    Toast.makeText(ctx, "执行失败：${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                onLogChanged()
                                onFinish()
                            }
                        }.start()
                    }
                },
            )
    }

    private fun buildSystemPrompt(): String {
        val date = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(Date())
        return """
你是 AutoGLM 手机自动化代理（无障碍执行器）。今天是：$date

你的目标：把用户任务拆解为可执行的 UI 自动化步骤，并输出严格 JSON（不要 Markdown、不要解释）。

输出必须是一个 JSON 对象，格式如下：
{
  "version": 1,
  "steps": [
    { "type": "home", "note": "回到桌面" },
    { "type": "launch_app", "package": "com.android.settings", "note": "打开设置" },
    { "type": "tap_text", "text": "WLAN", "timeout_ms": 8000, "note": "进入 WLAN" },
    { "type": "input", "text": "xxx", "note": "在当前输入框输入" },
    { "type": "back", "note": "返回" },
    { "type": "wait", "ms": 800, "note": "等待页面稳定" }
  ]
}

可用 type：
- home / back / recents
- launch_app（package）
- tap_text（text，timeout_ms 可选）
- tap（x,y，使用屏幕坐标，慎用）
- swipe（start_x,start_y,end_x,end_y,duration_ms 可选）
- input（text：在当前界面找到可输入控件并设置文本）
- wait（ms）

注意：
- 尽量使用 tap_text/launch_app/input，避免坐标 tap。
- 如果某步可能失败（找不到文本），请先安排 wait，再 tap_text，并给足 timeout_ms。
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
        executor?.cancelled = true
        appendLog("[Execution Cancelled] $reason")
        isExecuting = false
    }

    override fun onDestroyView() {
        inFlightCall?.cancel()
        executor?.cancelled = true
        super.onDestroyView()
    }
}

