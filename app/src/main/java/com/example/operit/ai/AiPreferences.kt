package com.example.operit.ai

import android.content.Context

class AiPreferences private constructor(private val context: Context) {
    private val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings {
        val provider = AiProvider.entries.firstOrNull { it.name == sp.getString(KEY_PROVIDER, null) }
            ?: AiProvider.ZHIPU
        val endpoint = sp.getString(KEY_ENDPOINT, null) ?: provider.defaultEndpoint
        val apiKey = sp.getString(KEY_API_KEY, null) ?: ""
        val model = sp.getString(KEY_MODEL, null) ?: provider.defaultModel
        val temperature = sp.getFloat(KEY_TEMPERATURE, 0.7f)
        val topP = sp.getFloat(KEY_TOP_P, 1.0f)
        val maxTokens = sp.getInt(KEY_MAX_TOKENS, 4096)

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
        sp.edit()
            .putString(KEY_PROVIDER, settings.provider.name)
            .putString(KEY_ENDPOINT, settings.endpoint)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_MODEL, settings.model)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putFloat(KEY_TOP_P, settings.topP)
            .putInt(KEY_MAX_TOKENS, settings.maxTokens)
            .apply()
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

        fun get(context: Context): AiPreferences = AiPreferences(context.applicationContext)
    }
}

