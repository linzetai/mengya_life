package com.mengyalife.app.data.weekly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mengyalife.app.MainActivity
import com.mengyalife.app.R

object WeeklyReviewRouter {
    private val _requests = kotlinx.coroutines.flow.MutableStateFlow(false)
    val requests: kotlinx.coroutines.flow.StateFlow<Boolean> = _requests
    fun requestOpen() { _requests.value = true }
    fun consume() { _requests.value = false }
}

class WeeklyReviewNotifier(private val context: Context) {
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyReady(periodKey: String): Boolean {
        if (!canPost()) return false
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_WEEKLY_REVIEW, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("每周成长回顾")
            .setContentText("本周回顾已生成，点开看看；内容只在本机整理。")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "每周成长回顾",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "每周一次本地回顾提醒；不包含日记正文"
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "weekly_review"
        const val NOTIFICATION_ID = 3001
        const val EXTRA_WEEKLY_REVIEW = "open_weekly_review"
    }
}
