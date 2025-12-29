package com.example.operit.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val TAG = "Pikaso"

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun i(tag: String, msg: String) = log("INFO", tag, msg, null)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log("WARN", tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log("ERROR", tag, msg, tr)
    fun d(tag: String, msg: String) = log("DEBUG", tag, msg, null)

    fun readAll(): String {
        val ctx = appContext ?: return ""
        val file = logFile(ctx)
        return if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    fun clear() {
        val ctx = appContext ?: return
        val file = logFile(ctx)
        if (file.exists()) file.delete()
    }

    private fun log(level: String, tag: String, msg: String, tr: Throwable?) {
        val line = formatLine(level, tag, msg, tr)
        when (level) {
            "ERROR" -> Log.e(TAG, "[$tag] $msg", tr)
            "WARN" -> Log.w(TAG, "[$tag] $msg", tr)
            else -> Log.i(TAG, "[$tag] $msg")
        }
        append(line)
    }

    private fun formatLine(level: String, tag: String, msg: String, tr: Throwable?): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val base = "[$time] [$level] [$tag] $msg"
        return if (tr == null) base else base + "\n" + Log.getStackTraceString(tr)
    }

    private fun append(line: String) {
        val ctx = appContext ?: return
        val file = logFile(ctx)
        file.parentFile?.mkdirs()
        synchronized(this) {
            file.appendText(line + "\n", Charsets.UTF_8)
        }
    }

    private fun logFile(ctx: Context): File {
        return File(ctx.filesDir, "logs/app.log")
    }
}

