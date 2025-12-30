package com.example.operit.autoglm.runtime

import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

object AutoGlmAppResolver {
    private val builtIn = mapOf(
        "设置" to "com.android.settings",
        "相机" to "com.android.camera",
        "相册" to "com.android.gallery3d",
        "微信" to "com.tencent.mm",
        "QQ" to "com.tencent.mobileqq",
        "浏览器" to "com.android.browser",
        "Chrome" to "com.android.chrome",
    )

    fun resolvePackageName(context: Context, app: String): String {
        val trimmed = app.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.contains('.') && looksLikePackage(trimmed)) return trimmed

        builtIn[trimmed]?.let { return it }
        builtIn[trimmed.lowercase(Locale.getDefault())]?.let { return it }

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
        // very lightweight check: at least 2 dots and only [a-z0-9._]
        val v = value.trim()
        if (v.count { it == '.' } < 1) return false
        return v.all { it.isLowerCase() || it.isDigit() || it == '.' || it == '_' }
    }
}

