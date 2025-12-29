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

class AutoGlmExecutor(
    private val context: Context,
    private val serviceProvider: () -> OperitAccessibilityService?,
) {
    @Volatile
    var cancelled: Boolean = false

    fun execute(
        plan: AutoGlmPlan,
        onLog: (String) -> Unit,
    ): Result<Unit> {
        return runCatching {
            if (plan.steps.isEmpty()) throw IllegalArgumentException("计划为空：steps 为空")

            onLog("解析到 ${plan.steps.size} 个步骤")
            for ((index, step) in plan.steps.withIndex()) {
                if (cancelled) {
                    onLog("已取消：停止执行")
                    return@runCatching
                }

                val type = step.type
                val args = step.args
                onLog("Step ${index + 1}/${plan.steps.size}: $type ${args.optString(\"note\", \"\").trim()}")

                val service = serviceProvider() ?: throw IllegalStateException("无障碍服务未连接（请先在系统设置中开启）")

                val ok =
                    when (type) {
                        "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        "recents" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)

                        "launch_app" -> {
                            val pkg = args.optString("package").trim()
                            if (pkg.isBlank()) false else launchApp(pkg)
                        }

                        "wait" -> {
                            val ms = args.optLong("ms", 500L).coerceIn(0L, 30_000L)
                            Thread.sleep(ms)
                            true
                        }

                        "tap_text" -> {
                            val text = args.optString("text").trim()
                            val timeoutMs = args.optLong("timeout_ms", 5000L).coerceIn(0L, 30_000L)
                            if (text.isBlank()) false else tapText(service, text, timeoutMs, onLog)
                        }

                        "tap" -> {
                            val x = args.optDouble("x", Double.NaN)
                            val y = args.optDouble("y", Double.NaN)
                            if (!x.isFinite() || !y.isFinite()) {
                                false
                            } else {
                                awaitGesture { cb -> service.performTap(x.toFloat(), y.toFloat(), cb) }
                            }
                        }

                        "swipe" -> {
                            val sx = args.optDouble("start_x", Double.NaN)
                            val sy = args.optDouble("start_y", Double.NaN)
                            val ex = args.optDouble("end_x", Double.NaN)
                            val ey = args.optDouble("end_y", Double.NaN)
                            val duration = args.optLong("duration_ms", 350L).coerceIn(50L, 5000L)
                            if (!sx.isFinite() || !sy.isFinite() || !ex.isFinite() || !ey.isFinite()) {
                                false
                            } else {
                                awaitGesture { cb ->
                                    service.performSwipe(
                                        startX = sx.toFloat(),
                                        startY = sy.toFloat(),
                                        endX = ex.toFloat(),
                                        endY = ey.toFloat(),
                                        durationMs = duration,
                                        onDone = cb,
                                    )
                                }
                            }
                        }

                        "input" -> {
                            val text = args.optString("text")
                            if (text.isBlank()) false else inputText(service, text, onLog)
                        }

                        else -> {
                            onLog("未知步骤类型：$type（已跳过）")
                            true
                        }
                    }

                if (!ok) {
                    val err = "步骤执行失败：$type"
                    onLog(err)
                    throw IllegalStateException(err)
                }

                val afterWait = args.optLong("after_wait_ms", 350L).coerceIn(0L, 5000L)
                if (afterWait > 0) Thread.sleep(afterWait)
            }

            onLog("执行完成 ✅")
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

    private fun tapText(
        service: OperitAccessibilityService,
        text: String,
        timeoutMs: Long,
        onLog: (String) -> Unit,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() <= deadline) {
            if (cancelled) return false
            val node = service.findNodeByText(text)
            if (node != null) {
                val ok = service.clickNode(node)
                if (!ok) onLog("tap_text 找到但点击失败：$text")
                return ok
            }
            Thread.sleep(250)
        }
        onLog("tap_text 超时未找到：$text")
        return false
    }

    private fun inputText(
        service: OperitAccessibilityService,
        text: String,
        onLog: (String) -> Unit,
    ): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val target = findFirstEditable(root)
        if (target == null) {
            onLog("未找到可输入的控件（EditText）")
            return false
        }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args =
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) onLog("输入失败（ACTION_SET_TEXT 返回 false）")
        return ok
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        while (queue.isNotEmpty()) {
            if (cancelled) return null
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
}

