package com.example.operit.autoglm.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.accessibility.AccessibilityStatus
import com.example.operit.ai.AiPreferences
import com.example.operit.autoglm.AutoGlmOneClickFragment
import com.example.operit.autoglm.runtime.AutoGlmAgentRunner
import com.example.operit.logging.AppLog
import com.example.operit.settings.ui.SettingsPermissionsFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rikka.shizuku.Shizuku

class AutoGLMFragment : Fragment() {
    private var isExecuting = false
    private var runner: AutoGlmAgentRunner? = null
    private var ivPreview: ImageView? = null

    private val logBuilder = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_autoglm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<MaterialButton>(R.id.btnOneClick).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AutoGlmOneClickFragment())
                .addToBackStack(null)
                .commit()
        }

        val etTask = view.findViewById<EditText>(R.id.etTask)
        val btnExecute = view.findViewById<MaterialButton>(R.id.btnExecute)
        val tvLog = view.findViewById<TextView>(R.id.tvLog)
        val scrollLog = view.findViewById<ScrollView>(R.id.scrollLog)
        ivPreview = view.findViewById(R.id.ivPreview)

        val tvMaxSteps = view.findViewById<TextView>(R.id.tvMaxStepsValue)
        val sliderMaxSteps = view.findViewById<Slider>(R.id.sliderMaxSteps)
        tvMaxSteps.text = sliderMaxSteps.value.toInt().coerceIn(1, 100).toString()
        sliderMaxSteps.addOnChangeListener { _, value, _ ->
            tvMaxSteps.text = value.toInt().coerceIn(1, 100).toString()
        }

        btnExecute.setOnClickListener {
            if (isExecuting) {
                cancelExecution("用户取消")
                btnExecute.text = "执行"
                renderLog(tvLog, scrollLog)
                return@setOnClickListener
            }

            val task = etTask.text.toString().trim()
            if (task.isBlank()) return@setOnClickListener
            val maxSteps = sliderMaxSteps.value.toInt().coerceIn(1, 100)

            val ctx = context ?: return@setOnClickListener
            val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)
            if (settings.endpoint.isBlank() || settings.model.isBlank()) {
                appendLog("未配置 UI 控制器模型，请先在“设置 -> AI 配置”中填写 Endpoint/模型，并在 AutoGLM 一键配置中写入 UI_CONTROLLER。")
                renderLog(tvLog, scrollLog)
                Toast.makeText(ctx, "请先完成 AutoGLM 一键配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!AccessibilityStatus.isServiceEnabled(ctx)) {
                appendLog("无障碍服务未开启：请先在“设置 -> 权限配置”中开启无障碍服务。")
                renderLog(tvLog, scrollLog)
                Toast.makeText(ctx, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, SettingsPermissionsFragment())
                    .addToBackStack(null)
                    .commit()
                return@setOnClickListener
            }

            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                appendLog("Shizuku 未授权：AutoGLM 需要通过 Shizuku 截图以支持 autoglm-phone 的多模态推理。")
                renderLog(tvLog, scrollLog)
                Toast.makeText(ctx, "请先授权 Shizuku（设置 -> 权限配置）", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, SettingsPermissionsFragment())
                    .addToBackStack(null)
                    .commit()
                return@setOnClickListener
            }

            startExecution(
                task = task,
                maxSteps = maxSteps,
                onLogChanged = { renderLog(tvLog, scrollLog) },
                onFinish = {
                    isExecuting = false
                    btnExecute.text = "执行"
                },
            )
            isExecuting = true
            btnExecute.text = "取消"
        }

        if (logBuilder.isEmpty()) {
            appendLog("就绪：输入任务并点击“执行”。")
            appendLog("提示：请先完成 AutoGLM 一键配置，并开启无障碍 + Shizuku。")
            renderLog(tvLog, scrollLog)
        }
    }

    private fun startExecution(task: String, maxSteps: Int, onLogChanged: () -> Unit, onFinish: () -> Unit) {
        val ctx = context ?: return
        val settings = AiPreferences.get(ctx).load(AiPreferences.PROFILE_UI_CONTROLLER)
        val safeSteps = maxSteps.coerceIn(1, 100)

        appendLog("==================================================")
        appendLog("Task: $task")
        appendLog("Model: ${settings.model}")
        appendLog("MaxSteps: $safeSteps")
        appendLog("==================================================")
        onLogChanged()

        runner =
            AutoGlmAgentRunner(
                context = ctx,
                settings = settings,
                serviceProvider = { com.example.operit.accessibility.OperitAccessibilityService.instance },
                onLog = { line ->
                    activity?.runOnUiThread {
                        appendLog(line)
                        onLogChanged()
                    }
                },
                onScreenshot = { path ->
                    activity?.runOnUiThread {
                        updatePreview(path)
                    }
                },
            )

        Thread {
            val result = runner?.run(task = task, maxSteps = safeSteps)
            activity?.runOnUiThread {
                result?.exceptionOrNull()?.let { e ->
                    appendLog("执行失败：${e.message ?: e.javaClass.simpleName}")
                    AppLog.e("AutoGLM", "execute failed", e)
                    Toast.makeText(ctx, "执行失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
                onLogChanged()
                onFinish()
            }
        }.start()
    }

    private fun appendLog(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logBuilder.append('[').append(time).append("] ").appendLine(line)
    }

    private fun renderLog(tvLog: TextView, scrollLog: ScrollView) {
        tvLog.text = logBuilder.toString().trimEnd()
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun cancelExecution(reason: String) {
        runner?.cancel(reason)
        appendLog("[Execution Cancelled] $reason")
        isExecuting = false
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
        runner?.cancel("页面销毁")
        ivPreview = null
        super.onDestroyView()
    }
}
