package com.example.operit.autoglm.agent

data class AutoGlmAgentResponse(
    val thinking: String?,
    val answerRaw: String?,
    val action: AutoGlmAgentAction?,
)

sealed class AutoGlmAgentAction {
    data class Do(
        val action: String,
        val args: Map<String, String>,
    ) : AutoGlmAgentAction()

    data class Finish(
        val message: String,
    ) : AutoGlmAgentAction()

    data class Interrupt(
        val reason: String,
    ) : AutoGlmAgentAction()
}

