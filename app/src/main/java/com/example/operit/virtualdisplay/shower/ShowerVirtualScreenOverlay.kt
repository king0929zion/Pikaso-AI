package com.example.operit.virtualdisplay.shower

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
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerServerManager
import com.example.operit.R
import com.example.operit.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ShowerVirtualScreenOverlay {
    private const val TAG = "ShowerOverlay"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(context: Context) {
        runOnMain {
            val appContext = context.applicationContext
            if (!Settings.canDrawOverlays(appContext)) {
                Toast.makeText(appContext, "请先授予“悬浮窗”权限", Toast.LENGTH_SHORT).show()
                return@runOnMain
            }

            if (overlayView != null) return@runOnMain

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

            btnClose.setOnClickListener { hide() }
            attachDragToHeader(view, params)

            try {
                wm.addView(view, params)
            } catch (e: Exception) {
                AppLog.e(TAG, "addView failed", e)
                overlayView = null
                layoutParams = null
                windowManager = null
                Toast.makeText(appContext, "悬浮窗创建失败：${e.message}", Toast.LENGTH_SHORT).show()
                return@runOnMain
            }

            scope.launch {
                tvStatus.text = "启动虚拟屏幕中…"

                val started =
                    runCatching { ShowerServerManager.ensureServerStarted(appContext) }
                        .getOrElse { e ->
                            AppLog.e(TAG, "ensureServerStarted failed", e)
                            false
                        }
                if (!started) {
                    tvStatus.text = "启动失败：Shower server 未就绪（可用 shower_log_read 查看详细日志）"
                    return@launch
                }

                val metrics = appContext.resources.displayMetrics
                val ok =
                    runCatching {
                        ShowerController.ensureDisplay(
                            context = appContext,
                            width = metrics.widthPixels,
                            height = metrics.heightPixels,
                            dpi = metrics.densityDpi,
                            bitrateKbps = 3000,
                        )
                    }.getOrElse { e ->
                        AppLog.e(TAG, "ensureDisplay failed", e)
                        false
                    }

                val id = ShowerController.getDisplayId()
                tvStatus.text = if (ok && id != null) "已启动：displayId=$id" else "启动失败：displayId=$id"
            }
        }
    }

    fun hide() {
        runOnMain {
            val wm = windowManager
            val view = overlayView
            overlayView = null
            layoutParams = null
            windowManager = null
            if (wm != null && view != null) {
                runCatching { wm.removeView(view) }
            }
        }
    }

    private fun attachDragToHeader(root: View, params: WindowManager.LayoutParams) {
        val header = root.findViewById<View>(R.id.overlayHeader)
        header.setOnTouchListener(
            object : View.OnTouchListener {
                private var lastX = 0f
                private var lastY = 0f
                private var startX = 0
                private var startY = 0

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val wm = windowManager ?: return false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.rawX
                            lastY = event.rawY
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
                    }
                    return false
                }
            },
        )
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post { runCatching { action() } }
        }
    }
}
