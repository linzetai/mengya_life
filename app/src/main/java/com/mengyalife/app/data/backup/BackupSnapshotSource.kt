package com.mengyalife.app.data.backup

import androidx.room.withTransaction
import com.mengyalife.app.data.advisor.AdvisorConfigRepository
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.preferences.ThemePreferences
import com.mengyalife.app.data.weekly.WeeklyReviewPreferences
import kotlinx.coroutines.flow.first

/** 数据收集边界：实现方只读，不写业务表。 */
interface BackupSnapshotSource {
    suspend fun load(): BackupSnapshot
}

/** Room 只读事务内读取 10 张表；调用方不直接依赖 DAO。 */
class RoomBackupSnapshotSource(private val database: MengyaDatabase) : BackupSnapshotSource {
    override suspend fun load(): BackupSnapshot = database.withTransaction {
        val journalDao = database.journalEntryDao()
        val goalDao = database.goalDao()
        BackupSnapshot(
            journalEntries = journalDao.loadAll(),
            goals = goalDao.loadAllGoals(),
            goalCheckins = goalDao.loadAllCheckins(),
            advisorMessages = database.advisorMessageDao().loadAll(),
            goalProposals = database.goalProposalDao().loadAll(),
            growthSignals = database.growthSignalDao().loadAll(),
            growthMemories = database.growthMemoryDao().loadAll(),
            insightReports = database.insightReportDao().loadAll(),
            weeklyReviews = database.weeklyReviewDao().loadAll(),
            weeklyAiReviews = database.weeklyAiDao().loadAll(),
            smallPlans = database.smallPlanDao().loadAll(),
            planEvents = database.smallPlanDao().loadAllEvents(),
            planProgress = database.smallPlanDao().loadAllProgress(),
            careOpportunities = database.careDao().loadAllOpportunities(),
            careDecisions = database.careDao().allDecisions(),
            careFeedback = database.careDao().allFeedback(),
            carePreferences = database.carePreferenceDao().all(),
            recordProposals = database.recordProposalDao().loadAll(),
        )
    }
}

/** 设置收集边界：D1；绝不读取 API Key。 */
interface BackupSettingsSource {
    suspend fun load(): BackupSettingsSnapshot
}

class AppBackupSettingsSource(
    private val themePreferences: ThemePreferences,
    private val weeklyPreferences: WeeklyReviewPreferences,
    private val configRepository: AdvisorConfigRepository,
) : BackupSettingsSource {
    override suspend fun load(): BackupSettingsSnapshot {
        val config = configRepository.state.first()
        return BackupSettingsSnapshot(
            themeMode = themePreferences.themeMode.first().name,
            weeklyReviewEnabled = weeklyPreferences.enabled.first(),
            weeklyAiEnabled = weeklyPreferences.aiEnabled.first(),
            notificationAsked = weeklyPreferences.notificationAsked.first(),
            aiEndpoint = config.endpoint,
            aiModel = config.model,
        )
    }
}
