package com.example.operit.autoglm.runtime

import java.util.Locale

object AutoGlmUiStatus {
    private const val STALE_MS = 12_000L

    @Volatile private var lastLabel: String = ""
    @Volatile private var lastAtMs: Long = 0L

    fun onAction(actionName: String, args: Map<String, String>) {
        val label =
            when (actionName) {
                "Tap" -> "AI：点击"
                "Swipe" -> "AI：滑动"
                "Long Press" -> "AI：长按"
                "Double Tap" -> "AI：双击"
                "Back" -> "AI：返回"
                "Home" -> "AI：返回桌面"
                "Launch" -> {
                    val app = args["app"]?.trim().orEmpty()
                    if (app.isNotBlank()) "AI：打开「$app」" else "AI：打开应用"
                }
                "Type", "Type_Name" -> "AI：输入"
                "Wait" -> "AI：等待"
                else -> {
                    val safe = actionName.trim()
                    if (safe.isBlank()) "" else "AI：${safe.lowercase(Locale.getDefault())}"
                }
            }
        if (label.isBlank()) return
        lastLabel = label
        lastAtMs = System.currentTimeMillis()
    }

    fun onActionResult(ok: Boolean, actionName: String) {
        if (!ok) {
            lastLabel = "AI：操作失败（$actionName）"
            lastAtMs = System.currentTimeMillis()
        }
    }

    fun getButtonLabel(defaultLabel: String): String {
        val now = System.currentTimeMillis()
        val label = lastLabel
        val at = lastAtMs
        if (label.isBlank() || at <= 0L || now - at > STALE_MS) return defaultLabel
        return label
    }

    fun getStatusLine(): String {
        val now = System.currentTimeMillis()
        val label = lastLabel
        val at = lastAtMs
        if (label.isBlank() || at <= 0L || now - at > STALE_MS) return ""
        val age = (now - at).coerceAtLeast(0L)
        return "当前动作：$label（${age}ms 前）"
    }
}

