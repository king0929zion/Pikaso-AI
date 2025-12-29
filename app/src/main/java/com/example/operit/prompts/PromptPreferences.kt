package com.example.operit.prompts

import android.content.Context

class PromptPreferences private constructor(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    fun getChatSystemPrompt(): String {
        return sp.getString(KEY_CHAT_SYSTEM_PROMPT, null) ?: DEFAULT_CHAT_SYSTEM_PROMPT
    }

    fun setChatSystemPrompt(prompt: String) {
        sp.edit().putString(KEY_CHAT_SYSTEM_PROMPT, prompt).apply()
    }

    companion object {
        private const val SP_NAME = "prompts"
        private const val KEY_CHAT_SYSTEM_PROMPT = "chat_system_prompt"

        const val DEFAULT_CHAT_SYSTEM_PROMPT =
            "你是 Pikaso，一个可靠、简洁的中文 AI 助手。优先给出可执行的步骤与结论。"

        fun get(context: Context): PromptPreferences = PromptPreferences(context)
    }
}

