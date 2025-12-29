package com.example.operit

import android.app.Application
import com.example.operit.logging.AppLog
import com.example.operit.settings.LanguagePreferences

class OperitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        LanguagePreferences.applySavedLocales(this)
    }
}

