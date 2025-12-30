package com.example.operit.ai

import java.net.URL

object EndpointCompleter {
    fun complete(endpoint: String): String {
        val trimmedEndpoint = endpoint.trim()
        if (trimmedEndpoint.endsWith("#")) return trimmedEndpoint.removeSuffix("#")

        val endpointWithoutSlash = trimmedEndpoint.removeSuffix("/")

        return try {
            val url = URL(trimmedEndpoint)
            val path = url.path.removeSuffix("/")
            val host = url.host.lowercase()

            when {
                path.isEmpty() -> "$endpointWithoutSlash/v1/chat/completions"
                path.endsWith("/v1", ignoreCase = true) -> "$endpointWithoutSlash/chat/completions"
                // Zhipu BigModel base url (docs often use base-url=https://open.bigmodel.cn/api/paas/v4)
                path.endsWith("/api/paas/v4", ignoreCase = true) -> "$endpointWithoutSlash/chat/completions"
                // 避免误用 /v1：如果是 BigModel 域名且走了 /v1，则强制路由到 /api/paas/v4
                host.endsWith("open.bigmodel.cn") && path.startsWith("/v1", ignoreCase = true) ->
                    "${url.protocol}://${url.host}/api/paas/v4/chat/completions"
                else -> trimmedEndpoint
            }
        } catch (_: Exception) {
            trimmedEndpoint
        }
    }
}
