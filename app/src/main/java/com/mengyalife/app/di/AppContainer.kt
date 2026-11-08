package com.mengyalife.app.di

import android.content.Context
import androidx.room.withTransaction
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.backup.AndroidBackupTempStore
import com.mengyalife.app.data.backup.AppBackupSettingsSource
import com.mengyalife.app.data.backup.BackupAppInfo
import com.mengyalife.app.data.backup.BackupExporter
import com.mengyalife.app.data.backup.BackupFileWriter
import com.mengyalife.app.data.backup.RoomBackupSnapshotSource
import com.mengyalife.app.data.backup.SafBackupDestination
import com.mengyalife.app.data.advisor.AdvisorDataRepository
import com.mengyalife.app.data.advisor.AndroidKeystoreCipher
import com.mengyalife.app.data.advisor.ConversationRepository
import com.mengyalife.app.data.advisor.DataStoreAdvisorConfigRepository
import com.mengyalife.app.data.advisor.DataStoreAiConfigStorage
import com.mengyalife.app.data.advisor.FakeAdvisorEngine
import com.mengyalife.app.data.advisor.GoalProposalApplyService
import com.mengyalife.app.data.advisor.GoalProposalRepository
import com.mengyalife.app.data.advisor.DiaryRecordApplyService
import com.mengyalife.app.data.advisor.GoalRecordApplyService
import com.mengyalife.app.data.advisor.ProgressRecordApplyService
import com.mengyalife.app.data.advisor.RecordProposalRepository
import com.mengyalife.app.data.advisor.RoomRecordProposalRepository
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
import com.mengyalife.app.data.restore.BackupRestoreReader
import com.mengyalife.app.data.restore.RestoreCurrentStateSource
import com.mengyalife.app.data.restore.RestorePlanner
import com.mengyalife.app.data.restore.RestoreCoordinator
import com.mengyalife.app.data.restore.RestoreGate
import com.mengyalife.app.data.restore.RestoreJournal
import com.mengyalife.app.data.restore.RestoreSettingsApplier
import com.mengyalife.app.data.restore.RestoreSnapshotStore
import com.mengyalife.app.data.restore.RestoreTransaction
import com.mengyalife.app.data.restore.RestoreValidator
import com.mengyalife.app.data.restore.RoomRestoreCurrentStateSource
import com.mengyalife.app.data.restore.SafRestoreFileSource
import com.mengyalife.app.data.repository.GoalRepository
import com.mengyalife.app.data.repository.JournalRepository
import com.mengyalife.app.data.weekly.WeeklyReviewDataRepository
import com.mengyalife.app.data.weekly.WeeklyReviewNotifier
import com.mengyalife.app.data.weekly.WeeklyReviewPreferences
import com.mengyalife.app.data.weekly.WeeklyReviewScheduler
import com.mengyalife.app.data.weekly.WeeklyReviewService
import com.mengyalife.app.data.weekly.WeeklyAiService
import kotlinx.coroutines.flow.first

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
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val growthMemoryService: GrowthMemoryService by lazy {
        GrowthMemoryService(
            journalDao = database.journalEntryDao(),
            signalDao = database.growthSignalDao(),
            memoryDao = database.growthMemoryDao(),
            transaction = { block -> database.withTransaction { block() } },
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val growthSignalRepository: GrowthSignalRepository by lazy {
        GrowthSignalRepository(
            dao = database.growthSignalDao(),
            onChanged = { growthMemoryService.rebuildAll() },
            writeBarrier = restoreWriteBarrier,
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
            beforeWrite = { if (restoreCoordinator.isGateActive()) throw IllegalStateException("恢复维护中") },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val growthMemoryRepository: GrowthMemoryRepository by lazy {
        GrowthMemoryRepository(
            memoryDao = database.growthMemoryDao(),
            signalDao = database.growthSignalDao(),
            journalDao = database.journalEntryDao(),
            writeBarrier = restoreWriteBarrier,
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
            beforeWrite = { if (restoreCoordinator.isGateActive()) throw IllegalStateException("恢复维护中") },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val goalRepository: GoalRepository by lazy {
        GoalRepository(database.goalDao(), canWrite = { !restoreCoordinator.isGateActive() }, writeBarrier = restoreWriteBarrier)
    }

    val weeklyReviewPreferences: WeeklyReviewPreferences by lazy {
        WeeklyReviewPreferences(appContext, restoreWriteBarrier)
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
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val weeklyAiService: WeeklyAiService by lazy {
        WeeklyAiService(
            source = weeklyReviewDataRepository,
            consentRepository = aiConsentRepository,
            configRepository = advisorConfigRepository,
            networkEngine = networkAdvisorEngine,
            fakeEngine = fakeAdvisorEngine,
            dao = database.weeklyAiDao(),
            autoEnabled = { weeklyReviewPreferences.enabled.first() && weeklyReviewPreferences.aiEnabled.first() },
            beforeRequest = { if (restoreCoordinator.isGateActive()) throw IllegalStateException("恢复维护中") },
            beforeComplete = { if (restoreCoordinator.isGateActive()) throw IllegalStateException("恢复维护中") },
            writeBarrier = restoreWriteBarrier,
        )
    }

    /** Worker 使用；只在本地读写，不构造网络引擎。 */
    val databaseWeeklyReviewDao get() = database.weeklyReviewDao()

    /** 仅 QA：清空本周 AI 结果行，便于状态机用例独立重跑（正式包 no-op）。 */
    suspend fun qaClearWeeklyAi() {
        if (!BuildConfig.QA_BUILD) return
        check(restoreCoordinator.awaitReady()) { "恢复尚未就绪，QA清理未执行" }
        restoreWriteBarrier.withWrite { database.openHelper.writableDatabase.execSQL("DELETE FROM weekly_ai_reviews") }
    }

    suspend fun qaSeedRestoreSecurity() {
        if (!BuildConfig.QA_BUILD) return
        check(restoreCoordinator.awaitReady()) { "恢复尚未就绪，QA security fixture未写入" }
        com.mengyalife.app.data.qa.QaRestoreSecuritySeeder.seed(this)
    }

    /** G4A-1.1 QA-only：10 表全字段非空夹具；正式包忽略。 */
    suspend fun qaSeedBackupFixture() {
        if (!BuildConfig.QA_BUILD) return
        check(restoreCoordinator.awaitReady()) { "恢复尚未就绪，QA fixture未写入" }
        com.mengyalife.app.data.qa.QaBackupFixtureSeeder.seed(database)
    }

    suspend fun qaSeedBackupFixtureB() {
        if (!BuildConfig.QA_BUILD) return
        check(restoreCoordinator.awaitReady()) { "恢复尚未就绪，QA fixture未写入" }
        com.mengyalife.app.data.qa.QaBackupFixtureSeeder.seedVariantB(database)
    }

    val themePreferences: ThemePreferences by lazy {
        ThemePreferences(appContext, restoreWriteBarrier)
    }

    private val secretCipher: SecretCipher by lazy {
        AndroidKeystoreCipher(BuildConfig.AI_KEY_ALIAS)
    }

    val advisorConfigRepository: AdvisorConfigRepository by lazy {
        DataStoreAdvisorConfigRepository(
            storage = DataStoreAiConfigStorage(appContext),
            cipher = secretCipher,
            writeBarrier = restoreWriteBarrier,
        )
    }

    val conversationRepository: ConversationRepository by lazy {
        RoomConversationRepository(database.advisorMessageDao(), canWrite = { !restoreCoordinator.isGateActive() }, writeBarrier = restoreWriteBarrier)
    }

    val aiConsentRepository: AiConsentRepository by lazy {
        DataStoreAiConsentRepository(appContext, writeBarrier = restoreWriteBarrier)
    }

    val advisorDataRepository: AdvisorDataRepository by lazy {
        RoomAdvisorDataRepository(
            goalDao = database.goalDao(),
            journalDao = database.journalEntryDao(),
            growthSignalDao = database.growthSignalDao(),
            growthMemoryDao = database.growthMemoryDao(),
            smallPlanDao = database.smallPlanDao(),
        )
    }

    val smallPlanRepository: com.mengyalife.app.data.repository.SmallPlanRepository by lazy {
        com.mengyalife.app.data.repository.SmallPlanRepository(
            dao = database.smallPlanDao(),
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val planRecordApplyService: com.mengyalife.app.data.advisor.PlanRecordApplyService by lazy {
        com.mengyalife.app.data.advisor.PlanRecordApplyService(
            database = database,
            recordDao = database.recordProposalDao(),
            planRepository = smallPlanRepository,
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val goalProposalRepository: GoalProposalRepository by lazy {
        RoomGoalProposalRepository(database.goalProposalDao(), writeBarrier = restoreWriteBarrier)
    }

    val goalProposalApplyService: GoalProposalApplyService by lazy {
        GoalProposalApplyService(
            database = database,
            proposalDao = database.goalProposalDao(),
            goalRepository = goalRepository,
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val recordProposalRepository: RecordProposalRepository by lazy {
        RoomRecordProposalRepository(database.recordProposalDao(), writeBarrier = restoreWriteBarrier)
    }

    val progressRecordApplyService: ProgressRecordApplyService by lazy {
        ProgressRecordApplyService(
            database = database,
            recordDao = database.recordProposalDao(),
            goalRepository = goalRepository,
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val diaryRecordApplyService: DiaryRecordApplyService by lazy {
        DiaryRecordApplyService(
            database = database,
            recordDao = database.recordProposalDao(),
            journalRepository = journalRepository,
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val goalRecordApplyService: GoalRecordApplyService by lazy {
        GoalRecordApplyService(
            database = database,
            recordDao = database.recordProposalDao(),
            goalRepository = goalRepository,
            canWrite = { !restoreCoordinator.isGateActive() },
            writeBarrier = restoreWriteBarrier,
        )
    }

    val careRepository: com.mengyalife.app.data.care.CareRepository by lazy {
        com.mengyalife.app.data.care.CareRepository(database.careDao())
    }

    val carePreferenceRepository: com.mengyalife.app.data.care.CarePreferenceRepository by lazy {
        com.mengyalife.app.data.care.CarePreferenceRepository(database.carePreferenceDao())
    }

    val networkAdvisorEngine: NetworkAdvisorEngine by lazy { NetworkAdvisorEngine() }

    /** 仅 QA 构建提供；正式包永远为 null，避免假回复出现在真实产品里。 */
    val fakeAdvisorEngine: FakeAdvisorEngine? by lazy {
        if (BuildConfig.QA_BUILD) FakeAdvisorEngine() else null
    }

    /** v2 完整加密备份：只读快照 + 设置快照 + 格式/加密。 */
    val backupExporter: BackupExporter by lazy {
        BackupExporter(
            snapshotSource = RoomBackupSnapshotSource(database),
            settingsSource = AppBackupSettingsSource(
                themePreferences = themePreferences,
                weeklyPreferences = weeklyReviewPreferences,
                configRepository = advisorConfigRepository,
            ),
            appInfo = BackupAppInfo(
                appPackage = BuildConfig.APPLICATION_ID,
                qaBuild = BuildConfig.QA_BUILD,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                androidSdk = android.os.Build.VERSION.SDK_INT,
            ),
        )
    }

    /** v2 写入：私有临时文件 + SAF + 回读解密校验。 */
    val backupFileWriter: BackupFileWriter by lazy {
        BackupFileWriter(
            tempStore = AndroidBackupTempStore(appContext),
            destination = SafBackupDestination(appContext),
            verifier = backupExporter::verify,
        )
    }

    /** G4A-2A：只读 SAF 文件源 + 解密/校验/预览，不提供写入入口。 */
    val restoreFileSource by lazy { SafRestoreFileSource(appContext) }

    val restoreReader by lazy { BackupRestoreReader() }

    val restoreValidator by lazy {
        RestoreValidator(
            expectedApp = BackupAppInfo(
                appPackage = BuildConfig.APPLICATION_ID,
                qaBuild = BuildConfig.QA_BUILD,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                androidSdk = android.os.Build.VERSION.SDK_INT,
            ),
        )
    }

    val restorePlanner by lazy {
        RestorePlanner(RoomRestoreCurrentStateSource(database))
    }

    val restoreWriteBarrier by lazy { com.mengyalife.app.data.restore.RestoreWriteBarrier(initiallyReady = false) }

    private val restoreDir get() = java.io.File(appContext.noBackupFilesDir, "restore")
    private val restoreGate by lazy { RestoreGate(restoreDir) }
    private val restoreJournal by lazy { RestoreJournal(restoreDir) }
    private val restoreSnapshotStore by lazy {
        RestoreSnapshotStore(
            dir = restoreDir,
            exporter = backupExporter,
            keyCipher = AndroidKeystoreCipher("mengya_restore_snapshot"),
        )
    }
    private val restoreSettingsApplier by lazy {
        RestoreSettingsApplier(themePreferences, weeklyReviewPreferences, advisorConfigRepository)
    }
    val restoreCoordinator by lazy {
        RestoreCoordinator(
            database = database,
            gate = restoreGate,
            journal = restoreJournal,
            snapshotStore = restoreSnapshotStore,
            transaction = RestoreTransaction(database),
            consentRepository = aiConsentRepository,
            configRepository = advisorConfigRepository,
            weeklyPreferences = weeklyReviewPreferences,
            settingsApplier = restoreSettingsApplier,
            reader = restoreReader,
            validator = restoreValidator,
            writeBarrier = restoreWriteBarrier,
            evidenceDir = if (BuildConfig.QA_BUILD) restoreDir else null,
            onSuccess = { message ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_LONG).show()
                }
            },
        )
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
