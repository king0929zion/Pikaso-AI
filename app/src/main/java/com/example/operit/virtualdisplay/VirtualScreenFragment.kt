package com.example.operit.virtualdisplay

import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.example.operit.R
import com.example.operit.settings.ui.SettingsPermissionsFragment
import com.example.operit.shizuku.ShizukuScreencap
import com.example.operit.virtualdisplay.shower.ShowerVirtualScreenOverlay
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VirtualScreenFragment : Fragment() {
    private val manager by lazy { VirtualDisplayManager.getInstance(requireContext()) }
    private var presentation: VirtualScreenPresentation? = null

    private lateinit var tvStatusOverlay: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var btnAction: MaterialButton

    @Volatile private var autoRunning: Boolean = false
    @Volatile private var autoThread: Thread? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_virtual_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        tvStatusOverlay = view.findViewById(R.id.tvStatusOverlay)
        ivPreview = view.findViewById(R.id.ivPreview)
        btnAction = view.findViewById(R.id.btnAction)

        view.findViewById<ImageButton>(R.id.btnInfo).setOnClickListener { showInfoDialog() }

        btnAction.setOnClickListener { toggleConnection() }

        refreshUi()
    }

    override fun onDestroyView() {
        stopAutoPreview()
        dismissPresentation()
        super.onDestroyView()
    }

    private fun refreshUi() {
        val shizukuReady = ShizukuScreencap.isReady()
        val binderAlive = runCatching { ShowerBinderRegistry.hasAliveService() }.getOrDefault(false)
        val showerId = runCatching { ShowerController.getDisplayId() }.getOrNull()
        val showing = ShowerVirtualScreenOverlay.isShowing()

        val status = when {
            autoRunning -> "实时预览中"
            showerId != null -> "已连接 (DisplayId=$showerId)"
            shizukuReady && binderAlive -> "就绪"
            shizukuReady -> "Shizuku 已授权"
            else -> "未连接"
        }
        tvStatusOverlay.text = status

        btnAction.text = when {
            autoRunning -> "停止预览"
            showing -> "关闭悬浮窗"
            shizukuReady -> "开始预览"
            else -> "配置权限"
        }
    }

    private fun toggleConnection() {
        val ctx = context ?: return
        val shizukuReady = ShizukuScreencap.isReady()

        if (!shizukuReady) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsPermissionsFragment())
                .addToBackStack(null)
                .commit()
            return
        }

        if (autoRunning) {
            stopAutoPreview()
        } else {
            startAutoPreview()
        }
    }

    private fun showInfoDialog() {
        val ctx = context ?: return

        val overlayGranted = Settings.canDrawOverlays(ctx)
        val shizukuReady = ShizukuScreencap.isReady()
        val binderAlive = runCatching { ShowerBinderRegistry.hasAliveService() }.getOrDefault(false)
        val showerId = runCatching { ShowerController.getDisplayId() }.getOrNull()
        val displayId = manager.getDisplayId()

        val info = buildString {
            appendLine("【系统状态】")
            appendLine("悬浮窗权限: ${if (overlayGranted) "✓ 已开启" else "✗ 未开启"}")
            appendLine("Shizuku: ${if (shizukuReady) "✓ 已授权" else "✗ 未授权"}")
            appendLine("Binder: ${if (binderAlive) "✓ alive" else "✗ not ready"}")
            appendLine()
            appendLine("【虚拟屏幕】")
            appendLine("VirtualDisplay ID: ${displayId ?: "未创建"}")
            appendLine("Shower DisplayId: ${showerId ?: "未创建"}")
            appendLine()
            appendLine("【操作说明】")
            appendLine("• 创建：初始化虚拟屏幕")
            appendLine("• 释放：销毁虚拟屏幕")
            appendLine("• 截图：捕获虚拟屏幕画面")
            appendLine("• 悬浮窗：启动/关闭 Shower 悬浮窗")
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("虚拟屏幕信息")
            .setMessage(info)
            .setPositiveButton("创建虚拟屏幕") { _, _ ->
                val id = manager.ensureVirtualDisplay()
                if (id == null) {
                    Toast.makeText(ctx, "创建失败", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "已创建：id=$id", Toast.LENGTH_SHORT).show()
                    showPresentationIfPossible()
                }
                refreshUi()
            }
            .setNegativeButton("释放虚拟屏幕") { _, _ ->
                dismissPresentation()
                manager.release()
                Toast.makeText(ctx, "已释放", Toast.LENGTH_SHORT).show()
                refreshUi()
            }
            .setNeutralButton("悬浮窗") { _, _ -> toggleShowerOverlay() }
            .show()
    }

    fun updateActionStatus(action: String) {
        activity?.runOnUiThread {
            btnAction.text = action
        }
    }

    private fun startAutoPreview() {
        val ctx = context ?: return
        if (!ShizukuScreencap.isReady()) {
            Toast.makeText(ctx, "Shizuku 未授权或未运行", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }

        if (autoRunning) return
        autoRunning = true
        refreshUi()

        val t = Thread {
            while (autoRunning) {
                val r = ShizukuScreencap.capture(ctx).getOrNull()
                if (r != null) {
                    val bmp = BitmapFactory.decodeFile(r.screenshotFile.absolutePath)
                    activity?.runOnUiThread {
                        if (bmp != null) {
                            ivPreview.setImageBitmap(bmp)
                        }
                        refreshUi()
                    }
                }
                Thread.sleep(1200)
            }
        }.also { it.isDaemon = true }

        autoThread = t
        t.start()
    }

    private fun stopAutoPreview() {
        autoRunning = false
        autoThread = null
        activity?.runOnUiThread { refreshUi() }
    }

    private fun toggleShowerOverlay() {
        val ctx = context ?: return
        val overlayGranted = Settings.canDrawOverlays(ctx)
        val shizukuReady = ShizukuScreencap.isReady()
        if (!overlayGranted || !shizukuReady) {
            Toast.makeText(ctx, "请先授予悬浮窗权限与 Shizuku 权限", Toast.LENGTH_SHORT).show()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsPermissionsFragment())
                .addToBackStack(null)
                .commit()
            return
        }

        if (ShowerVirtualScreenOverlay.isShowing()) {
            ShowerVirtualScreenOverlay.hide()
            refreshUi()
            return
        }

        ShowerVirtualScreenOverlay.show(ctx)
        refreshUi()
    }

    private fun showPresentationIfPossible() {
        if (presentation?.isShowing == true) return
        val display = manager.getDisplay() ?: return
        val ctx = activity ?: return
        runCatching {
            presentation = VirtualScreenPresentation(ctx, display).also { it.show() }
        }
    }

    private fun dismissPresentation() {
        runCatching { presentation?.dismiss() }
        presentation = null
    }
}
