package com.example.operit.ai

import android.content.Context

class AiPreferences private constructor(private val context: Context) {
    private val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings {
        return load(PROFILE_CHAT)
    }

    fun load(profile: String): AiSettings {
        val providerKey = key(profile, KEY_PROVIDER)
        val endpointKey = key(profile, KEY_ENDPOINT)
        val apiKeyKey = key(profile, KEY_API_KEY)
        val modelKey = key(profile, KEY_MODEL)
        val tempKey = key(profile, KEY_TEMPERATURE)
        val topPKey = key(profile, KEY_TOP_P)
        val maxTokensKey = key(profile, KEY_MAX_TOKENS)

        val providerName = sp.getString(providerKey, null)
        val provider =
            AiProvider.entries.firstOrNull { it.name == providerName }
                ?: if (profile == PROFILE_UI_CONTROLLER) AiProvider.ZHIPU else AiProvider.ZHIPU

        val defaultModel =
            if (profile == PROFILE_UI_CONTROLLER) {
                DEFAULT_UI_CONTROLLER_MODEL
            } else {
                provider.defaultModel
            }
        val defaultEndpoint = provider.defaultEndpoint

        val endpoint =
            if (sp.contains(endpointKey)) {
                sp.getString(endpointKey, null).orEmpty().trim().ifBlank { defaultEndpoint }
            } else {
                defaultEndpoint
            }

        val apiKey = sp.getString(apiKeyKey, null) ?: ""

        val model =
            if (sp.contains(modelKey)) {
                sp.getString(modelKey, null).orEmpty().trim().ifBlank { defaultModel }
            } else {
                defaultModel
            }

        val temperature =
            if (sp.contains(tempKey)) {
                sp.getFloat(tempKey, 0.7f)
            } else if (profile == PROFILE_UI_CONTROLLER) {
                DEFAULT_UI_CONTROLLER_TEMPERATURE
            } else {
                0.7f
            }

        val topP =
            if (sp.contains(topPKey)) {
                sp.getFloat(topPKey, 1.0f)
            } else if (profile == PROFILE_UI_CONTROLLER) {
                DEFAULT_UI_CONTROLLER_TOP_P
            } else {
                1.0f
            }

        val maxTokens =
            if (sp.contains(maxTokensKey)) {
                sp.getInt(maxTokensKey, 4096)
            } else if (profile == PROFILE_UI_CONTROLLER) {
                DEFAULT_UI_CONTROLLER_MAX_TOKENS
            } else {
                4096
            }

        return AiSettings(
            provider = provider,
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        )
    }

    fun save(settings: AiSettings) {
        save(PROFILE_CHAT, settings)
    }

    fun save(profile: String, settings: AiSettings) {
        sp.edit()
            .putString(key(profile, KEY_PROVIDER), settings.provider.name)
            .putString(key(profile, KEY_ENDPOINT), settings.endpoint)
            .putString(key(profile, KEY_API_KEY), settings.apiKey)
            .putString(key(profile, KEY_MODEL), settings.model)
            .putFloat(key(profile, KEY_TEMPERATURE), settings.temperature)
            .putFloat(key(profile, KEY_TOP_P), settings.topP)
            .putInt(key(profile, KEY_MAX_TOKENS), settings.maxTokens)
            .apply()
    }

    private fun key(profile: String, baseKey: String): String {
        return if (profile == PROFILE_CHAT) baseKey else "${profile}_$baseKey"
    }

    companion object {
        private const val SP_NAME = "ai_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        private const val KEY_MAX_TOKENS = "max_tokens"

        const val PROFILE_CHAT = "chat"
        const val PROFILE_UI_CONTROLLER = "ui_controller"

        // UI 控制模型（AutoGLM）默认配置：用户只需填智谱 API Key 即可使用
        private const val DEFAULT_UI_CONTROLLER_MODEL = "autoglm-phone"
        private const val DEFAULT_UI_CONTROLLER_TEMPERATURE = 0.0f
        private const val DEFAULT_UI_CONTROLLER_TOP_P = 0.85f
        private const val DEFAULT_UI_CONTROLLER_MAX_TOKENS = 3000

        fun get(context: Context): AiPreferences = AiPreferences(context.applicationContext)
    }
}
