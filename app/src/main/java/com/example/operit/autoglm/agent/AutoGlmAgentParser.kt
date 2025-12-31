package com.example.operit.autoglm.agent

object AutoGlmAgentParser {
    private val thinkRegex = Regex("""<think>(.*?)</think>""", RegexOption.DOT_MATCHES_ALL)
    private val answerRegex = Regex("""<answer>(.*?)</answer>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(text: String): AutoGlmAgentResponse {
        val thinking = thinkRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        val answerRaw = answerRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        val fallback = answerRaw ?: extractActionCall(text)
        val action = fallback?.let { parseAction(it) }
        return AutoGlmAgentResponse(thinking = thinking, answerRaw = fallback, action = action)
    }

    fun parseAction(answer: String): AutoGlmAgentAction? {
        val trimmed = answer.trim()
        if (trimmed.startsWith("finish(")) {
            val args = parseArgs(trimmed.removePrefix("finish").trim())
            val message = args["message"]?.trim().orEmpty()
            return AutoGlmAgentAction.Finish(message = if (message.isNotBlank()) message else "已完成")
        }
        if (trimmed.startsWith("interrupt(")) {
            val args = parseArgs(trimmed.removePrefix("interrupt").trim())
            val reason = args["message"] ?: args["reason"] ?: "中断"
            return AutoGlmAgentAction.Interrupt(reason = reason)
        }
        if (trimmed.startsWith("do(")) {
            val args = parseArgs(trimmed.removePrefix("do").trim())
            val action = args["action"]?.trim().orEmpty()
            if (action.isBlank()) return null
            return AutoGlmAgentAction.Do(action = action, args = args - "action")
        }
        return null
    }

    /**
     * Parse do(...) or finish(...) argument list into a map.
     *
     * Supports:
     * - key="value"
     * - key=[x,y] (kept as raw string "[x,y]")
     */
    private fun parseArgs(parenChunk: String): Map<String, String> {
        val inside = parenChunk.trim().removePrefix("(").removeSuffix(")")
        if (inside.isBlank()) return emptyMap()

        val parts = splitTopLevelByComma(inside)
        val out = LinkedHashMap<String, String>(parts.size)
        for (raw in parts) {
            val kv = raw.trim()
            if (kv.isBlank()) continue
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            val key = kv.substring(0, eq).trim()
            val valueRaw = kv.substring(eq + 1).trim()
            val value = unquote(valueRaw)
            if (key.isNotBlank()) out[key] = value
        }
        return out
    }

    private fun splitTopLevelByComma(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var bracketDepth = 0

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when (c) {
                '"' -> {
                    inQuotes = !inQuotes
                    sb.append(c)
                }
                '[' -> {
                    bracketDepth++
                    sb.append(c)
                }
                ']' -> {
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                    sb.append(c)
                }
                ',' -> {
                    if (!inQuotes && bracketDepth == 0) {
                        out.add(sb.toString())
                        sb.setLength(0)
                    } else {
                        sb.append(c)
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun unquote(value: String): String {
        val v = value.trim()
        if (v.length >= 2 && v.first() == '"' && v.last() == '"') {
            return v.substring(1, v.length - 1)
        }
        return v
    }

    /**
     * 部分模型会忽略 <answer> 标签而直接输出自然语言 + do(...)/finish(...)
     * 这里做一个兜底：从全文中提取第一个可解析的动作调用。
     */
    private fun extractActionCall(text: String): String? {
        val candidates = listOf("do(", "finish(", "interrupt(")
        val start = candidates.mapNotNull { token -> text.indexOf(token).takeIf { it >= 0 } }.minOrNull() ?: return null
        val chunk = text.substring(start)
        return takeCallChunk(chunk)
    }

    private fun takeCallChunk(textFromCallStart: String): String? {
        val trimmed = textFromCallStart.trimStart()
        val prefix =
            when {
                trimmed.startsWith("do(") -> "do"
                trimmed.startsWith("finish(") -> "finish"
                trimmed.startsWith("interrupt(") -> "interrupt"
                else -> return null
            }
        val openIdx = trimmed.indexOf('(')
        if (openIdx < 0) return null

        var inQuotes = false
        var bracketDepth = 0
        var parenDepth = 0
        for (i in openIdx until trimmed.length) {
            val c = trimmed[i]
            when (c) {
                '"' -> inQuotes = !inQuotes
                '[' -> if (!inQuotes) bracketDepth++
                ']' -> if (!inQuotes) bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                '(' -> if (!inQuotes && bracketDepth == 0) parenDepth++
                ')' -> if (!inQuotes && bracketDepth == 0) {
                    parenDepth--
                    if (parenDepth == 0) {
                        return trimmed.substring(0, i + 1).trim()
                    }
                }
            }
        }
        // 没找到闭合括号，尽量返回首行（避免整段自然语言导致 parseArgs 失败）
        return (prefix + "(" + trimmed.substring(openIdx + 1).lineSequence().firstOrNull().orEmpty()).trim()
    }
}
