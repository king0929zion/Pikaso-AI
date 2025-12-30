package com.example.operit.autoglm.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.operit.accessibility.OperitAccessibilityService
import com.example.operit.logging.AppLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AutoGlmActionExecutor(
    private val context: Context,
    private val serviceProvider: () -> OperitAccessibilityService?,
) {
    data class ExecResult(
        val ok: Boolean,
        val message: String? = null,
        val shouldFinish: Boolean = false,
    )

    fun exec(actionName: String, args: Map<String, String>, onLog: (String) -> Unit): ExecResult {
        val service = serviceProvider() ?: return ExecResult(false, "无障碍服务未连接（请先在系统设置中开启）")
        return when (actionName) {
            "Home" -> ExecResult(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            "Back" -> ExecResult(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            "Wait" -> {
                val seconds = parseSeconds(args["duration"]) ?: 1.0
                Thread.sleep((seconds * 1000).toLong().coerceIn(0L, 30_000L))
                ExecResult(true)
            }
            "Launch" -> {
                val app = args["app"].orEmpty().trim()
                if (app.isBlank()) return ExecResult(false, "Launch 缺少 app 参数")
                val pkg = AutoGlmAppResolver.resolvePackageName(context, app)
                if (pkg.isBlank()) return ExecResult(false, "未找到应用：$app")
                ExecResult(launchApp(pkg), if (pkg != app) "Launch: $app -> $pkg" else "Launch: $pkg")
            }
            "Tap" -> {
                val element = args["element"].orEmpty()
                val (x, y) = parseRelativePoint(element) ?: return ExecResult(false, "Tap 坐标无效：$element")
                val (absX, absY) = toAbsPoint(x, y)
                val ok = awaitGesture { cb -> service.performTap(absX, absY, cb) }
                ExecResult(ok, if (!ok) "Tap 失败" else null)
            }
            "Swipe" -> {
                val start = args["start"].orEmpty()
                val end = args["end"].orEmpty()
                val (sx, sy) = parseRelativePoint(start) ?: return ExecResult(false, "Swipe start 无效：$start")
                val (ex, ey) = parseRelativePoint(end) ?: return ExecResult(false, "Swipe end 无效：$end")
                val (absSx, absSy) = toAbsPoint(sx, sy)
                val (absEx, absEy) = toAbsPoint(ex, ey)
                val ok =
                    awaitGesture { cb ->
                        service.performSwipe(
                            startX = absSx,
                            startY = absSy,
                            endX = absEx,
                            endY = absEy,
                            durationMs = 350L,
                            onDone = cb,
                        )
                    }
                ExecResult(ok, if (!ok) "Swipe 失败" else null)
            }
            "Long Press" -> {
                val element = args["element"].orEmpty()
                val (x, y) = parseRelativePoint(element) ?: return ExecResult(false, "Long Press 坐标无效：$element")
                val (absX, absY) = toAbsPoint(x, y)
                val ok = awaitGesture { cb -> service.performLongPress(absX, absY, cb) }
                ExecResult(ok, if (!ok) "Long Press 失败" else null)
            }
            "Double Tap" -> {
                val element = args["element"].orEmpty()
                val (x, y) = parseRelativePoint(element) ?: return ExecResult(false, "Double Tap 坐标无效：$element")
                val (absX, absY) = toAbsPoint(x, y)
                val ok1 = awaitGesture { cb -> service.performTap(absX, absY, cb) }
                Thread.sleep(80)
                val ok2 = awaitGesture { cb -> service.performTap(absX, absY, cb) }
                ExecResult(ok1 && ok2, if (!(ok1 && ok2)) "Double Tap 失败" else null)
            }
            "Type", "Type_Name" -> {
                val text = args["text"].orEmpty()
                if (text.isBlank()) return ExecResult(false, "Type 缺少 text 参数")
                val ok = inputText(service, text, onLog)
                ExecResult(ok, if (!ok) "输入失败" else null)
            }
            "Take_over" -> ExecResult(true, args["message"] ?: "需要用户接管（登录/验证码）", shouldFinish = true)
            "Interact" -> ExecResult(true, "需要用户选择（Interact）", shouldFinish = true)
            "Note" -> ExecResult(true)
            "Call_API" -> ExecResult(true)
            else -> ExecResult(false, "未知 action：$actionName")
        }
    }

    private fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLog.e("AutoGLM", "launch app failed: $packageName", e)
            false
        }
    }

    private fun inputText(service: OperitAccessibilityService, text: String, onLog: (String) -> Unit): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val target = findFirstEditable(root)
        if (target == null) {
            onLog("未找到可输入控件（EditText）")
            return false
        }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) onLog("输入失败（ACTION_SET_TEXT 返回 false）")
        return ok
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur.isEditable || cur.className?.toString() == "android.widget.EditText") {
                return cur
            }
            for (i in 0 until cur.childCount) {
                cur.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun awaitGesture(start: ((Boolean) -> Unit) -> Unit): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        start { success ->
            ok = success
            latch.countDown()
        }
        return latch.await(5, TimeUnit.SECONDS) && ok
    }

    private fun parseRelativePoint(value: String): Pair<Int, Int>? {
        val parts = value.trim().removeSurrounding("[", "]").split(",").map { it.trim() }
        if (parts.size < 2) return null
        val relX = parts[0].toIntOrNull() ?: return null
        val relY = parts[1].toIntOrNull() ?: return null
        return relX to relY
    }

    private fun toAbsPoint(relX: Int, relY: Int): Pair<Float, Float> {
        val dm = context.resources.displayMetrics
        // AutoGLM 坐标通常按 0~1000 归一化（和 Operit 保持一致）
        val x = (relX.coerceIn(0, 1000) / 1000f) * dm.widthPixels
        val y = (relY.coerceIn(0, 1000) / 1000f) * dm.heightPixels
        return x to y
    }

    private fun parseSeconds(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        val num = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(v)?.groupValues?.getOrNull(1) ?: return null
        return num.toDoubleOrNull()
    }
}
