package com.mengyalife.app.data.care

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mengyalife.app.MainActivity
import com.mengyalife.app.MengyaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CareNotification {
    const val CHANNEL = "mengya_care"
    const val EXTRA_CARE_ID = "care_open_id"
    const val ACTION_SILENCE = "com.mengyalife.app.care.SILENCE"
    const val ACTION_LATER = "com.mengyalife.app.care.LATER"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "主动关心", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "MengYa 在合适的时候轻轻问一句"
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun post(context: Context, careId: Long, text: String, lockDetail: Boolean) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, careId.toInt(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_CARE_ID, careId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val silence = PendingIntent.getBroadcast(
            context, careId.toInt() + 1000,
            Intent(context, CareActionReceiver::class.java)
                .setAction(ACTION_SILENCE).putExtra(EXTRA_CARE_ID, careId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val later = PendingIntent.getBroadcast(
            context, careId.toInt() + 2000,
            Intent(context, CareActionReceiver::class.java)
                .setAction(ACTION_LATER).putExtra(EXTRA_CARE_ID, careId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val shownText = if (lockDetail) text else "有件之前聊过的事，想问问你最近怎么样。"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MengYa 想和你聊聊")
            .setContentText(shownText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(shownText))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(if (lockDetail) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_SECRET)
            .addAction(android.R.drawable.ic_menu_recent_history, "稍后", later)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "不再提醒", silence)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(careId.toInt(), notification)
    }

    fun cancel(context: Context, careId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(careId.toInt())
    }
}

/** 通知动作：不启动 UI，只在本地更新反馈/状态。 */
class CareActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(CareNotification.EXTRA_CARE_ID, -1L)
        if (id <= 0) return
        CareNotification.cancel(context, id)
        val app = context.applicationContext as? MengyaApplication ?: return
        if (!app.container.restoreWriteBarrier.isReady()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!app.container.restoreWriteBarrier.isReady()) return@launch
                val repo = app.container.careRepository
                val prefs = app.container.carePreferenceRepository
                when (intent.action) {
                    CareNotification.ACTION_LATER -> {
                        // MVP：记录延期，24 小时内不再考虑该机会。
                        repo.addFeedback(id, "DEFERRED")
                        repo.markStatus(id, "PENDING")
                    }
                    CareNotification.ACTION_SILENCE -> {
                        val opp = repo.find(id)
                        repo.addFeedback(id, "SILENCED")
                        repo.markStatus(id, "DISMISSED")
                        if (opp != null) {
                            val key = opp.planId?.let { "plan:$it" } ?: opp.goalId?.let { "goal:$it" }
                                ?: "topic:${opp.title}"
                            prefs.save(
                                kind = "TOPIC", topicKey = key, topicLabel = opp.title,
                                value = "SILENCED", source = "USER_EXPLICIT",
                                sourceMessageId = null, sourceExcerpt = "通知动作：不再提醒",
                            )
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
