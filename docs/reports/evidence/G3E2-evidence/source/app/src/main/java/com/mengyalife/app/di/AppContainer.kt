package com.mengyalife.app.di

import android.content.Context
import androidx.room.withTransaction
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AndroidKeystoreCipher
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.data.advisor.DataStoreAdvisorConfigRepository
import com.mengyalife.app.data.advisor.DataStoreAiConfigStorage
import com.mengyalife.app.data.advisor.FakeAdvisorEngine
import com.mengyalife.app.data.advisor.GoalProposalApplyService
import com.mengyalife.app.data.advisor.GoalProposalRepository
import com.mengyalife.app.data.advisor.RoomAdvisorDataRepository
import com.mengyalife.app.data.advisor.RoomGoalProposalRepository
import com.mengyalife.app.data.advisor.NetworkAdvisorEngine
import com.mengyalife.app.data.advisor.RoomConversationRepository
import com.mengyalife.app.data.advisor.SecretCipher
import com.mengyalife.app.data.consent.AiConsentRepository
import com.mengyalife.app.data.consent.DataStoreAiConsentRepository
import com.mengyalife.app.data.export.DataExportRepository
import com.mengyalife.app.data.growth.DiaryExtractionService
import com.mengyalife.app.data.growth.GrowthMemoryRepository
import com.mengyalife.app.data.growth.GrowthMemoryService
import com.mengyalife.app.data.growth.GrowthSignalRepository
import com.mengyalife.app.data.insight.InsightService
import com.mengyalife.app.data.local.DB_SCHEMA_VERSION
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.preferences.ThemePreferences
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.data.weekly.WeeklyReviewDataRepository
import com.mengyalife.app.data.weekly.WeeklyReviewNotifier
import com.mengyalife.app.data.weekly.WeeklyReviewPreferences
import com.mengyalife.app.data.weekly.WeeklyReviewScheduler
import com.mengyalife.app.data.weekly.WeeklyReviewService
import com.mengyalife.app.data.weekly.WeeklyAiService

/**
 * 手工依赖容器（本轮刻意不引入 Hilt：少一个编译器插件、少一层构建风险）。
 *
 * 用 `by lazy` 保证数据库与 DataStore 都是首次使用时才创建。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val database: MengyaDatabase by lazy { MengyaDatabase.get(appContext) }

    val journalRepository: JournalRepository by lazy {
        JournalRepository(
            dao = database.journalEntryDao(),
            growthSignalDao = database.growthSignalDao(),
            transaction = { block -> database.withTransaction { block() } },
            onSignalsChanged = { growthMemoryService.rebuildAll() },
        )
    }

    val growthMemoryService: GrowthMemoryService by lazy {
        GrowthMemoryService(
            journalDao = database.journalEntryDao(),
            signalDao = database.growthSignalDao(),
            memoryDao = database.growthMemoryDao(),
            transaction = { block -> database.withTransaction { block() } },
        )
    }

    val growthSignalRepository: GrowthSignalRepository by lazy {
        GrowthSignalRepository(
            dao = database.growthSignalDao(),
            onChanged = { growthMemoryService.rebuildAll() },
        )
    }

    val insightService: InsightService by lazy {
        InsightService(
            dataRepository = advisorDataRepository,
            consentRepository = aiConsentRepository,
            configRepository = advisorConfigRepository,
            networkEngine = networkAdvisorEngine,
            fakeEngine = fakeAdvisorEngine,
            dao = database.insightReportDao(),
        )
    }

    val growthMemoryRepository: GrowthMemoryRepository by lazy {
        GrowthMemoryRepository(
            memoryDao = database.growthMemoryDao(),
            signalDao = database.growthSignalDao(),
            journalDao = database.journalEntryDao(),
        )
    }

    val diaryExtractionService: DiaryExtractionService by lazy {
        DiaryExtractionService(
            journalRepository = journalRepository,
            growthSignalDao = database.growthSignalDao(),
            configRepository = advisorConfigRepository,
            consentRepository = aiConsentRepository,
            networkEngine = networkAdvisorEngine,
            fakeEngine = fakeAdvisorEngine,
            transaction = { block -> database.withTransaction { block() } },
            onSignalsChanged = { growthMemoryService.rebuildAll() },
        )
    }

    val goalRepository: GoalRepository by lazy {
        GoalRepository(database.goalDao())
    }

    val weeklyReviewPreferences: WeeklyReviewPreferences by lazy {
        WeeklyReviewPreferences(appContext)
    }

    val weeklyReviewScheduler: WeeklyReviewScheduler by lazy {
        WeeklyReviewScheduler(appContext)
    }

    val weeklyReviewNotifier: WeeklyReviewNotifier by lazy {
        WeeklyReviewNotifier(appContext)
    }

    val weeklyReviewDataRepository: WeeklyReviewDataRepository by lazy {
        WeeklyReviewDataRepository(
            goalDao = database.goalDao(),
            journalDao = database.journalEntryDao(),
            memoryDao = database.growthMemoryDao(),
            signalDao = database.growthSignalDao(),
        )
    }

    val weeklyReviewService: WeeklyReviewService by lazy {
        WeeklyReviewService(
            dataRepository = weeklyReviewDataRepository,
            dao = database.weeklyReviewDao(),
            consentRepository = aiConsentRepository,
        )
    }

    val weeklyAiService: WeeklyAiService by lazy {
        WeeklyAiService(
            source = weeklyReviewDataRepository,
            consentRepository = aiConsentRepository,
            configRepository = advisorConfigRepository,
            networkEngine = networkAdvisorEngine,
            dao = database.weeklyAiDao(),
        )
    }

    /** Worker 使用；只在本地读写，不构造网络引擎。 */
    val databaseWeeklyReviewDao get() = database.weeklyReviewDao()

    val themePreferences: ThemePreferences by lazy {
        ThemePreferences(appContext)
    }

    private val secretCipher: SecretCipher by lazy {
        AndroidKeystoreCipher(BuildConfig.AI_KEY_ALIAS)
    }

    val advisorConfigRepository: AdvisorConfigRepository by lazy {
        DataStoreAdvisorConfigRepository(
            storage = DataStoreAiConfigStorage(appContext),
            cipher = secretCipher,
        )
    }

    val conversationRepository: ConversationRepository by lazy {
        RoomConversationRepository(database.advisorMessageDao())
    }

    val aiConsentRepository: AiConsentRepository by lazy {
        DataStoreAiConsentRepository(appContext)
    }

    val advisorDataRepository: AdvisorDataRepository by lazy {
        RoomAdvisorDataRepository(
            goalDao = database.goalDao(),
            journalDao = database.journalEntryDao(),
            growthSignalDao = database.growthSignalDao(),
            growthMemoryDao = database.growthMemoryDao(),
        )
    }

    val goalProposalRepository: GoalProposalRepository by lazy {
        RoomGoalProposalRepository(database.goalProposalDao())
    }

    val goalProposalApplyService: GoalProposalApplyService by lazy {
        GoalProposalApplyService(
            database = database,
            proposalDao = database.goalProposalDao(),
            goalRepository = goalRepository,
        )
    }

    val networkAdvisorEngine: NetworkAdvisorEngine by lazy { NetworkAdvisorEngine() }

    /** 仅 QA 构建提供；正式包永远为 null，避免假回复出现在真实产品里。 */
    val fakeAdvisorEngine: FakeAdvisorEngine? by lazy {
        if (BuildConfig.QA_BUILD) FakeAdvisorEngine() else null
    }

    /**
     * 导出仓库。事务执行器用 Room 的 `withTransaction`：
     * 三张表在同一事务内读取，得到一致快照。
     */
    val dataExportRepository: DataExportRepository by lazy {
        DataExportRepository(
            journalDao = database.journalEntryDao(),
            goalDao = database.goalDao(),
            appPackage = BuildConfig.APPLICATION_ID,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            databaseVersion = DB_SCHEMA_VERSION,
            transaction = { block ->
                database.withTransaction { block() }
            },
        )
    }
}
