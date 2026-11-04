package com.mengyalife.app.data.advisor

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.growth.GrowthMemoryDao
import com.mengyalife.app.data.local.growth.GrowthSignalDao
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.advisor.ContextGrowthMemory
import com.mengyalife.app.domain.advisor.ContextGrowthSignal
import com.mengyalife.app.domain.advisor.ContextJournalRecord
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.growth.DiaryContentHash

/**
 * 自动上下文的数据入口。只在授权有效时被调用；
 * 类别开关在这里再次兜底，避免上层误用。
 */
interface AdvisorDataRepository {
    suspend fun snapshot(consent: AiConsentState): AdvisorContextSnapshot
}

class RoomAdvisorDataRepository(
    private val goalDao: GoalDao,
    private val journalDao: JournalEntryDao,
    private val growthSignalDao: GrowthSignalDao,
    private val growthMemoryDao: GrowthMemoryDao,
) : AdvisorDataRepository {

    override suspend fun snapshot(consent: AiConsentState): AdvisorContextSnapshot {
        val goals = if (consent.enabled(ConsentCategory.GOALS)) {
            goalDao.loadAllGoals().map {
                ContextGoalRecord(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    level = it.level,
                    parentId = it.parentId,
                    domain = it.domain,
                    status = it.status,
                    createdAt = it.createdAt,
                )
            }
        } else {
            emptyList()
        }
        val checkins = if (consent.enabled(ConsentCategory.PROGRESS)) {
            goalDao.loadAllCheckins().map {
                ContextCheckinRecord(
                    id = it.id,
                    goalId = it.goalId,
                    recordedAt = it.recordedAt,
                    note = it.note,
                    metricValue = it.metricValue,
                    metricUnit = it.metricUnit,
                )
            }
        } else {
            emptyList()
        }
        val journalRows = if (consent.enabled(ConsentCategory.JOURNAL)) journalDao.loadAll() else emptyList()
        val journals = journalRows.map {
            ContextJournalRecord(
                id = it.id,
                title = it.title,
                body = it.body,
                updatedAt = it.updatedAt,
            )
        }
        // 只用当前日记版本、仍存在的来源、ACTIVE/CORRECTED 线索；
        // 删除日记/编辑后旧线索不会进入上下文。
        val journalById = journalRows.associateBy { it.id }
        val signals = if (consent.enabled(ConsentCategory.JOURNAL)) {
            growthSignalDao.loadActive(50).mapNotNull { signal ->
                val journal = journalById[signal.sourceId] ?: return@mapNotNull null
                if (signal.sourceType != "DIARY") return@mapNotNull null
                if (signal.sourceVersion != DiaryContentHash.of(journal.title, journal.body)) {
                    return@mapNotNull null
                }
                val category = com.mengyalife.app.domain.growth.GrowthCategory.entries
                    .firstOrNull { it.name == signal.category }?.label ?: signal.category
                ContextGrowthSignal(
                    id = signal.id,
                    diaryId = signal.sourceId,
                    diaryTitle = journal.title,
                    category = category,
                    content = signal.content,
                    evidence = signal.evidence,
                    isInference = signal.isInference,
                    corrected = signal.status == "CORRECTED" || signal.origin == "USER",
                )
            }
        } else {
            emptyList()
        }
        val memories = if (consent.enabled(ConsentCategory.JOURNAL)) {
            val allJournals = journalById
            growthMemoryDao.loadAll()
                .filter { it.status == "ACTIVE" || it.status == "CORRECTED" }
                .filter { memory ->
                    val ids = com.mengyalife.app.domain.growth.GrowthMemoryIds
                        .decodeLongs(memory.sourceDiaryIdsJson)
                    ids.isNotEmpty() && ids.all { it in allJournals }
                }
                .map { memory ->
                    ContextGrowthMemory(
                        id = memory.id,
                        theme = memory.theme,
                        content = memory.content,
                        isInference = memory.isInference,
                        corrected = memory.userCorrected || memory.status == "CORRECTED",
                        insufficient = memory.insufficient,
                        recentChange = memory.recentChange,
                        sourceDiaryIds = com.mengyalife.app.domain.growth.GrowthMemoryIds
                            .decodeLongs(memory.sourceDiaryIdsJson),
                    )
                }
        } else {
            emptyList()
        }
        return AdvisorContextSnapshot(
            goals = goals,
            checkins = checkins,
            journals = journals,
            growthSignals = signals,
            growthMemories = memories,
            history = emptyList(),
        )
    }
}
