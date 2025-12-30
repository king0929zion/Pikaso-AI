package com.example.operit.autoglm.runtime

import android.content.Context
import com.example.operit.accessibility.AccessibilityStatus
import com.example.operit.ai.AiPreferences
import com.example.operit.logging.AppLog
import com.example.operit.shizuku.ShizukuScreencap
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
        val task: String,
        val startedAt: Long,
        @Volatile var status: Status,
        @Volatile var endedAt: Long?,
        val log: StringBuilder,
        val runner: AutoGlmAgentRunner,
        val maxSteps: Int,
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    fun start(context: Context, task: String, maxSteps: Int = 25): JSONObject {
        val ctx = context.applicationContext

        if (task.isBlank()) {
            return JSONObject().put("ok", false).put("error", "任务为空")
        }
        if (!AccessibilityStatus.isServiceEnabled(ctx)) {
            return JSONObject().put("ok", false).put("error", "无障碍未开启")
        }
        if (!ShizukuScreencap.isReady()) {
            return JSONObject().put("ok", false).put("error", "Shizuku 未授权或未运行（AutoGLM 需要截图）")
        }

        val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)
        if (settings.endpoint.isBlank() || settings.model.isBlank()) {
            return JSONObject().put("ok", false).put("error", "未配置 UI 控制器模型（设置->AI 配置->AutoGLM）")
        }

        val id = UUID.randomUUID().toString()
        val sb = StringBuilder(8 * 1024)

        val runner =
            AutoGlmAgentRunner(
                context = ctx,
                settings = settings,
                serviceProvider = { com.example.operit.accessibility.OperitAccessibilityService.instance },
                onLog = { line ->
                    synchronized(sb) {
                        sb.appendLine(line)
                        // 控制内存：最多保留 60k 字符
                        val max = 60_000
                        if (sb.length > max) {
                            sb.delete(0, sb.length - max)
                        }
                    }
                },
            )

        val session =
            Session(
                id = id,
                task = task,
                startedAt = System.currentTimeMillis(),
                status = Status.RUNNING,
                endedAt = null,
                log = sb,
                runner = runner,
                maxSteps = maxSteps.coerceIn(1, 50),
            )

        sessions[id] = session

        Thread {
            val result = runner.run(task = task, maxSteps = session.maxSteps)
            val now = System.currentTimeMillis()
            session.endedAt = now
            if (runner.cancelled) {
                session.status = Status.CANCELLED
            } else {
                session.status = if (result.isSuccess) Status.SUCCESS else Status.FAILED
            }
            result.exceptionOrNull()?.let { e ->
                AppLog.e("AutoGLM", "session failed id=$id", e)
                synchronized(sb) {
                    sb.appendLine("失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }.start()

        return JSONObject()
            .put("ok", true)
            .put("session_id", id)
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
}
