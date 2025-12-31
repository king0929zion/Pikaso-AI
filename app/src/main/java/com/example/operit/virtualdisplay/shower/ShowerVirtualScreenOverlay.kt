package com.example.operit.virtualdisplay.shower

import android.content.Intent
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.example.operit.R
import com.example.operit.logging.AppLog

object ShowerVirtualScreenOverlay {
    private const val TAG = "ShowerOverlay"

    private val handler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null

    private val statusRefreshRunnable =
        object : Runnable {
            override fun run() {
                val ctx = overlayView?.context?.applicationContext
                if (ctx != null) {
                    refreshStatus(ctx)
                    handler.postDelayed(this, 1000)
                }
            }
        }

    fun isShowing(): Boolean = overlayView != null

    fun show(context: Context) {
        runOnMain {
            val appContext = context.applicationContext
            if (!Settings.canDrawOverlays(appContext)) {
                Toast.makeText(appContext, "请先授予“悬浮窗”权限", Toast.LENGTH_SHORT).show()
                return@runOnMain
            }

            if (overlayView != null) {
                refreshStatus(appContext)
                return@runOnMain
            }

            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            val params =
                WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    type =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_PHONE
                        }
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.TOP or Gravity.START
                    x = 18
                    y = 160
                }
            layoutParams = params

            val view = LayoutInflater.from(appContext).inflate(R.layout.overlay_shower_virtual_screen, null, false)
            overlayView = view

            val tvStatus = view.findViewById<TextView>(R.id.tvOverlayStatus)
            val btnClose = view.findViewById<ImageButton>(R.id.btnOverlayClose)
            statusView = tvStatus

            btnClose.setOnClickListener { hide() }

            val openViewer = { openViewer(appContext) }
            view.findViewById<View>(R.id.overlayCard)?.setOnClickListener { openViewer() }
            attachDragToHeader(view, params, onClick = openViewer)

            try {
                wm.addView(view, params)
            } catch (e: Exception) {
                AppLog.e(TAG, "addView failed", e)
                overlayView = null
                layoutParams = null
                windowManager = null
                statusView = null
                Toast.makeText(appContext, "悬浮窗创建失败：${e.message}", Toast.LENGTH_SHORT).show()
                return@runOnMain
            }

            refreshStatus(appContext)
            handler.removeCallbacks(statusRefreshRunnable)
            handler.postDelayed(statusRefreshRunnable, 1000)
        }
    }

    fun hide() {
        runOnMain {
            handler.removeCallbacks(statusRefreshRunnable)
            val wm = windowManager
            val view = overlayView
            overlayView = null
            layoutParams = null
            windowManager = null
            statusView = null
            if (wm != null && view != null) {
                runCatching { wm.removeView(view) }
            }
        }
    }

    private fun attachDragToHeader(root: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        val header = root.findViewById<View>(R.id.overlayHeader)
        header.setOnTouchListener(
            object : View.OnTouchListener {
                private var lastX = 0f
                private var lastY = 0f
                private var startX = 0
                private var startY = 0
                private var downX = 0f
                private var downY = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val wm = windowManager ?: return false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.rawX
                            lastY = event.rawY
                            downX = event.rawX
                            downY = event.rawY
                            startX = params.x
                            startY = params.y
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - lastX).toInt()
                            val dy = (event.rawY - lastY).toInt()
                            params.x = startX + dx
                            params.y = startY + dy
                            runCatching { wm.updateViewLayout(root, params) }
                            return true
                        }

                        MotionEvent.ACTION_UP -> {
                            val moveX = kotlin.math.abs(event.rawX - downX)
                            val moveY = kotlin.math.abs(event.rawY - downY)
                            if (moveX < 12 && moveY < 12) {
                                onClick()
                            }
                            return true
                        }
                    }
                    return false
                }
            },
        )
    }

    private fun refreshStatus(context: Context) {
        val tv = statusView ?: return
        val binderAlive = runCatching { ShowerBinderRegistry.hasAliveService() }.getOrDefault(false)
        val displayId = runCatching { ShowerController.getDisplayId() }.getOrNull()
        val size = runCatching { ShowerController.getVideoSize() }.getOrNull()
        tv.text =
            buildString {
                append("Binder: ").append(if (binderAlive) "alive" else "not ready")
                append("  DisplayId: ").append(displayId?.toString() ?: "未创建")
                if (size != null) {
                    append("  Size: ").append(size.first).append("x").append(size.second)
                }
                appendLine()
                append("点击打开虚拟屏幕")
            }
    }

    private fun openViewer(context: Context) {
        runCatching {
            val i = Intent(context, ShowerViewerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post { runCatching { action() } }
        }
    }
}
