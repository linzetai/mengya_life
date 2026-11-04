package com.mengyalife.app.data.weekly

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mengyalife.app.MengyaApplication
import kotlinx.coroutines.flow.first

class WeeklyReviewWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MengyaApplication ?: return Result.success()
        val preferences = app.container.weeklyReviewPreferences
        if (!preferences.current()) return Result.success()
        return try {
            val review = app.container.weeklyReviewService.generateIfNeeded()
            if (app.container.weeklyReviewPreferences.aiEnabled.first()) {
                app.container.weeklyAiService.generateIfAllowed(autoRequest = true)
            }
            val dao = app.container.databaseWeeklyReviewDao
            val stored = dao.findByPeriod(review.periodKey)
            val notifier = app.container.weeklyReviewNotifier
            if (stored?.notifiedAt == null && notifier.canPost()) {
                val claimAt = System.currentTimeMillis()
                // 原子 claim：并发 Worker / QA / 周期任务只有一个能成功
                if (dao.tryClaimNotification(review.periodKey, claimAt) == 1) {
                    try {
                        if (!notifier.notifyReady(review.periodKey)) {
                            dao.releaseNotification(review.periodKey, claimAt)
                        }
                    } catch (e: Exception) {
                        dao.releaseNotification(review.periodKey, claimAt)
                        throw e
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
