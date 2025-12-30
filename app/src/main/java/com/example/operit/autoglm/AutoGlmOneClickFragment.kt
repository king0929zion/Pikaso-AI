package com.example.operit.autoglm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.AiProvider
import com.example.operit.ai.AiSettings
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText

class AutoGlmOneClickFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_autoglm_one_click, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val etApiKey = view.findViewById<TextInputEditText>(R.id.etApiKey)
        val tvEffectiveConfig = view.findViewById<TextView>(R.id.tvEffectiveConfig)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)

        val prefs = AiPreferences.get(requireContext())
        val existing = prefs.load(AiPreferences.PROFILE_UI_CONTROLLER)
        if (existing.provider == AiProvider.ZHIPU && existing.apiKey.isNotBlank()) {
            tvStatus.text = "已检测到现有 AutoGLM 配置（UI 控制模型）：${existing.model}"
        } else {
            tvStatus.text = "未配置 AutoGLM（UI 控制模型）"
        }

        val endpoint = AiProvider.ZHIPU.defaultEndpoint
        val model = "autoglm-phone"
        tvEffectiveConfig.text = "将自动配置：智谱 / $model\nEndpoint：$endpoint"

        view.findViewById<View>(R.id.btnOpenApiKeyPage).setOnClickListener {
            val url = "https://open.bigmodel.cn/usercenter/apikeys"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "无法打开链接：$url", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btnConfigure).setOnClickListener {
            val apiKey = etApiKey.text?.toString().orEmpty().trim()
            if (apiKey.isBlank()) {
                tvStatus.text = "请先填写智谱 API Key"
                return@setOnClickListener
            }

            // AutoGLM 模型配置独立于主对话模型（写入 UI_CONTROLLER profile）
            prefs.save(
                AiPreferences.PROFILE_UI_CONTROLLER,
                AiSettings(
                    provider = AiProvider.ZHIPU,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    model = model,
                    temperature = 0.0f,
                    topP = 0.8f,
                    maxTokens = 4096,
                ),
            )

            tvStatus.text =
                """
                已完成 AutoGLM 一键配置 ✅
                - UI 控制模型：$model
                - Endpoint：$endpoint

                现在可以返回工具箱使用“AutoGLM 连接测试 / 执行器”。
                """.trimIndent()
        }
    }
}
