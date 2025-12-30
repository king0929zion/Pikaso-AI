package com.example.operit.ai

import java.net.URL

object EndpointCompleter {
    /**
     * 补全 OpenAI 兼容的 `chat/completions` 端点：
     * - 传入基础 URL（如 `https://api.example.com`）时，默认补全为 `/v1/chat/completions`
     * - 传入 `.../v1` 时，补全为 `.../v1/chat/completions`
     * - 传入 BigModel 基础地址时，自动路由到 `/api/paas/v4/chat/completions`
     *
     * 特殊约定：
     * - 以 `#` 结尾表示“不要补全”，仅去掉末尾 `#`
     */
    fun complete(endpoint: String): String {
        val trimmedEndpoint = endpoint.trim()
        if (trimmedEndpoint.endsWith("#")) return trimmedEndpoint.removeSuffix("#")

        val endpointWithoutSlash = trimmedEndpoint.removeSuffix("/")

        return try {
            val url = URL(trimmedEndpoint)
            val path = url.path.removeSuffix("/")
            val host = url.host.lowercase()

            // BigModel（智谱）兼容：不要走 /v1
            if (host.endsWith("open.bigmodel.cn")) {
                return when {
                    path.isEmpty() || path == "/" || path.equals("/api", ignoreCase = true) ->
                        "${url.protocol}://${url.host}/api/paas/v4/chat/completions"
                    path.startsWith("/v1", ignoreCase = true) ->
                        "${url.protocol}://${url.host}/api/paas/v4/chat/completions"
                    path.endsWith("/api/paas/v4", ignoreCase = true) -> "$endpointWithoutSlash/chat/completions"
                    else -> trimmedEndpoint
                }
            }

            when {
                path.isEmpty() -> "$endpointWithoutSlash/v1/chat/completions"
                path.endsWith("/v1", ignoreCase = true) -> "$endpointWithoutSlash/chat/completions"
                else -> trimmedEndpoint
            }
        } catch (_: Exception) {
            trimmedEndpoint
        }
    }
}
