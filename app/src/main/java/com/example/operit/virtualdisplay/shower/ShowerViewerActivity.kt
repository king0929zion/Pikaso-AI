package com.example.operit.virtualdisplay.shower

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerServerManager
import com.example.operit.R
import com.example.operit.autoglm.runtime.AutoGlmVirtualScreen
import com.example.operit.autoglm.runtime.AutoGlmUiStatus
import com.example.operit.logging.AppLog
import com.example.operit.shizuku.ShizukuScreencap
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShowerViewerActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var btnCreate: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private var chatSessionId: String? = null

    private val updateButtonRunnable =
        object : Runnable {
            override fun run() {
                val label = AutoGlmUiStatus.getButtonLabel(defaultLabel = "没有画面？点此创建/重连")
                btnCreate.text = label
                handler.postDelayed(this, 500)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shower_viewer)

        chatSessionId = intent?.getStringExtra(EXTRA_CHAT_SESSION_ID)?.trim()?.takeIf { it.isNotBlank() }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_info -> {
                    showInfoDialog()
                    true
                }
                else -> false
            }
        }

        btnCreate = findViewById(R.id.btnCreateIfNeeded)

        btnCreate.setOnClickListener {
            if (!ShizukuScreencap.isReady()) {
                Toast.makeText(this, "请先授予 Shizuku 权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scope.launch(Dispatchers.Main) {
                Toast.makeText(this@ShowerViewerActivity, "正在启动/重连虚拟屏幕…", Toast.LENGTH_SHORT).show()
                val ok =
                    runCatching {
                        val started = ShowerServerManager.ensureServerStarted(this@ShowerViewerActivity)
                        if (!started) return@runCatching false

                        val dm: DisplayMetrics = resources.displayMetrics
                        // 注意：ensureDisplay 会 destroyDisplay 后重建；仅在“没有画面”时手动点击使用
                        ShowerController.ensureDisplay(
                            context = this@ShowerViewerActivity,
                            width = dm.widthPixels,
                            height = dm.heightPixels,
                            dpi = dm.densityDpi,
                            bitrateKbps = 3000,
                        )
                    }.getOrElse { e ->
                        AppLog.e("ShowerViewer", "ensure failed", e)
                        false
                    }

                if (!ok) {
                    Toast.makeText(this@ShowerViewerActivity, "启动失败，可在聊天里调用 shower_log_read 查看日志", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 若带着 chat_session_id 打开，则切换/重建对应对话的虚拟屏幕，避免新对话复用旧屏幕。
        chatSessionId?.let { id ->
            scope.launch(Dispatchers.IO) {
                AutoGlmVirtualScreen.ensureCreatedForChatSession(
                    context = applicationContext,
                    chatSessionId = id,
                    onLog = null,
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        val id = intent?.getStringExtra(EXTRA_CHAT_SESSION_ID)?.trim()?.takeIf { it.isNotBlank() }
        if (id != null && id != chatSessionId) {
            chatSessionId = id
            scope.launch(Dispatchers.IO) {
                AutoGlmVirtualScreen.ensureCreatedForChatSession(
                    context = applicationContext,
                    chatSessionId = id,
                    onLog = null,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updateButtonRunnable)
        handler.post(updateButtonRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(updateButtonRunnable)
        super.onPause()
    }

    private fun buildStatusText(): String {
        val shizuku = ShizukuScreencap.isReady()
        val binderAlive = runCatching { ShowerBinderRegistry.hasAliveService() }.getOrDefault(false)
        val id = runCatching { ShowerController.getDisplayId() }.getOrNull()
        val size = runCatching { ShowerController.getVideoSize() }.getOrNull()
        val stats = runCatching { ShowerController.getFrameStats() }.getOrNull()
        val action = AutoGlmUiStatus.getStatusLine()

        return buildString {
            appendLine("Shizuku：${if (shizuku) "已授权" else "未授权"}")
            appendLine("Binder：${if (binderAlive) "alive" else "not ready"}")
            appendLine("DisplayId：${id ?: "未创建"}")
            if (size != null) appendLine("Size：${size.first}x${size.second}")
            if (stats != null) {
                val lastAt = stats.lastFrameAtMs?.let { System.currentTimeMillis() - it } ?: -1
                appendLine("Frames：${stats.receivedFrames} buf=${stats.bufferedFrames} cfg=${stats.cachedConfigFrames} last=${if (lastAt >= 0) "${lastAt}ms" else "-"}")
            }
            if (action.isNotBlank()) {
                appendLine()
                appendLine(action)
            }
        }.trimEnd()
    }

    private fun showInfoDialog() {
        val msg = buildStatusText()
        MaterialAlertDialogBuilder(this)
            .setTitle("虚拟屏幕信息")
            .setMessage(msg)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制") { _, _ ->
                val cm = getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("Shower Status", msg))
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        const val EXTRA_CHAT_SESSION_ID = "chat_session_id"
    }
}
