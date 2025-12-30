package com.example.operit.virtualdisplay.shower

import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerServerManager
import com.example.operit.R
import com.example.operit.logging.AppLog
import com.example.operit.shizuku.ShizukuScreencap
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShowerViewerActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shower_viewer)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnCreate = findViewById<MaterialButton>(R.id.btnCreateIfNeeded)

        fun refreshStatus() {
            val shizuku = ShizukuScreencap.isReady()
            val binderAlive = runCatching { ShowerBinderRegistry.hasAliveService() }.getOrDefault(false)
            val id = runCatching { ShowerController.getDisplayId() }.getOrNull()
            tvStatus.text =
                buildString {
                    appendLine("Shizuku：${if (shizuku) "已授权" else "未授权"}")
                    appendLine("Binder：${if (binderAlive) "alive" else "not ready"}")
                    appendLine("DisplayId：${id ?: "未创建"}")
                    appendLine()
                    appendLine("提示：此页面用于查看 AutoGLM 当前操作的虚拟屏幕画面。")
                }.trimEnd()
        }

        btnCreate.setOnClickListener {
            if (!ShizukuScreencap.isReady()) {
                Toast.makeText(this, "请先授予 Shizuku 权限", Toast.LENGTH_SHORT).show()
                refreshStatus()
                return@setOnClickListener
            }

            scope.launch(Dispatchers.Main) {
                tvStatus.text = "正在启动/重连虚拟屏幕…"
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
                refreshStatus()
            }
        }

        refreshStatus()
    }
}

