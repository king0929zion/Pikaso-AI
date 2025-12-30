package com.example.operit.autoglm.runtime

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.assistance.showerclient.ShellIdentity
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerEnvironment
import com.example.operit.accessibility.OperitAccessibilityService
import com.example.operit.logging.AppLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
        val useVirtual = AutoGlmVirtualScreen.isReady()
        val service = serviceProvider()
        if (!useVirtual && service == null) {
            return ExecResult(false, "无障碍服务未连接（请先在系统设置中开启）")
        }

        return when (actionName) {
            "Home" ->
                if (useVirtual) {
                    ExecResult(showerKey(KeyEvent.KEYCODE_HOME))
                } else {
                    ExecResult(service!!.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
                }

            "Back" ->
                if (useVirtual) {
                    ExecResult(showerKey(KeyEvent.KEYCODE_BACK))
                } else {
                    ExecResult(service!!.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                }

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
                val ok =
                    if (useVirtual) {
                        showerLaunchApp(pkg)
                    } else {
                        launchApp(pkg)
                    }
                ExecResult(ok, if (pkg != app) "Launch: $app -> $pkg" else "Launch: $pkg")
            }

            "Tap" -> {
                val element = args["element"].orEmpty()
                val (nx, ny) = parseRelativePoint(element) ?: return ExecResult(false, "Tap 坐标无效：$element")
                val ok =
                    if (useVirtual) {
                        val (x, y) = toAbsPointPx(nx, ny, preferVirtual = true)
                        showerTap(x, y)
                    } else {
                        val (absX, absY) = toAbsPointReal(nx, ny)
                        awaitGestureWithRetry { cb -> service!!.performTap(absX, absY, cb) }
                    }
                ExecResult(ok, if (!ok) "Tap 失败" else null)
            }

            "Swipe" -> {
                val start = args["start"].orEmpty()
                val end = args["end"].orEmpty()
                val (sx, sy) = parseRelativePoint(start) ?: return ExecResult(false, "Swipe start 无效：$start")
                val (ex, ey) = parseRelativePoint(end) ?: return ExecResult(false, "Swipe end 无效：$end")
                val ok =
                    if (useVirtual) {
                        val (x1, y1) = toAbsPointPx(sx, sy, preferVirtual = true)
                        val (x2, y2) = toAbsPointPx(ex, ey, preferVirtual = true)
                        showerSwipe(x1, y1, x2, y2, 350L)
                    } else {
                        val (absSx, absSy) = toAbsPointReal(sx, sy)
                        val (absEx, absEy) = toAbsPointReal(ex, ey)
                        awaitGestureWithRetry { cb ->
                            service!!.performSwipe(
                                startX = absSx,
                                startY = absSy,
                                endX = absEx,
                                endY = absEy,
                                durationMs = 350L,
                                onDone = cb,
                            )
                        }
                    }
                ExecResult(ok, if (!ok) "Swipe 失败" else null)
            }

            "Long Press" -> {
                val element = args["element"].orEmpty()
                val (x, y) = parseRelativePoint(element) ?: return ExecResult(false, "Long Press 坐标无效：$element")
                val ok =
                    if (useVirtual) {
                        val (px, py) = toAbsPointPx(x, y, preferVirtual = true)
                        showerLongPress(px, py, 650L)
                    } else {
                        val (absX, absY) = toAbsPointReal(x, y)
                        awaitGestureWithRetry { cb -> service!!.performLongPress(absX, absY, cb) }
                    }
                ExecResult(ok, if (!ok) "Long Press 失败" else null)
            }

            "Double Tap" -> {
                val element = args["element"].orEmpty()
                val (x, y) = parseRelativePoint(element) ?: return ExecResult(false, "Double Tap 坐标无效：$element")
                val ok =
                    if (useVirtual) {
                        val (px, py) = toAbsPointPx(x, y, preferVirtual = true)
                        val ok1 = showerTap(px, py)
                        Thread.sleep(80)
                        val ok2 = showerTap(px, py)
                        ok1 && ok2
                    } else {
                        val (absX, absY) = toAbsPointReal(x, y)
                        val ok1 = awaitGestureWithRetry { cb -> service!!.performTap(absX, absY, cb) }
                        Thread.sleep(80)
                        val ok2 = awaitGestureWithRetry { cb -> service!!.performTap(absX, absY, cb) }
                        ok1 && ok2
                    }
                ExecResult(ok, if (!ok) "Double Tap 失败" else null)
            }

            "Type", "Type_Name" -> {
                val text = args["text"].orEmpty()
                if (text.isBlank()) return ExecResult(false, "Type 缺少 text 参数")
                val ok =
                    if (useVirtual) {
                        inputTextViaAdbKeyboard(text, onLog)
                    } else {
                        inputText(service!!, text, onLog)
                    }
                ExecResult(ok, if (!ok) "输入失败" else null)
            }

            // 不再反复要求用户确认：这些动作只做记录，不中断任务（用户可手动取消）
            "Take_over" -> {
                onLog("提示：模型请求接管（登录/验证码等），本次将继续执行并尝试绕过。")
                ExecResult(true)
            }

            "Interact" -> {
                onLog("提示：模型请求用户选择（Interact），本次将继续执行并尝试绕过。")
                ExecResult(true)
            }

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
        val target = findEditableTarget(root)
        if (target == null) {
            onLog("未找到可输入控件（EditText）")
            return false
        }

        // 1) 优先使用 ACTION_SET_TEXT（支持的控件最稳定）
        runCatching { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val setTextArgs = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val okSetText = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs) }.getOrNull() == true
        if (okSetText) return true

        // 2) 兼容不支持 ACTION_SET_TEXT 的控件：尝试剪贴板粘贴
        val cm = context.getSystemService(ClipboardManager::class.java)
        if (cm == null) {
            onLog("输入失败：无法获取 ClipboardManager（ACTION_SET_TEXT 失败且无法走粘贴兜底）")
            return false
        }
        runCatching {
            cm.setPrimaryClip(ClipData.newPlainText("AutoGLM", text))
        }.onFailure { e ->
            onLog("输入失败：写入剪贴板失败：${e.message ?: e.javaClass.simpleName}")
            return false
        }

        runCatching { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        runCatching {
            val current = target.text?.toString().orEmpty()
            val end = current.length.coerceAtLeast(0)
            val selArgs =
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
                }
            target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }
        val okPaste = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrNull() == true
        if (okPaste) return true

        onLog("输入失败（ACTION_SET_TEXT/ACTION_PASTE 均失败）")
        return false
    }

    private fun findEditableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var fallback: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val isEdit = cur.isEditable || cur.className?.toString() == "android.widget.EditText"
            if (isEdit && cur.isFocused) {
                return cur
            }
            if (fallback == null && isEdit) {
                fallback = cur
            }
            for (i in 0 until cur.childCount) {
                cur.getChild(i)?.let { queue.add(it) }
            }
        }
        return fallback
    }

    private fun awaitGesture(start: ((Boolean) -> Unit) -> Unit): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        start { success ->
            ok = success
            latch.countDown()
        }
        return latch.await(7, TimeUnit.SECONDS) && ok
    }

    private fun awaitGestureWithRetry(start: ((Boolean) -> Unit) -> Unit): Boolean {
        val first = awaitGesture(start)
        if (first) return true
        Thread.sleep(250)
        return awaitGesture(start)
    }

    /**
     * 支持两种坐标写法：
     * - [x,y]，x/y 为 0~999（或 0~1000）整数
     * - [0.5,0.7]，x/y 为 0~1 小数（比例）
     *
     * 返回归一化后的坐标(0~1)
     */
    private fun parseRelativePoint(value: String): Pair<Double, Double>? {
        val raw = value.trim().removeSurrounding("[", "]")
        val parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val x = parts[0].toDoubleOrNull() ?: return null
        val y = parts[1].toDoubleOrNull() ?: return null

        val isRatio = x in 0.0..1.0 && y in 0.0..1.0
        if (isRatio) return x to y

        val denom = 999.0
        val nx = (x.coerceIn(0.0, 1000.0).coerceAtMost(999.0)) / denom
        val ny = (y.coerceIn(0.0, 1000.0).coerceAtMost(999.0)) / denom
        return nx to ny
    }

    private fun toAbsPointReal(normX: Double, normY: Double): Pair<Float, Float> {
        val dm = context.resources.displayMetrics
        val x = (normX.coerceIn(0.0, 1.0) * dm.widthPixels).toFloat()
        val y = (normY.coerceIn(0.0, 1.0) * dm.heightPixels).toFloat()
        return x to y
    }

    private fun toAbsPointPx(normX: Double, normY: Double, preferVirtual: Boolean): Pair<Int, Int> {
        val (w, h) =
            if (preferVirtual) {
                AutoGlmVirtualScreen.getSize() ?: run {
                    val dm = context.resources.displayMetrics
                    dm.widthPixels to dm.heightPixels
                }
            } else {
                val dm = context.resources.displayMetrics
                dm.widthPixels to dm.heightPixels
            }
        val x = (normX.coerceIn(0.0, 1.0) * w).toInt().coerceIn(0, (w - 1).coerceAtLeast(0))
        val y = (normY.coerceIn(0.0, 1.0) * h).toInt().coerceIn(0, (h - 1).coerceAtLeast(0))
        return x to y
    }

    private fun showerTap(x: Int, y: Int): Boolean {
        return runBlocking(Dispatchers.IO) { ShowerController.tap(x, y) }
    }

    private fun showerSwipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long): Boolean {
        return runBlocking(Dispatchers.IO) { ShowerController.swipe(sx, sy, ex, ey, durationMs) }
    }

    private fun showerLongPress(x: Int, y: Int, durationMs: Long): Boolean {
        return runBlocking(Dispatchers.IO) {
            val down = ShowerController.touchDown(x, y)
            if (!down) return@runBlocking false
            Thread.sleep(durationMs.coerceIn(200L, 3000L))
            ShowerController.touchUp(x, y)
        }
    }

    private fun showerKey(keyCode: Int): Boolean {
        return runBlocking(Dispatchers.IO) { ShowerController.key(keyCode) }
    }

    private fun showerLaunchApp(packageName: String): Boolean {
        return runBlocking(Dispatchers.IO) { ShowerController.launchApp(packageName) }
    }

    private fun inputTextViaAdbKeyboard(text: String, onLog: (String) -> Unit): Boolean {
        val runner = ShowerEnvironment.shellRunner
        if (runner == null) {
            onLog("输入失败：Shower ShellRunner 未初始化")
            return false
        }

        return runBlocking(Dispatchers.IO) {
            val setIme = runner.run("ime set com.android.adbkeyboard/.AdbIME", ShellIdentity.SHELL)
            if (!setIme.success) {
                onLog("ADB Keyboard 未就绪（请安装并在系统输入法中启用 ADB Keyboard）：${setIme.stderr.ifBlank { setIme.stdout }}")
                return@runBlocking false
            }

            runner.run("am broadcast -a ADB_CLEAR_TEXT", ShellIdentity.SHELL)
            val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val cmd = "am broadcast -a ADB_INPUT_B64 --es msg ${escapeShellArg(b64)}"
            val r = runner.run(cmd, ShellIdentity.SHELL)
            if (!r.success) {
                onLog("输入失败：${r.stderr.ifBlank { r.stdout }}")
            }
            r.success
        }
    }

    private fun escapeShellArg(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun parseSeconds(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        val num = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(v)?.groupValues?.getOrNull(1) ?: return null
        return num.toDoubleOrNull()
    }
}

