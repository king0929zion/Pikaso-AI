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

