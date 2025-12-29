package com.example.operit.ai

enum class AiProvider(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModel: String,
) {
    OPENAI(
        displayName = "OpenAI",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
    ),
    DEEPSEEK(
        displayName = "DeepSeek（OpenAI 兼容）",
        defaultEndpoint = "https://api.deepseek.com/v1/chat/completions",
        defaultModel = "deepseek-chat",
    ),
    ZHIPU(
        displayName = "智谱（OpenAI 兼容）",
        defaultEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        defaultModel = "glm-4-flash",
    ),
    CUSTOM(
        displayName = "自定义（OpenAI 兼容）",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
    ),
}
