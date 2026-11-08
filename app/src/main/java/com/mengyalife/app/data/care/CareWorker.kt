package com.mengyalife.app.data.care

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import com.mengyalife.app.MengyaApplication
import java.util.concurrent.TimeUnit

/** 后台关心：本地筛候选 → AI 决策 → 通过策略后发系统通知。 */
class CareWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MengyaApplication ?: return Result.failure()
        val settings = CareSettings(app)
        if (!settings.enabled || !settings.notificationsEnabled) return Result.success()
        if (!CareNotification.canPost(app)) return Result.success()
        val container = app.container
        val runner = CareRunner(
            careRepository = container.careRepository,
            planRepository = container.smallPlanRepository,
            dataRepository = container.advisorDataRepository,
            consentRepository = container.aiConsentRepository,
            configRepository = container.advisorConfigRepository,
            conversations = container.conversationRepository,
            engine = container.networkAdvisorEngine,
            settings = settings,
            preferences = container.carePreferenceRepository,
            restoreGeneration = { container.restoreWriteBarrier.generation() },
            database = container.databaseHandle,
            writeBarrier = container.restoreWriteBarrier,
        )
        val barrier = container.restoreWriteBarrier
        val startGeneration = barrier.generation()
        val card = runCatching { runner.runOnce() }.getOrNull() ?: return Result.success()
        val decisionId = card.decisionId ?: return Result.success()
        if (!card.isNew) return Result.success() // 旧卡片只展示，不再次通知
        // 恢复维护中/代次变化：不投递旧关心。
        if (!CareDeliveryGate.canDeliver(barrier.isReady(), startGeneration, barrier.generation())) return Result.success()
        if (!container.careRepository.claimNotification(decisionId)) return Result.success()
        if (!CareDeliveryGate.canDeliver(barrier.isReady(), startGeneration, barrier.generation())) return Result.success()
        CareNotification.post(app, card.opportunityId, card.text, settings.lockScreenDetail)
        return Result.success()
    }
}

object CareScheduler {
    private const val PERIODIC = "care_periodic"
    private const val ONE_SHOT = "care_one_shot"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CareWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<CareWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
