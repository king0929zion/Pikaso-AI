package com.example.operit.ai

import android.content.Context

class AiPreferences private constructor(private val context: Context) {
    private val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun load(): AiSettings {
        return load(PROFILE_CHAT)
    }

    fun load(profile: String): AiSettings {
        val provider = AiProvider.entries.firstOrNull { it.name == sp.getString(key(profile, KEY_PROVIDER), null) }
            ?: AiProvider.ZHIPU
        val endpoint = sp.getString(key(profile, KEY_ENDPOINT), null) ?: provider.defaultEndpoint
        val apiKey = sp.getString(key(profile, KEY_API_KEY), null) ?: ""
        val model = sp.getString(key(profile, KEY_MODEL), null) ?: provider.defaultModel
        val temperature = sp.getFloat(key(profile, KEY_TEMPERATURE), 0.7f)
        val topP = sp.getFloat(key(profile, KEY_TOP_P), 1.0f)
        val maxTokens = sp.getInt(key(profile, KEY_MAX_TOKENS), 4096)

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

        fun get(context: Context): AiPreferences = AiPreferences(context.applicationContext)
    }
}
