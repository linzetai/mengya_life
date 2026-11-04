package com.mengyalife.app.data.weekly

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mengyalife.app.MengyaApplication

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
            val stored = app.container.databaseWeeklyReviewDao.findByPeriod(review.periodKey)
            if (stored?.notifiedAt == null) {
                if (app.container.weeklyReviewNotifier.notifyReady(review.periodKey)) {
                    app.container.databaseWeeklyReviewDao.markNotified(review.periodKey, System.currentTimeMillis())
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
