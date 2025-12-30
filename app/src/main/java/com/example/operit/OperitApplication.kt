package com.example.operit

import android.app.Application
import com.ai.assistance.showerclient.ShowerEnvironment
import com.example.operit.logging.AppLog
import com.example.operit.settings.LanguagePreferences
import com.example.operit.virtualdisplay.shower.PikasoShowerShellRunner

class OperitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        LanguagePreferences.applySavedLocales(this)

        // 注入 Shower ShellRunner（用于启动虚拟屏幕 server.jar）
        ShowerEnvironment.shellRunner = PikasoShowerShellRunner(this)
    }
}
