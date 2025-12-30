package com.example.operit.autoglm.runtime

import android.content.Context
import com.example.operit.ai.AiSettings
import com.example.operit.autoglm.agent.AutoGlmAgentParser
import com.example.operit.autoglm.agent.AutoGlmAgentPrompts
import com.example.operit.autoglm.agent.AutoGlmAgentAction
import com.example.operit.logging.AppLog
import com.example.operit.shizuku.ShizukuScreencap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoGlmAgentRunner(
    private val context: Context,
    private val settings: AiSettings,
    private val serviceProvider: () -> com.example.operit.accessibility.OperitAccessibilityService?,
    private val onLog: (String) -> Unit,
) {
    @Volatile
    var cancelled: Boolean = false

    private val client = AutoGlmVisionClient()
    private val executor = AutoGlmActionExecutor(context, serviceProvider)

    fun cancel(reason: String = "用户取消") {
        cancelled = true
        onLog("已取消：$reason")
    }

    fun run(task: String, maxSteps: Int = 25): Result<Unit> {
        return runCatching {
            if (task.isBlank()) error("任务为空")
            if (!ShizukuScreencap.isReady()) error("需要 Shizuku 权限（用于截图）")

            val systemPrompt = AutoGlmAgentPrompts.buildUiAutomationSystemPrompt()

            var lastExecSummary = ""
            for (step in 1..maxSteps) {
                if (cancelled) return@runCatching

                onLog("--------------------------------------------------")
                onLog("Step $step/$maxSteps：截图 -> 规划 -> 执行")

                val capture = ShizukuScreencap.capture().getOrElse { e ->
                    throw IllegalStateException("截图失败：${e.message ?: e.javaClass.simpleName}", e)
                }

                val userText = buildUserText(task = task, step = step, maxSteps = maxSteps, lastExecSummary = lastExecSummary)
                val reply = client.chatOnce(settings, systemPrompt, userText, capture.dataUrl).getOrElse { e ->
                    throw IllegalStateException("模型调用失败：${e.message ?: e.javaClass.simpleName}", e)
                }

                if (cancelled) return@runCatching

                val parsed = AutoGlmAgentParser.parse(reply)
                parsed.thinking?.takeIf { it.isNotBlank() }?.let { onLog("思考：$it") }
                val action = parsed.action ?: throw IllegalStateException("无法解析模型输出：缺少 action\n$reply")

                when (action) {
                    is AutoGlmAgentAction.Finish -> {
                        onLog("完成：${action.message}")
                        return@runCatching
                    }
                    is AutoGlmAgentAction.Interrupt -> {
                        onLog("中断：${action.reason}")
                        return@runCatching
                    }
                    is AutoGlmAgentAction.Do -> {
                        val name = action.action.trim()
                        onLog("动作：do(action=\"$name\", ...)")
                        val exec = executor.exec(name, action.args, onLog)
                        lastExecSummary =
                            buildString {
                                append("action=").append(name)
                                if (exec.ok) append(", ok=true") else append(", ok=false")
                                exec.message?.takeIf { it.isNotBlank() }?.let { append(", message=").append(it) }
                            }
                        if (!exec.ok) {
                            onLog("动作失败：${exec.message ?: name}")
                        } else if (exec.shouldFinish) {
                            onLog(exec.message ?: "需要用户接管/确认，已停止执行")
                            return@runCatching
                        }
                        Thread.sleep(450)
                    }
                }
            }

            onLog("已达到最大步数上限（$maxSteps），建议把任务描述得更具体一些。")
        }.onFailure { e ->
            AppLog.e("AutoGLM", "agent run failed", e)
        }
    }

    private fun buildUserText(task: String, step: Int, maxSteps: Int, lastExecSummary: String): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            appendLine("任务：$task")
            appendLine("当前步骤：$step / $maxSteps")
            if (lastExecSummary.isNotBlank()) {
                appendLine("上一动作结果：$lastExecSummary")
            }
            appendLine("当前时间：$time")
            appendLine()
            appendLine("请基于当前截图，输出下一步的 do(...) 或 finish(...)。")
        }.trim()
    }
}
