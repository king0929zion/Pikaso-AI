package com.example.operit

import android.app.Application
import com.example.operit.logging.AppLog
import com.example.operit.settings.LanguagePreferences
import com.google.android.material.color.DynamicColors

class OperitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        AppLog.init(this)
        LanguagePreferences.applySavedLocales(this)
    }
}
