package com.mengyalife.app.data.advisor

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.advisor.ContextJournalRecord
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.AiConsentState

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
        val journals = if (consent.enabled(ConsentCategory.JOURNAL)) {
            journalDao.loadAll().map {
                ContextJournalRecord(
                    id = it.id,
                    title = it.title,
                    body = it.body,
                    updatedAt = it.updatedAt,
                )
            }
        } else {
            emptyList()
        }
        return AdvisorContextSnapshot(
            goals = goals,
            checkins = checkins,
            journals = journals,
            history = emptyList(),
        )
    }
}
