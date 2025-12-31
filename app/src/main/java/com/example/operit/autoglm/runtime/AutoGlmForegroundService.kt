package com.example.operit.autoglm.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.operit.MainActivity
import com.example.operit.R
import com.example.operit.logging.AppLog

class AutoGlmForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }.onFailure { e ->
            AppLog.e(TAG, "startForeground failed", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val pi =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0),
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_robot_2)
            .setContentTitle("AutoGLM 执行中")
            .setContentText("任务正在后台运行，切到其它应用也会继续执行")
            .setOngoing(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val exists = nm.getNotificationChannel(CHANNEL_ID) != null
        if (exists) return
        val channel =
            NotificationChannel(CHANNEL_ID, "AutoGLM", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持 AutoGLM 在后台持续执行"
                setShowBadge(false)
            }
        nm.createNotificationChannel(channel)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val TAG = "AutoGlmFGS"
        private const val CHANNEL_ID = "autoglm_runner"
        private const val NOTIFICATION_ID = 10021
        private const val ACTION_START = "com.example.operit.action.AUTOGLM_FGS_START"

        fun setRunning(context: Context, running: Boolean) {
            val ctx = context.applicationContext
            val intent = Intent(ctx, AutoGlmForegroundService::class.java)
            if (running) {
                intent.action = ACTION_START
                runCatching { ContextCompat.startForegroundService(ctx, intent) }.onFailure { e ->
                    AppLog.e(TAG, "startForegroundService failed", e)
                }
            } else {
                runCatching { ctx.stopService(intent) }
            }
        }
    }
}
