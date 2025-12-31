package com.example.operit.autoglm.runtime

import android.content.Context
import android.util.DisplayMetrics
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerServerManager
import com.example.operit.logging.AppLog
import com.example.operit.shizuku.ShizukuScreencap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AutoGlmVirtualScreen {
    private const val ENSURE_DEBOUNCE_MS = 1500L
    private val ensureMutex = Mutex()

    @Volatile private var lastEnsureAtMs: Long = 0L
    @Volatile private var boundChatSessionId: String? = null

    fun isReady(): Boolean {
        val binderAlive = runCatching { ShowerBinderRegistry.hasAliveService() }.getOrDefault(false)
        val displayId = runCatching { ShowerController.getDisplayId() }.getOrNull()
        return binderAlive && displayId != null
    }

    fun getSize(): Pair<Int, Int>? = runCatching { ShowerController.getVideoSize() }.getOrNull()

    fun getDisplayId(): Int? = runCatching { ShowerController.getDisplayId() }.getOrNull()

    fun ensureCreated(
        context: Context,
        onLog: ((String) -> Unit)? = null,
        bitrateKbps: Int = 3000,
    ): Boolean {
        return ensureCreatedForChatSession(
            context = context,
            chatSessionId = "",
            onLog = onLog,
            bitrateKbps = bitrateKbps,
        )
    }

    /**
     * 确保虚拟屏幕已创建，并将其“绑定”到某个聊天会话。
     *
     * Shower 当前只支持单个 display：当 chatSessionId 变化时，这里会主动重建 display，
     * 以保证“新对话不会复用旧对话的屏幕画面”。
     */
    fun ensureCreatedForChatSession(
        context: Context,
        chatSessionId: String,
        onLog: ((String) -> Unit)? = null,
        bitrateKbps: Int = 3000,
    ): Boolean {
        val ctx = context.applicationContext
        val desired = chatSessionId.trim().takeIf { it.isNotBlank() }

        if (!ShizukuScreencap.isReady()) {
            onLog?.invoke("Shizuku 未授权或未运行：无法创建虚拟屏幕")
            return false
        }

        return runBlocking(Dispatchers.IO) {
            ensureMutex.withLock {
                val ready = isReady()
                val needRecreate = ready && desired != null && boundChatSessionId != desired

                if (ready && !needRecreate) {
                    if (desired != null) boundChatSessionId = desired
                    onLog?.invoke("虚拟屏幕已就绪：displayId=${getDisplayId()}")
                    return@withLock true
                }

                val now = System.currentTimeMillis()
                if (!needRecreate && now - lastEnsureAtMs < ENSURE_DEBOUNCE_MS) {
                    onLog?.invoke("虚拟屏幕正在初始化，请稍候…")
                    return@withLock isReady()
                }
                lastEnsureAtMs = now

                if (needRecreate) {
                    onLog?.invoke("检测到对话切换：重建虚拟屏幕（旧=${boundChatSessionId ?: "<none>"} 新=$desired）")
                }

                val started =
                    runCatching { ShowerServerManager.ensureServerStarted(ctx) }
                        .getOrElse { e ->
                            AppLog.e("AutoGLM", "ensureServerStarted failed", e)
                            false
                        }
                if (!started) {
                    onLog?.invoke("Shower server 启动失败（可调用 shower_log_read 查看 /data/local/tmp/shower.log）")
                    return@withLock false
                }

                val dm: DisplayMetrics = ctx.resources.displayMetrics
                val ok =
                    runCatching {
                        ShowerController.ensureDisplay(
                            context = ctx,
                            width = dm.widthPixels,
                            height = dm.heightPixels,
                            dpi = dm.densityDpi,
                            bitrateKbps = bitrateKbps,
                        )
                    }.getOrElse { e ->
                        AppLog.e("AutoGLM", "ensureDisplay failed", e)
                        false
                    }

                // 等待 displayId / videoSize 就绪，避免 Overlay/Viewer 抢先 attach 时拿不到尺寸导致黑屏
                var id: Int? = null
                var size: Pair<Int, Int>? = null
                for (i in 0 until 50) { // 最多 5s
                    id = runCatching { ShowerController.getDisplayId() }.getOrNull()
                    size = runCatching { ShowerController.getVideoSize() }.getOrNull()
                    if (id != null && size != null) break
                    Thread.sleep(100)
                }

                val created = isReady()
                if (created) boundChatSessionId = desired

                val suffix = "displayId=$id, size=${size?.first}x${size?.second}"
                onLog?.invoke(if (ok && created) "虚拟屏幕已创建：$suffix" else "虚拟屏幕创建失败：$suffix")
                created
            }
        }
    }
}
