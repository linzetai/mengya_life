package com.mengyalife.app.data.qa

import androidx.room.withTransaction
import com.mengyalife.app.data.local.MengyaDatabase
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus

/** QA-only, explicitly labelled persisted rows for device walkthroughs; never called by release builds. */
object QaGrowthLoopFixtureSeeder {
    private const val STALE_TITLE = "QA 样例：长期阅读"
    private const val ACTIVE_TITLE = "QA 样例：本周阅读"

    suspend fun seed(database: MengyaDatabase, now: Long = System.currentTimeMillis()) {
        database.withTransaction {
            val dao = database.goalDao()
            val existingTitles = dao.loadAllGoals().map { it.title }.toSet()
            if (STALE_TITLE in existingTitles && ACTIVE_TITLE in existingTitles) return@withTransaction

            val staleGoal = if (STALE_TITLE in existingTitles) {
                dao.loadAllGoals().first { it.title == STALE_TITLE }
            } else {
                dao.insertGoal(
                    GoalEntity(
                        title = STALE_TITLE,
                        description = "仅用于 QA 成长闭环验收，不代表用户真实进展。",
                        level = GoalLevel.LONG_TERM,
                        domain = GoalDomain.LEARNING,
                        status = GoalStatus.IN_PROGRESS,
                        startDate = now - 120L * DAY,
                        createdAt = now - 120L * DAY,
                        updatedAt = now - 120L * DAY,
                    ),
                ).let { id -> dao.findGoal(id)!! }
            }
            val activeGoal = if (ACTIVE_TITLE in existingTitles) {
                dao.loadAllGoals().first { it.title == ACTIVE_TITLE }
            } else {
                dao.insertGoal(
                    GoalEntity(
                        title = ACTIVE_TITLE,
                        description = "仅用于 QA 成长闭环验收；记录由测试夹具写入。",
                        level = GoalLevel.LONG_TERM,
                        domain = GoalDomain.LEARNING,
                        status = GoalStatus.IN_PROGRESS,
                        startDate = now - 14L * DAY,
                        createdAt = now - 14L * DAY,
                        updatedAt = now - 14L * DAY,
                    ),
                ).let { id -> dao.findGoal(id)!! }
            }

            val existingCheckins = dao.loadAllCheckins()
            if (existingCheckins.none { it.goalId == staleGoal.id }) {
                dao.insertCheckin(
                    GoalCheckinEntity(
                        goalId = staleGoal.id,
                        recordedAt = now - 90L * DAY,
                        note = "QA 样例记录：阅读一次",
                        source = "MANUAL",
                    ),
                )
                dao.insertCheckin(
                    GoalCheckinEntity(
                        goalId = staleGoal.id,
                        recordedAt = now - 60L * DAY,
                        note = "QA 样例记录：又读了一次",
                        source = "MANUAL",
                    ),
                )
            }
            if (existingCheckins.none { it.goalId == activeGoal.id }) {
                dao.insertCheckin(
                    GoalCheckinEntity(
                        goalId = activeGoal.id,
                        recordedAt = now - 7L * DAY,
                        note = "QA 样例记录：阅读一次",
                        metricValue = 1.0,
                        metricUnit = "次/周",
                        source = "MANUAL",
                    ),
                )
                dao.insertCheckin(
                    GoalCheckinEntity(
                        goalId = activeGoal.id,
                        recordedAt = now - 3L * DAY,
                        note = "QA 样例记录：阅读两次",
                        metricValue = 2.0,
                        metricUnit = "次/周",
                        source = "MANUAL",
                    ),
                )
            }
        }
    }

    private const val DAY = 24L * 60 * 60 * 1000
}
