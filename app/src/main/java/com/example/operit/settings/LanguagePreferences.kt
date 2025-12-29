package com.example.operit.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class LanguagePreferences private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLanguageTag(): String = prefs.getString(KEY_LANGUAGE_TAG, TAG_ZH_CN) ?: TAG_ZH_CN

    fun setLanguageTag(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE_TAG, tag).apply()
    }

    fun apply() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(getLanguageTag()))
    }

    companion object {
        private const val PREFS_NAME = "language_prefs"
        private const val KEY_LANGUAGE_TAG = "language_tag"

        const val TAG_ZH_CN = "zh-CN"
        const val TAG_EN = "en"

        fun get(context: Context): LanguagePreferences = LanguagePreferences(context.applicationContext)

        fun applySavedLocales(context: Context) {
            get(context).apply()
        }
    }
}

