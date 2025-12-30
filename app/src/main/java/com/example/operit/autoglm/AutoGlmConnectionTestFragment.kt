package com.example.operit.autoglm

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.SettingsPermissionsFragment
import com.example.operit.accessibility.AccessibilityStatus
import com.example.operit.ai.AiPreferences
import com.example.operit.autoglm.agent.AutoGlmAgentParser
import com.example.operit.autoglm.agent.AutoGlmAgentPrompts
import com.example.operit.autoglm.runtime.AutoGlmVisionClient
import com.example.operit.shizuku.ShizukuScreencap
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoGlmConnectionTestFragment : Fragment() {
    private val logBuilder = StringBuilder()
    private var isTesting = false
    private var ivPreview: ImageView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_autoglm_connection_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener { parentFragmentManager.popBackStack() }

        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val tvLog = view.findViewById<TextView>(R.id.tvLog)
        val scrollLog = view.findViewById<ScrollView>(R.id.scrollLog)
        val btnTest = view.findViewById<MaterialButton>(R.id.btnTest)
        ivPreview = view.findViewById(R.id.ivPreview)

        fun refreshStatus() {
            val ctx = context ?: return
            val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)

            val apiKeyMasked =
                settings.apiKey.trim().let {
                    if (it.isBlank()) "未填写"
                    else if (it.length <= 10) "已填写（长度=${it.length}）"
                    else "${it.take(6)}…${it.takeLast(4)}（长度=${it.length}）"
                }

            val a11y = if (AccessibilityStatus.isServiceEnabled(ctx)) "已开启" else "未开启"
            val shizuku = if (ShizukuScreencap.isReady()) "已授权" else "未授权"

            tvStatus.text =
                buildString {
                    appendLine("UI 控制模型（AutoGLM）配置：")
                    appendLine("Endpoint：${settings.endpoint}")
                    appendLine("Model：${settings.model}")
                    appendLine("API Key：$apiKeyMasked")
                    appendLine()
                    appendLine("权限状态：")
                    appendLine("无障碍：$a11y")
                    appendLine("Shizuku：$shizuku")
                }.trimEnd()
        }

        btnTest.setOnClickListener {
            if (isTesting) return@setOnClickListener
            val ctx = context ?: return@setOnClickListener

            val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)
            if (settings.endpoint.isBlank() || settings.model.isBlank() || settings.apiKey.isBlank()) {
                Toast.makeText(ctx, "请先在设置里完成 AutoGLM（UI 控制模型）配置", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, com.example.operit.SettingsAiFragment())
                    .addToBackStack(null)
                    .commit()
                return@setOnClickListener
            }
            if (!AccessibilityStatus.isServiceEnabled(ctx) || !ShizukuScreencap.isReady()) {
                Toast.makeText(ctx, "请先授予无障碍与 Shizuku 权限", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, SettingsPermissionsFragment())
                    .addToBackStack(null)
                    .commit()
                return@setOnClickListener
            }

            isTesting = true
            btnTest.isEnabled = false
            btnTest.text = "测试中..."
            appendLog("开始测试：将截图并调用 autoglm-phone（SSE）")
            renderLog(tvLog, scrollLog)

            Thread {
                val result =
                    runCatching {
                        val capture =
                            ShizukuScreencap.capture(
                                context = ctx,
                                mode = ShizukuScreencap.CaptureMode.AUTOGLM_PNG,
                            ).getOrThrow()

                        activity?.runOnUiThread {
                            updatePreview(capture.screenshotFile.absolutePath)
                        }

                        val systemPrompt = AutoGlmAgentPrompts.buildUiAutomationSystemPrompt()
                        val userText =
                            """
                            连接测试：不需要执行任何手机操作。
                            请直接输出：finish(message="OK")
                            """.trimIndent()

                        val reply =
                            AutoGlmVisionClient().chatOnce(
                                settings = settings,
                                systemPrompt = systemPrompt,
                                userText = userText,
                                imageDataUrl = capture.dataUrl,
                            ).getOrThrow()

                        val parsed = AutoGlmAgentParser.parse(reply)
                        Triple(reply, parsed.thinking.orEmpty(), parsed.action?.toString().orEmpty())
                    }

                activity?.runOnUiThread {
                    result.onSuccess { (raw, thinking, action) ->
                        appendLog("模型返回成功")
                        if (thinking.isNotBlank()) appendLog("thinking：$thinking")
                        if (action.isNotBlank()) appendLog("action：$action")
                        appendLog("raw：$raw")
                    }.onFailure { e ->
                        appendLog("测试失败：${e.message ?: e.javaClass.simpleName}")
                    }
                    renderLog(tvLog, scrollLog)
                    isTesting = false
                    btnTest.isEnabled = true
                    btnTest.text = "开始测试"
                    refreshStatus()
                }
            }.start()
        }

        if (logBuilder.isEmpty()) {
            appendLog("提示：此页面用于测试 AutoGLM（UI 控制模型）是否可连通。")
            appendLog("会使用 Shizuku 截图，并调用 endpoint 的 chat/completions。")
        }

        refreshStatus()
        renderLog(tvLog, scrollLog)
    }

    private fun appendLog(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logBuilder.append('[').append(time).append("] ").appendLine(line)
    }

    private fun renderLog(tvLog: TextView, scrollLog: ScrollView) {
        tvLog.text = logBuilder.toString().trimEnd()
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updatePreview(path: String) {
        val iv = ivPreview ?: return
        val filePath = path.trim()
        if (filePath.isBlank()) return

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        val width = bounds.outWidth.coerceAtLeast(1)
        val height = bounds.outHeight.coerceAtLeast(1)

        val reqW = iv.width.takeIf { it > 0 } ?: 1080
        val reqH = iv.height.takeIf { it > 0 } ?: 1920

        var sample = 1
        while (width / sample > reqW * 2 || height / sample > reqH * 2) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        val bmp = BitmapFactory.decodeFile(filePath, opts) ?: return
        iv.setImageBitmap(bmp)
    }

    override fun onDestroyView() {
        ivPreview = null
        super.onDestroyView()
    }
}
