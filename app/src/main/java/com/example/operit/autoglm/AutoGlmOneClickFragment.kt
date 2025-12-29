package com.example.operit.autoglm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.ai.AiPreferences
import com.example.operit.ai.AiProvider
import com.example.operit.ai.AiSettings
import com.google.android.material.button.MaterialButton

class AutoGlmOneClickFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_autoglm_one_click, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val etApiKey = view.findViewById<EditText>(R.id.etApiKey)
        val etEndpoint = view.findViewById<EditText>(R.id.etEndpoint)
        val etModel = view.findViewById<EditText>(R.id.etModel)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)

        val prefs = AiPreferences.get(requireContext())
        val existing = prefs.load(AiPreferences.PROFILE_UI_CONTROLLER)
        etEndpoint.setText(existing.endpoint)
        etModel.setText(existing.model)

        view.findViewById<MaterialButton>(R.id.btnOpenApiKeyPage).setOnClickListener {
            val url = "https://open.bigmodel.cn/usercenter/apikeys"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开链接：$url", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<MaterialButton>(R.id.btnConfigure).setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            val endpointInput = etEndpoint.text.toString().trim()
            val modelInput = etModel.text.toString().trim()

            if (apiKey.isBlank()) {
                tvStatus.text = "请先填写智谱 API Key。"
                return@setOnClickListener
            }

            val endpoint = endpointInput.ifBlank { AiProvider.ZHIPU.defaultEndpoint }
            val model = modelInput.ifBlank { "autoglm-phone" }

            val provider = if (endpoint == AiProvider.ZHIPU.defaultEndpoint) AiProvider.ZHIPU else AiProvider.CUSTOM

            prefs.save(
                AiPreferences.PROFILE_UI_CONTROLLER,
                AiSettings(
                    provider = provider,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    model = model,
                    temperature = 0.0f,
                    topP = 0.85f,
                    maxTokens = 4096,
                ),
            )

            tvStatus.text =
                "已完成 AutoGLM 一键配置：\n- 已写入 UI 控制器模型配置（$model）\n- Endpoint：$endpoint\n\n现在可以回到工具箱使用“AutoGLM 执行器”。"
        }
    }
}

