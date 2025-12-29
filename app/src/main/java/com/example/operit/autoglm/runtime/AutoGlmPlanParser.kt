package com.example.operit.autoglm.runtime

import org.json.JSONArray
import org.json.JSONObject

object AutoGlmPlanParser {
    fun parseFromModelOutput(text: String): AutoGlmPlan {
        val obj = JSONObject(extractJsonObject(text))
        val steps = parseSteps(obj)
        return AutoGlmPlan(raw = obj, steps = steps)
    }

    private fun parseSteps(obj: JSONObject): List<AutoGlmStep> {
        val arr = obj.optJSONArray("steps") ?: JSONArray()
        val list = ArrayList<AutoGlmStep>(arr.length())
        for (i in 0 until arr.length()) {
            val stepObj = arr.optJSONObject(i) ?: continue
            val type = stepObj.optString("type").trim()
            if (type.isBlank()) continue
            list.add(AutoGlmStep(type = type, args = stepObj))
        }
        return list
    }

    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        // 兜底：给 JSONObject 抛出更明确的错误
        return trimmed
    }
}

