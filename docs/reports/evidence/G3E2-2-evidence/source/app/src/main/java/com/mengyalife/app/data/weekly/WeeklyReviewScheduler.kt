package com.mengyalife.app.data.weekly

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 唯一周期任务。WorkManager 不承诺精确星期几/几点；
 * 周期 7 天、flex 1 天，遵守系统后台限制。
 */
class WeeklyReviewScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(7, TimeUnit.DAYS, 1, TimeUnit.DAYS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_NAME)
    }

    /** QA/手工测试：立即跑一次 Worker（仍会检查开关与通知权限）。 */
    fun runNowForQa() {
        workManager.enqueueUniqueWork(
            "weekly_review_qa_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WeeklyReviewWorker>().addTag(TAG).build(),
        )
    }

    suspend fun sync(preferences: WeeklyReviewPreferences) {
        if (preferences.current()) schedule() else cancel()
    }

    companion object {
        const val UNIQUE_NAME = "weekly_review_periodic"
        const val TAG = "weekly_review"
    }
}
