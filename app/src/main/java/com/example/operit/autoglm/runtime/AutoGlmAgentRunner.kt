package com.example.operit.autoglm.runtime

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.operit.ai.AiSettings
import com.example.operit.autoglm.agent.AutoGlmAgentAction
import com.example.operit.autoglm.agent.AutoGlmAgentParser
import com.example.operit.autoglm.agent.AutoGlmAgentPrompts
import com.example.operit.logging.AppLog
import com.example.operit.shizuku.ShizukuScreencap
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AutoGlmAgentRunner(
    private val context: Context,
    private val settings: AiSettings,
    private val serviceProvider: () -> com.example.operit.accessibility.OperitAccessibilityService?,
    private val onLog: (String) -> Unit,
    private val onScreenshot: ((String) -> Unit)? = null,
) {
    @Volatile var cancelled: Boolean = false

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

            onLog("准备虚拟屏幕（Shower）…")
            val virtualOk = AutoGlmVirtualScreen.ensureCreated(context, onLog = onLog)
            if (!virtualOk) {
                onLog("虚拟屏幕创建失败：将回退到真实屏幕截图/无障碍执行")
            }

            val systemPrompt = AutoGlmAgentPrompts.buildUiAutomationSystemPrompt()

            var lastExecSummary = ""
            for (step in 1..maxSteps) {
                if (cancelled) return@runCatching

                onLog("--------------------------------------------------")
                onLog("Step $step/$maxSteps：截图 -> 规划 -> 执行")

                val capture = captureForAutoGlm()
                onScreenshot?.invoke(capture.screenshotFile.absolutePath)
                val w = capture.width ?: -1
                val h = capture.height ?: -1
                if (w > 0 && h > 0) {
                    onLog("截图：${w}x$h，上传 ${capture.mimeType} ${capture.imageBytes.size} bytes")
                }

                val userText =
                    buildUserText(
                        task = task,
                        step = step,
                        maxSteps = maxSteps,
                        lastExecSummary = lastExecSummary,
                    )

                val reply =
                    client.chatOnce(settings, systemPrompt, userText, capture.dataUrl).getOrElse { e ->
                        val cfg = "endpoint=${settings.endpoint}, model=${settings.model}"
                        throw IllegalStateException("模型调用失败：${e.message ?: e.javaClass.simpleName}（$cfg）", e)
                    }

                if (cancelled) return@runCatching

                val parsed = AutoGlmAgentParser.parse(reply)
                parsed.thinking?.takeIf { it.isNotBlank() }?.let { onLog("思考：$it") }
                val action =
                    parsed.action
                        ?: throw IllegalStateException("无法解析模型输出：缺少 action\n$reply")

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
                                append(", ok=").append(exec.ok)
                                exec.message?.takeIf { it.isNotBlank() }?.let { append(", message=").append(it) }
                            }
                        if (!exec.ok) {
                            onLog("动作失败：${exec.message ?: name}")
                        } else if (exec.shouldFinish) {
                            onLog(exec.message ?: "需要用户接管确认，已停止执行")
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

    private data class Capture(
        val screenshotFile: File,
        val imageBytes: ByteArray,
        val dataUrl: String,
        val width: Int?,
        val height: Int?,
        val mimeType: String,
    )

    private fun captureForAutoGlm(): Capture {
        // 优先使用虚拟屏幕截图；如果虚拟屏幕未就绪则回退到真实屏幕（Shizuku screencap）
        if (AutoGlmVirtualScreen.isReady()) {
            val bytes =
                runBlocking(Dispatchers.IO) {
                    runCatching { com.ai.assistance.showerclient.ShowerController.requestScreenshot(4000L) }.getOrNull()
                }
            if (bytes != null && bytes.isNotEmpty()) {
                val dir = File(context.cacheDir, "autoglm/virtual")
                dir.mkdirs()
                val file = File(dir, "virtual_latest.png")
                runCatching { file.writeBytes(bytes) }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val width = bounds.outWidth.takeIf { it > 0 }
                val height = bounds.outHeight.takeIf { it > 0 }

                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                return Capture(
                    screenshotFile = file,
                    imageBytes = bytes,
                    dataUrl = "data:image/png;base64,$b64",
                    width = width,
                    height = height,
                    mimeType = "image/png",
                )
            } else {
                onLog("虚拟屏幕截图失败：回退到真实屏幕截图")
            }
        }

        val real =
            ShizukuScreencap.capture(
                context = context,
                mode = ShizukuScreencap.CaptureMode.AUTOGLM_PNG,
            ).getOrElse { e ->
                throw IllegalStateException("截图失败：${e.message ?: e.javaClass.simpleName}", e)
            }
        return Capture(
            screenshotFile = real.screenshotFile,
            imageBytes = real.imageBytes,
            dataUrl = real.dataUrl,
            width = real.width,
            height = real.height,
            mimeType = real.mimeType,
        )
    }

    private fun buildUserText(
        task: String,
        step: Int,
        maxSteps: Int,
        lastExecSummary: String,
    ): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            if (step == 1) {
                appendLine("任务：$task")
            } else {
                appendLine("继续执行同一任务：$task")
            }
            appendLine("当前步数：$step / $maxSteps（每步都会结合最新截图）")
            if (lastExecSummary.isNotBlank()) {
                appendLine("上一动作结果：$lastExecSummary")
            }
            appendLine("当前时间：$time")
            appendLine()
            appendLine("请基于当前截图，输出下一步的 do(...) 或 finish(...)。")
        }.trim()
    }
}
