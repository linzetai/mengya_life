package com.mengyalife.app.ui.screens

import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 今天页「下一步」的决策规则。
 *
 * 规则必须由真实状态决定：没有数据时不编造判断，也不把同一件事重复推荐。
 */
class TodayNextStepTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val noon = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun goal(
        id: Long,
        title: String,
        status: GoalStatus = GoalStatus.IN_PROGRESS,
    ) = GoalEntity(
        id = id,
        title = title,
        level = GoalLevel.LONG_TERM,
        domain = GoalDomain.LIFE,
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun snapshot(vararg nodes: GoalNode) = GoalSnapshot(
        roots = nodes.toList(),
        totalGoals = nodes.size,
        longTermCount = nodes.size,
        lastCheckinAt = nodes.mapNotNull { it.lastCheckinAt }.maxOrNull(),
    )

    private fun node(
        id: Long,
        title: String,
        lastCheckinAt: Long? = null,
        checkinCount: Int = if (lastCheckinAt == null) 0 else 1,
        status: GoalStatus = GoalStatus.IN_PROGRESS,
    ) = GoalNode(
        goal = goal(id, title, status),
        children = emptyList(),
        checkinCount = checkinCount,
        lastCheckinAt = lastCheckinAt,
    )

    private fun entry(createdAt: Long) = JournalEntryEntity(
        id = 1L,
        title = null,
        body = "记录",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun decide(
        careText: String? = null,
        careSourceType: String? = null,
        plans: List<PlanTodayItem> = emptyList(),
        goals: GoalSnapshot = GoalSnapshot(),
        entries: List<JournalEntryEntity> = emptyList(),
    ) = decideTodayNextStep(
        careText = careText,
        careSourceType = careSourceType,
        plans = plans,
        goals = goals,
        entries = entries,
        today = today,
        now = noon,
    )

    @Test
    fun `有真实关心时优先呈现关心`() {
        val step = decide(careText = "这周的身体记录少了一些", careSourceType = "GOAL")
        assertEquals(TodayNextStepKind.CARE, step.kind)
        assertEquals("来自你的长期目标", step.body)
    }

    @Test
    fun `本周计划没有进展时先聊计划`() {
        val step = decide(
            plans = listOf(PlanTodayItem(7L, "每天走 20 分钟", "本周", progressCount = 0)),
            goals = snapshot(node(1L, "身体")),
        )
        assertEquals(TodayNextStepKind.PLAN, step.kind)
        assertEquals(7L, step.planId)
        assertEquals("每天走 20 分钟", step.planTitle)
    }

    @Test
    fun `没有任何方向时引导创建方向`() {
        val step = decide()
        assertEquals(TodayNextStepKind.CREATE_GOAL, step.kind)
        assertNull(step.goalId)
    }

    @Test
    fun `超过十天没更新的方向会被提醒`() {
        val stale = noon - STALE_AFTER_MILLIS - 1
        val step = decide(goals = snapshot(node(3L, "写作", lastCheckinAt = stale)))
        assertEquals(TodayNextStepKind.STALE_GOAL, step.kind)
        assertEquals(3L, step.goalId)
    }

    @Test
    fun `刚更新的方向不会误报为停滞`() {
        val step = decide(
            goals = snapshot(node(4L, "身体", lastCheckinAt = noon - 60_000L)),
        )
        assertEquals(TodayNextStepKind.WRITE_DIARY, step.kind)
    }

    @Test
    fun `今天已经写过日记则提议聊一聊`() {
        val step = decide(
            goals = snapshot(node(5L, "身体", lastCheckinAt = noon - 60_000L)),
            entries = listOf(entry(noon - 3_600_000L)),
        )
        assertEquals(TodayNextStepKind.TALK, step.kind)
    }

    @Test
    fun `已完成的方向不参与停滞提醒`() {
        val old = noon - STALE_AFTER_MILLIS - 1
        val step = decide(
            goals = snapshot(
                node(6L, "旧方向", lastCheckinAt = old, status = GoalStatus.DONE),
            ),
        )
        assertEquals(TodayNextStepKind.WRITE_DIARY, step.kind)
    }

    @Test
    fun `从未记录过的新方向不当作停滞`() {
        val step = decide(goals = snapshot(node(8L, "新方向", lastCheckinAt = null)))
        assertEquals(TodayNextStepKind.WRITE_DIARY, step.kind)
    }

    @Test
    fun `昨天写的日记不算今天`() {
        val step = decide(
            goals = snapshot(node(7L, "身体", lastCheckinAt = noon - 60_000L)),
            entries = listOf(entry(noon - 24L * 60 * 60 * 1000)),
        )
        assertEquals(TodayNextStepKind.WRITE_DIARY, step.kind)
    }
}
