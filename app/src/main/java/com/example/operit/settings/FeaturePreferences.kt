package com.example.operit.settings

import android.content.Context

class FeaturePreferences private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoScreenshotAnalysisEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_SCREENSHOT_ANALYSIS, true)

    fun setAutoScreenshotAnalysisEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCREENSHOT_ANALYSIS, enabled).apply()
    }

    fun isBackgroundRunEnabled(): Boolean =
        prefs.getBoolean(KEY_BACKGROUND_RUN, true)

    fun setBackgroundRunEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_RUN, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "feature_prefs"
        private const val KEY_AUTO_SCREENSHOT_ANALYSIS = "auto_screenshot_analysis"
        private const val KEY_BACKGROUND_RUN = "background_run"

        fun get(context: Context): FeaturePreferences = FeaturePreferences(context.applicationContext)
    }
}

