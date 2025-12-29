package com.example.operit.autoglm.runtime

import org.json.JSONObject

data class AutoGlmPlan(
    val raw: JSONObject,
    val steps: List<AutoGlmStep>,
)

data class AutoGlmStep(
    val type: String,
    val args: JSONObject,
)

