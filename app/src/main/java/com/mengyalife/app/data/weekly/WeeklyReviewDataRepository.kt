package com.mengyalife.app.data.weekly

import com.mengyalife.app.data.local.JournalEntryDao
import com.mengyalife.app.data.local.goal.GoalDao
import com.mengyalife.app.data.local.growth.GrowthMemoryDao
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.growth.GrowthMemoryIds
import com.mengyalife.app.domain.weekly.WeeklyCheckin
import com.mengyalife.app.domain.weekly.WeeklyGoal
import com.mengyalife.app.domain.weekly.WeeklyGoalLevel
import com.mengyalife.app.domain.weekly.WeeklyGoalStatus
import com.mengyalife.app.domain.weekly.WeeklyMemory
import com.mengyalife.app.domain.weekly.WeeklySnapshot

interface WeeklyReviewSource {
    suspend fun load(): WeeklySnapshot
}

/** 本地读取；不经过 AI 授权仓库，是否使用由 WeeklyReviewRules 的分类策略决定。 */
class WeeklyReviewDataRepository(
    private val goalDao: GoalDao,
    private val journalDao: JournalEntryDao,
    private val memoryDao: GrowthMemoryDao,
) : WeeklyReviewSource {
    override suspend fun load(): WeeklySnapshot {
        val goals = goalDao.loadAllGoals().map { goal ->
            WeeklyGoal(
                id = goal.id,
                title = goal.title,
                level = when (goal.level) {
                    GoalLevel.LONG_TERM -> WeeklyGoalLevel.LONG_TERM
                    GoalLevel.STAGE -> WeeklyGoalLevel.STAGE
                    GoalLevel.ACTION -> WeeklyGoalLevel.ACTION
                },
                status = when (goal.status) {
                    GoalStatus.NOT_STARTED -> WeeklyGoalStatus.NOT_STARTED
                    GoalStatus.IN_PROGRESS -> WeeklyGoalStatus.IN_PROGRESS
                    GoalStatus.PAUSED -> WeeklyGoalStatus.PAUSED
                    GoalStatus.DONE -> WeeklyGoalStatus.DONE
                    GoalStatus.CANCELLED -> WeeklyGoalStatus.CANCELLED
                },
                startDate = goal.startDate,
                dueDate = goal.dueDate,
                updatedAt = goal.updatedAt,
            )
        }
        val checkins = goalDao.loadAllCheckins().map { c ->
            WeeklyCheckin(c.id, c.goalId, c.recordedAt, c.note, c.metricValue, c.metricUnit)
        }
        val memories = memoryDao.loadAll().map { m ->
            WeeklyMemory(
                id = m.id,
                theme = m.theme,
                content = m.content,
                corrected = m.userCorrected,
                status = m.status,
                sourceDiaryIds = GrowthMemoryIds.decodeLongs(m.sourceDiaryIdsJson),
                updatedAt = m.updatedAt,
            )
        }
        return WeeklySnapshot(goals, checkins, memories)
    }
}
