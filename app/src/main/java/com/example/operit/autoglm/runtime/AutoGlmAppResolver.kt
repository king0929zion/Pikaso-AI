package com.example.operit.autoglm.runtime

import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

object AutoGlmAppResolver {
    private val builtIn = AutoGlmKnownApps.appPackages
    private val builtInLower = AutoGlmKnownApps.appPackagesLower

    fun resolvePackageName(context: Context, app: String): String {
        val trimmed = app.trim()
        if (trimmed.isBlank()) return ""

        if (trimmed.contains('.') && looksLikePackage(trimmed)) return trimmed

        builtIn[trimmed]?.let { return it }
        builtInLower[trimmed.lowercase(Locale.ROOT)]?.let { return it }

        val normalizedSpaces = trimmed.replace(Regex("\\s+"), " ")
        if (normalizedSpaces != trimmed) {
            builtIn[normalizedSpaces]?.let { return it }
            builtInLower[normalizedSpaces.lowercase(Locale.ROOT)]?.let { return it }
        }

        return scanInstalledApps(context, trimmed) ?: ""
    }

    private fun scanInstalledApps(context: Context, query: String): String? {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.MATCH_ALL)
        val q = query.lowercase(Locale.getDefault())

        // 先精确匹配 label
        packages.forEach { pi ->
            val label = runCatching { pm.getApplicationLabel(pi.applicationInfo).toString() }.getOrNull() ?: return@forEach
            if (label.equals(query, ignoreCase = true)) return pi.packageName
        }

        // 再包含匹配
        packages.forEach { pi ->
            val label = runCatching { pm.getApplicationLabel(pi.applicationInfo).toString() }.getOrNull() ?: return@forEach
            if (label.lowercase(Locale.getDefault()).contains(q)) return pi.packageName
        }

        return null
    }

    private fun looksLikePackage(value: String): Boolean {
        val v = value.trim()
        if (v.count { it == '.' } < 1) return false
        // 允许大小写字母（部分系统/历史包名存在大写），仅排除明显不合法字符
        return v.all { it.isLetterOrDigit() || it == '.' || it == '_' }
    }
}

