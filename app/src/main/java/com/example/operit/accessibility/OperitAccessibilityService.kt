package com.example.operit.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.operit.logging.AppLog

class OperitAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLog.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 这里不做监听处理；执行链路按需使用 rootInActiveWindow
    }

    override fun onInterrupt() {
        AppLog.w(TAG, "service interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        AppLog.i(TAG, "service destroyed")
        super.onDestroy()
    }

    fun performTap(x: Float, y: Float, onDone: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onDone?.invoke(false)
            return
        }

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val ok =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onDone?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onDone?.invoke(false)
                    }
                },
                null,
            )
        if (!ok) onDone?.invoke(false)
    }

    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        onDone: ((Boolean) -> Unit)? = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onDone?.invoke(false)
            return
        }

        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val ok =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onDone?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onDone?.invoke(false)
                    }
                },
                null,
            )
        if (!ok) onDone?.invoke(false)
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val list = root.findAccessibilityNodeInfosByText(text)
        return list.firstOrNull()
    }

    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        var cur = node ?: return false
        while (true) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val parent = cur.parent ?: return false
            cur = parent
        }
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        val target = node ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        private const val TAG = "Accessibility"

        @Volatile
        var instance: OperitAccessibilityService? = null
            private set
    }
}

