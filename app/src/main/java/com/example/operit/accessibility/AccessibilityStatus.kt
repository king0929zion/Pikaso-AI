package com.example.operit.accessibility

import android.content.Context
import android.provider.Settings

object AccessibilityStatus {
    fun isServiceEnabled(context: Context): Boolean {
        val enabled =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

        val expected = context.packageName + "/" + OperitAccessibilityService::class.java.name
        return enabled.contains(expected)
    }
}

