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
        return try {
            // Hold shared read/write barrier through DB writes and notification side effects.
            // Restore obtains the exclusive barrier, so no worker can pass its initial check
            // and notify after Gate is armed.
            app.container.restoreWriteBarrier.withWrite { runWorker(app) }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun runWorker(app: MengyaApplication): Result {
        if (app.container.restoreCoordinator.isGateActive()) return Result.retry()
        val preferences = app.container.weeklyReviewPreferences
        if (!preferences.current()) return Result.success()
        return try {
            val review = app.container.weeklyReviewService.generateIfNeeded()
            if (app.container.weeklyReviewPreferences.aiEnabled.first()) {
                app.container.weeklyAiService.generateIfAllowed(autoRequest = true)
            }
            if (app.container.restoreCoordinator.isGateActive()) return Result.retry()
            val dao = app.container.databaseWeeklyReviewDao
            val stored = dao.findByPeriod(review.periodKey)
            val notifier = app.container.weeklyReviewNotifier
            if (stored?.notifiedAt == null && notifier.canPost()) {
                val claimAt = System.currentTimeMillis()
                if (dao.tryClaimNotification(review.periodKey, claimAt) == 1) {
                    try {
                        if (!notifier.notifyReady(review.periodKey)) dao.releaseNotification(review.periodKey, claimAt)
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
