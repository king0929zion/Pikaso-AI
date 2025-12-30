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

object AutoGlmVirtualScreen {
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
        val ctx = context.applicationContext
        if (!ShizukuScreencap.isReady()) {
            onLog?.invoke("Shizuku 未授权或未运行：无法创建虚拟屏幕")
            return false
        }

        return runBlocking(Dispatchers.IO) {
            val started =
                runCatching { ShowerServerManager.ensureServerStarted(ctx) }
                    .getOrElse { e ->
                        AppLog.e("AutoGLM", "ensureServerStarted failed", e)
                        false
                    }
            if (!started) {
                onLog?.invoke("Shower server 启动失败（可调用 shower_log_read 查看 /data/local/tmp/shower.log）")
                return@runBlocking false
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

            val id = runCatching { ShowerController.getDisplayId() }.getOrNull()
            onLog?.invoke(if (ok) "虚拟屏幕已创建：displayId=$id" else "虚拟屏幕创建失败：displayId=$id")
            ok
        }
    }
}

