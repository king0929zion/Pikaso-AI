package com.example.operit.autoglm.runtime

import android.content.Context
import com.example.operit.ai.AiPreferences
import com.example.operit.logging.AppLog
import com.example.operit.shizuku.ShizukuScreencap
import com.example.operit.virtualdisplay.shower.ShowerVirtualScreenOverlay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

object AutoGlmSessionManager {
    enum class Status {
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED,
    }

    data class Session(
        val id: String,
        val chatSessionId: String?,
        val task: String,
        val startedAt: Long,
        @Volatile var status: Status,
        @Volatile var endedAt: Long?,
        val log: StringBuilder,
        val runner: AutoGlmAgentRunner,
        val maxSteps: Int,
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    fun start(context: Context, task: String, maxSteps: Int = 0, chatSessionId: String = ""): JSONObject {
        val ctx = context.applicationContext
        val chatId = chatSessionId.trim().takeIf { it.isNotBlank() }

        if (task.isBlank()) {
            return JSONObject().put("ok", false).put("error", "任务为空")
        }
        if (!ShizukuScreencap.isReady()) {
            return JSONObject().put("ok", false).put("error", "Shizuku 未授权或未运行（AutoGLM 需要截图）")
        }

        val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)
        if (settings.endpoint.isBlank() || settings.model.isBlank() || settings.apiKey.isBlank()) {
            return JSONObject().put("ok", false).put("error", "未配置 AutoGLM（设置 -> AI 配置 -> AutoGLM）")
        }

        val id = UUID.randomUUID().toString()
        val sb = StringBuilder(8 * 1024)

        // Shower 目前只支持单 display：避免多个 AutoGLM 任务同时运行互相打架/反复重建虚拟屏幕。
        runCatching {
            val now = System.currentTimeMillis()
            sessions.values
                .filter { it.status == Status.RUNNING }
                .forEach { s ->
                    s.runner.cancel("已启动新的 AutoGLM 任务（当前仅支持单任务运行）")
                    s.status = Status.CANCELLED
                    s.endedAt = now
                    synchronized(s.log) { s.log.appendLine("提示：已启动新的 AutoGLM 任务，本任务被自动取消。") }
                }
        }

        fun appendLog(line: String) {
            synchronized(sb) {
                sb.appendLine(line)
                val max = 60_000
                if (sb.length > max) {
                    sb.delete(0, sb.length - max)
                }
            }
        }

        // 每次启动任务都确保虚拟屏幕就绪（Shower）；避免重复 ensureDisplay 造成“多虚拟屏幕/画面丢失”
        runCatching {
            val ok =
                AutoGlmVirtualScreen.ensureCreatedForChatSession(
                    context = ctx,
                    chatSessionId = chatId ?: "",
                    onLog = ::appendLog,
                )
            if (!ok) appendLog("提示：虚拟屏幕未就绪，后续将回退到真实屏幕截图/无障碍执行")
            // 如果用户已授予悬浮窗权限，则自动显示“虚拟屏幕（悬浮窗）”方便查看 AI 当前操作画面
            ShowerVirtualScreenOverlay.show(ctx)
        }.onFailure { e ->
            AppLog.w("AutoGLM", "ensure virtual screen failed: ${e.message}")
            appendLog("虚拟屏幕初始化异常：${e.message ?: e.javaClass.simpleName}")
        }

        val preferredMaxSteps = AiPreferences.get(ctx).loadUiControllerMaxSteps()
        val resolvedMaxSteps = (if (maxSteps > 0) maxSteps else preferredMaxSteps).coerceIn(1, 100)

        val runner =
            AutoGlmAgentRunner(
                context = ctx,
                settings = settings,
                serviceProvider = { com.example.operit.accessibility.OperitAccessibilityService.instance },
                onLog = ::appendLog,
            )

        val session =
            Session(
                id = id,
                chatSessionId = chatId,
                task = task,
                startedAt = System.currentTimeMillis(),
                status = Status.RUNNING,
                endedAt = null,
                log = sb,
                runner = runner,
                maxSteps = resolvedMaxSteps,
            )

        sessions[id] = session
        updateForeground(ctx)

        Thread {
            val result = runner.run(task = task, maxSteps = session.maxSteps)
            val now = System.currentTimeMillis()
            session.endedAt = now
            session.status =
                when {
                    runner.cancelled -> Status.CANCELLED
                    result.isSuccess -> Status.SUCCESS
                    else -> Status.FAILED
                }

            result.exceptionOrNull()?.let { e ->
                AppLog.e("AutoGLM", "session failed id=$id", e)
                appendLog("失败：${e.message ?: e.javaClass.simpleName}")
            }
            updateForeground(ctx)
        }.start()

        return JSONObject()
            .put("ok", true)
            .put("session_id", id)
            .put("chat_session_id", chatId ?: JSONObject.NULL)
            .put("status", session.status.name)
            .put("startedAt", session.startedAt)
            .put("recommended_poll_ms", 1200)
            .put("hint", "请循环调用 autoglm_status(session_id=...) 直到 status != RUNNING")
    }

    fun status(sessionId: String, maxChars: Int = 8000): JSONObject {
        val s = sessions[sessionId] ?: return JSONObject().put("ok", false).put("error", "未知 session_id")
        val text =
            synchronized(s.log) {
                val raw = s.log.toString()
                val safe = maxChars.coerceIn(200, 60_000)
                if (raw.length <= safe) raw else raw.takeLast(safe)
            }
        return JSONObject()
            .put("ok", true)
            .put("session_id", s.id)
            .put("chat_session_id", s.chatSessionId ?: JSONObject.NULL)
            .put("task", s.task)
            .put("status", s.status.name)
            .put("startedAt", s.startedAt)
            .put("endedAt", s.endedAt ?: JSONObject.NULL)
            .put("log", text)
    }

    fun cancel(sessionId: String): JSONObject {
        val s = sessions[sessionId] ?: return JSONObject().put("ok", false).put("error", "未知 session_id")
        s.runner.cancel("Chat 工具取消")
        s.status = Status.CANCELLED
        s.endedAt = System.currentTimeMillis()
        return JSONObject().put("ok", true).put("session_id", s.id).put("status", s.status.name)
    }

    private fun updateForeground(context: Context) {
        val anyRunning = sessions.values.any { it.status == Status.RUNNING }
        AutoGlmForegroundService.setRunning(context.applicationContext, anyRunning)
    }
}
