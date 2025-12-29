package com.example.operit.ai

data class AiSettings(
    val provider: AiProvider,
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int,
)

