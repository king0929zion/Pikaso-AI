package com.example.operit.toolsystem

import android.content.Context
import android.provider.Settings
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerEnvironment
import com.example.operit.autoglm.runtime.AutoGlmSessionManager
import com.example.operit.logging.AppLog
import com.example.operit.scripts.ScriptStore
import com.example.operit.shizuku.ShizukuScreencap
import com.example.operit.virtualdisplay.VirtualDisplayManager
import com.example.operit.virtualdisplay.shower.ShowerVirtualScreenOverlay
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

object ChatToolRegistry {
    fun defaultTools(): List<com.example.operit.ai.OpenAiChatClient.ToolDefinition> {
        return listOf(
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "scripts_list",
                description = "列出本地脚本库中的脚本（id/name/desc/updatedAt/sizeBytes）。",
                parameters = JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "scripts_read",
                description = "读取脚本内容。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("id", JSONObject().put("type", "string").put("description", "脚本 id")))
                        .put("required", JSONArray().put("id"))
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "scripts_write",
                description = "写入脚本内容（会覆盖原内容）。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("id", JSONObject().put("type", "string").put("description", "脚本 id"))
                                .put("content", JSONObject().put("type", "string").put("description", "脚本内容")),
                        )
                        .put("required", JSONArray().put("id").put("content"))
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "scripts_create",
                description = "新建脚本（可选指定 name/desc/content）。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("name", JSONObject().put("type", "string").put("description", "脚本名称（可选）"))
                                .put("desc", JSONObject().put("type", "string").put("description", "脚本描述（可选）"))
                                .put("content", JSONObject().put("type", "string").put("description", "脚本内容（可选）")),
                        )
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "logs_read",
                description = "读取应用日志（可指定最大字符数，默认 8000）。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("max_chars", JSONObject().put("type", "integer").put("description", "最大字符数")))
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "logs_clear",
                description = "清空应用日志（不可恢复）。",
                parameters = JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "virtual_screen_create",
                description = "创建/确保虚拟屏幕（VirtualDisplay）并返回 displayId。",
                parameters = JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "virtual_screen_release",
                description = "释放虚拟屏幕（VirtualDisplay）。",
                parameters = JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "virtual_screen_capture",
                description = "截取虚拟屏幕最新一帧到文件（可指定 path，默认写入 cache）。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("path", JSONObject().put("type", "string").put("description", "输出路径（可选）")))
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "shower_overlay_show",
                description = "显示虚拟屏幕悬浮窗（Shower）。需要：悬浮窗权限 + Shizuku。",
                parameters = JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "shower_overlay_hide",
                description = "关闭虚拟屏幕悬浮窗（Shower）。",
                parameters = JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "shower_status",
                description = "获取 Shower 虚拟屏幕状态（权限、binder、displayId、悬浮窗状态）。",
                parameters = JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "shower_log_read",
                description = "读取 shower server 日志（/data/local/tmp/shower.log，需要 Shizuku）。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("max_chars", JSONObject().put("type", "integer").put("description", "最大字符数（默认 8000）")))
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "autoglm_run",
                description = "启动 AutoGLM（autoglm-phone）执行手机自动化任务。需要无障碍与 Shizuku。返回 session_id，用 autoglm_status 查询进度。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("task", JSONObject().put("type", "string").put("description", "要执行的手机自动化任务"))
                                .put(
                                    "max_steps",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("minimum", 1)
                                        .put("maximum", 100)
                                        .put("description", "最大步数（可选；不填则使用“设置->AutoGLM 配置”里的默认值）"),
                                ),
                        )
                        .put("required", JSONArray().put("task"))
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "autoglm_status",
                description = "查询 AutoGLM session 状态与日志片段。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("session_id", JSONObject().put("type", "string").put("description", "autoglm_run 返回的 session_id"))
                                .put("max_chars", JSONObject().put("type", "integer").put("description", "返回日志最大字符数（可选，默认 8000）")),
                        )
                        .put("required", JSONArray().put("session_id"))
                        .put("additionalProperties", false),
            ),
            com.example.operit.ai.OpenAiChatClient.ToolDefinition(
                name = "autoglm_cancel",
                description = "取消 AutoGLM session。",
                parameters =
                    JSONObject()
                        .put("type", "object")
                        .put("properties", JSONObject().put("session_id", JSONObject().put("type", "string").put("description", "session_id")))
                        .put("required", JSONArray().put("session_id"))
                        .put("additionalProperties", false),
            ),
        )
    }

    fun execute(context: Context, name: String, argumentsJson: String): Result<String> {
        val args = parseArgs(argumentsJson)
        return runCatching {
            val result =
                when (name) {
                    "scripts_list" -> scriptsList(context)
                    "scripts_read" -> scriptsRead(context, args.getString("id"))
                    "scripts_write" -> scriptsWrite(context, args.getString("id"), args.getString("content"))
                    "scripts_create" -> scriptsCreate(context, args)
                    "logs_read" -> logsRead(args.optInt("max_chars", 8000))
                    "logs_clear" -> logsClear()
                    "virtual_screen_create" -> virtualScreenCreate(context)
                    "virtual_screen_release" -> virtualScreenRelease(context)
                    "virtual_screen_capture" -> virtualScreenCapture(context, args.optString("path", ""))
                    "shower_overlay_show" -> showerOverlayShow(context)
                    "shower_overlay_hide" -> showerOverlayHide()
                    "shower_status" -> showerStatus(context)
                    "shower_log_read" -> showerLogRead(args.optInt("max_chars", 8000))
                    "autoglm_run" -> AutoGlmSessionManager.start(context, args.optString("task", ""), args.optInt("max_steps", 0))
                    "autoglm_status" ->
                        AutoGlmSessionManager.status(
                            sessionId = args.optString("session_id", ""),
                            maxChars = args.optInt("max_chars", 8000),
                        )
                    "autoglm_cancel" -> AutoGlmSessionManager.cancel(args.optString("session_id", ""))
                    else -> JSONObject().put("ok", false).put("error", "未知工具：$name")
                }
            result.toString()
        }
    }

    private fun scriptsList(context: Context): JSONObject {
        val store = ScriptStore.get(context)
        val list = store.list()
        val arr =
            JSONArray().apply {
                list.forEach { meta ->
                    put(
                        JSONObject()
                            .put("id", meta.id)
                            .put("name", meta.name)
                            .put("desc", meta.desc)
                            .put("updatedAt", meta.updatedAt)
                            .put("sizeBytes", store.contentSizeBytes(meta.id)),
                    )
                }
            }
        return JSONObject().put("ok", true).put("items", arr).put("count", list.size)
    }

    private fun scriptsRead(context: Context, id: String): JSONObject {
        val store = ScriptStore.get(context)
        val meta = store.getMeta(id)
        val content = store.readContent(id)
        return JSONObject()
            .put("ok", true)
            .put(
                "meta",
                JSONObject()
                    .put("id", id)
                    .put("name", meta?.name ?: "")
                    .put("desc", meta?.desc ?: "")
                    .put("updatedAt", meta?.updatedAt ?: 0L)
                    .put("sizeBytes", store.contentSizeBytes(id)),
            )
            .put("content", content)
    }

    private fun scriptsWrite(context: Context, id: String, content: String): JSONObject {
        val store = ScriptStore.get(context)
        val now = System.currentTimeMillis()
        val old = store.getMeta(id)
        val meta =
            ScriptStore.ScriptMeta(
                id = id,
                name = old?.name ?: "script_$id.js",
                desc = old?.desc ?: "",
                updatedAt = now,
            )
        store.saveMeta(meta)
        store.writeContent(id, content)
        AppLog.i("Tool:scripts_write", "updated script id=$id size=${content.toByteArray(Charsets.UTF_8).size}")
        return JSONObject().put("ok", true).put("id", id).put("updatedAt", now).put("sizeBytes", store.contentSizeBytes(id))
    }

    private fun scriptsCreate(context: Context, args: JSONObject): JSONObject {
        val store = ScriptStore.get(context)
        val meta = store.createNew()

        val name = args.optString("name").takeIf { it.isNotBlank() }
        val desc = args.optString("desc").takeIf { it.isNotBlank() }
        val content = args.optString("content").takeIf { it.isNotBlank() }

        val now = System.currentTimeMillis()
        val updatedMeta =
            ScriptStore.ScriptMeta(
                id = meta.id,
                name = name ?: meta.name,
                desc = desc ?: meta.desc,
                updatedAt = now,
            )
        store.saveMeta(updatedMeta)
        if (content != null) {
            store.writeContent(meta.id, content)
        }
        AppLog.i("Tool:scripts_create", "created script id=${meta.id}")
        return JSONObject()
            .put("ok", true)
            .put("id", meta.id)
            .put("name", updatedMeta.name)
            .put("desc", updatedMeta.desc)
            .put("updatedAt", updatedMeta.updatedAt)
            .put("sizeBytes", store.contentSizeBytes(meta.id))
    }

    private fun logsRead(maxChars: Int): JSONObject {
        val raw = AppLog.readAll()
        val safeMax = maxChars.coerceIn(1, 200_000)
        val content = if (raw.length <= safeMax) raw else raw.takeLast(safeMax)
        return JSONObject().put("ok", true).put("content", content)
    }

    private fun logsClear(): JSONObject {
        AppLog.clear()
        AppLog.i("Tool:logs_clear", "logs cleared")
        return JSONObject().put("ok", true)
    }

    private fun virtualScreenCreate(context: Context): JSONObject {
        val manager = VirtualDisplayManager.getInstance(context)
        val id = manager.ensureVirtualDisplay()
        return if (id == null) {
            JSONObject().put("ok", false).put("error", "创建虚拟屏幕失败")
        } else {
            JSONObject().put("ok", true).put("displayId", id)
        }
    }

    private fun virtualScreenRelease(context: Context): JSONObject {
        val manager = VirtualDisplayManager.getInstance(context)
        manager.release()
        return JSONObject().put("ok", true)
    }

    private fun virtualScreenCapture(context: Context, path: String): JSONObject {
        val manager = VirtualDisplayManager.getInstance(context)
        val id = manager.ensureVirtualDisplay()
        if (id == null) return JSONObject().put("ok", false).put("error", "虚拟屏幕未就绪")

        val file =
            if (path.isNotBlank()) {
                File(path)
            } else {
                File(context.cacheDir, "virtual_display_latest.png")
            }
        val ok = manager.captureLatestFrameToFile(file)
        return if (!ok || !file.exists()) {
            JSONObject().put("ok", false).put("error", "截图失败（暂无帧）")
        } else {
            JSONObject().put("ok", true).put("displayId", id).put("path", file.absolutePath).put("sizeBytes", file.length())
        }
    }

    private fun showerOverlayShow(context: Context): JSONObject {
        val ctx = context.applicationContext
        if (!Settings.canDrawOverlays(ctx)) {
            return JSONObject().put("ok", false).put("error", "未授予悬浮窗权限（请在 权限配置 中开启）")
        }
        if (!ShizukuScreencap.isReady()) {
            return JSONObject().put("ok", false).put("error", "Shizuku 未授权或未运行")
        }
        ShowerVirtualScreenOverlay.show(ctx)
        return JSONObject().put("ok", true).put("showing", true)
    }

    private fun showerOverlayHide(): JSONObject {
        ShowerVirtualScreenOverlay.hide()
        return JSONObject().put("ok", true).put("showing", false)
    }

    private fun showerStatus(context: Context): JSONObject {
        val ctx = context.applicationContext
        val overlay = Settings.canDrawOverlays(ctx)
        val shizuku = ShizukuScreencap.isReady()
        val binderAlive = runCatching { ShowerBinderRegistry.hasAliveService() }.getOrDefault(false)
        val displayId = runCatching { ShowerController.getDisplayId() }.getOrNull()
        val showing = ShowerVirtualScreenOverlay.isShowing()
        return JSONObject()
            .put("ok", true)
            .put("overlayPermission", overlay)
            .put("shizukuReady", shizuku)
            .put("binderAlive", binderAlive)
            .put("displayId", displayId ?: JSONObject.NULL)
            .put("overlayShowing", showing)
    }

    private fun showerLogRead(maxChars: Int): JSONObject {
        val safeMax = maxChars.coerceIn(1, 200_000)
        val runner = ShowerEnvironment.shellRunner
            ?: return JSONObject().put("ok", false).put("error", "Shower ShellRunner 未初始化")

        val content =
            runBlocking(Dispatchers.IO) {
                val r = runner.run("cat /data/local/tmp/shower.log 2>/dev/null || true", com.ai.assistance.showerclient.ShellIdentity.SHELL)
                (r.stdout + if (r.stderr.isBlank()) "" else "\n" + r.stderr).trim()
            }
        val out = if (content.length <= safeMax) content else content.takeLast(safeMax)
        return JSONObject().put("ok", true).put("content", out)
    }

    private fun parseArgs(argumentsJson: String): JSONObject {
        val trimmed = argumentsJson.trim()
        if (trimmed.isBlank()) return JSONObject()
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            // 兼容部分模型返回的“近似 JSON”或转义问题：兜底成原始字符串
            JSONObject().put("_raw", argumentsJson)
        }
    }
}
