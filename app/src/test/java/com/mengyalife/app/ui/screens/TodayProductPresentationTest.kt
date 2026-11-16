package com.mengyalife.app.ui.screens

import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.local.plan.PlanProgressEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayProductPresentationTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val now = today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
    private fun goal(id: Long, title: String, parent: Long? = null) = GoalEntity(
        id = id, title = title, level = if (parent == null) GoalLevel.LONG_TERM else GoalLevel.ACTION,
        parentId = parent, domain = GoalDomain.LEARNING, status = GoalStatus.IN_PROGRESS,
        createdAt = now - 10_000, updatedAt = now,
    )
    private fun node(g: GoalEntity, vararg children: GoalEntity) =
        GoalNode(g, children.map { GoalNode(it, emptyList(), 0, null) }, 0, null)
    private fun plan(id: Long, goalId: Long?, title: String, updated: Long = now, scope: String? = "本周") =
        SmallPlanEntity(id, title, scope, goalId, "ACTIVE", "message", now, updated)
    private fun record(id: Long, goalId: Long, at: Long, note: String) =
        GoalCheckinEntity(id, goalId, at, note, 1.0, "次", "MANUAL")
    private fun planProgress(id: Long, planId: Long, at: Long = now) =
        PlanProgressEntity(id, planId, "已按计划记录", at, "source-$id")

    @Test fun emptyAccountUsesTrueEmptyStateWithoutInventedPlanOrProgress() {
        val value = buildTodayHomePresentation(GoalSnapshot(), emptyList(), emptyList(), emptyList(), today, now,
            ZoneId.of("Asia/Shanghai"))
        assertTrue(value.goals.isEmpty())
        assertNull(value.selectedPlan)
        assertNull(value.recordedPlanCount)
        assertTrue(value.recentRecords.isEmpty())
        assertFalse(value.needsGoalChoice)
    }

    @Test fun weeklySummaryUsesTheSelectedCurrentEntityAndExactPlanProgressCount() {
        val parent = goal(1, "准备半程马拉松")
        val action = goal(2, "周二阅读十分钟", 1)
        val plan = plan(40, 2, "周二和周四睡前读十分钟")
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(parent, action))),
            listOf(plan), listOf(planProgress(1, 40)), emptyList(), today, now, ZoneId.of("Asia/Shanghai"))
        assertEquals(40L, result.selectedPlan!!.id)
        assertEquals(40L, result.selectedPlanSubject!!.stableId)
        assertEquals("周二和周四睡前读十分钟", result.selectedPlanSubject!!.currentName)
        assertEquals("周二阅读十分钟", result.selectedPlanSubject!!.linkedGoal!!.currentName)
        assertEquals(1, result.recordedPlanCount)
        assertEquals(2L, result.recordTargetGoalId)
        assertEquals("准备半程马拉松", result.goals.single().title)
    }

    @Test fun weeklyProgressCountsOnlyRowsForTheSelectedPlan() {
        val g = goal(7, "本周阅读")
        val p = plan(90, 7, "本周读两次")
        val other = plan(91, 7, "下周读一次", updated = now - 1000)
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(g))),
            listOf(p, other), listOf(planProgress(1, 90), planProgress(2, 90), planProgress(3, 91)),
            emptyList(), today, now, ZoneId.of("Asia/Shanghai"))
        assertEquals(90L, result.selectedPlan!!.id)
        assertEquals(2, result.recordedPlanCount)
    }

    @Test fun currentPlanSummaryDoesNotInventAPlannedFrequencyOrDenominator() {
        val goal = goal(7, "本周散步")
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(goal))),
            listOf(plan(90, 7, "有空时去河边散步")), emptyList(), emptyList(), today, now,
            ZoneId.of("Asia/Shanghai"))
        assertEquals(0, result.recordedPlanCount)
        assertEquals("有空时去河边散步", result.selectedPlan!!.title)
        // The domain has no confirmed occurrence-count field, so the UI must not synthesize “0/3”.
    }

    @Test fun standalonePlanIdentityRemainsIndependentWithoutBorrowingAGoal() {
        val selected = plan(90, null, "单独安排")
        val result = buildTodayHomePresentation(
            GoalSnapshot(), listOf(selected), emptyList(), emptyList(), today, now, ZoneId.of("Asia/Shanghai"),
        )
        assertEquals(90L, result.selectedPlanSubject!!.stableId)
        assertEquals(com.mengyalife.app.ui.presentation.InformationGoalRelation.INDEPENDENT,
            result.selectedPlanSubject!!.goalRelation)
        assertNull(result.selectedPlanSubject!!.linkedGoal)
    }

    @Test fun selectedPlanIdentityResolvesCurrentRenamedGoalByStableId() {
        val current = goal(7, "新标题").copy(status = GoalStatus.IN_PROGRESS)
        val oldDuplicateTitle = goal(8, "新标题").copy(status = GoalStatus.IN_PROGRESS)
        val selected = plan(90, 7, "阅读计划")
        val result = buildTodayHomePresentation(
            GoalSnapshot(roots = listOf(node(current), node(oldDuplicateTitle))),
            listOf(selected), emptyList(), emptyList(), today, now, ZoneId.of("Asia/Shanghai"),
        )
        assertEquals(90L, result.selectedPlanSubject!!.stableId)
        assertEquals(7L, result.selectedPlanSubject!!.linkedGoal!!.id)
        assertEquals("新标题", result.selectedPlanSubject!!.linkedGoal!!.currentName)
    }

    @Test fun selectedPlanWithDeletedGoalKeepsPlanIdAndMarksMissingRelation() {
        val selected = plan(90, 999, "旧目标的计划")
        val result = buildTodayHomePresentation(
            GoalSnapshot(roots = emptyList()), listOf(selected), emptyList(), emptyList(), today, now,
            ZoneId.of("Asia/Shanghai"),
        )
        assertEquals(90L, result.selectedPlanSubject!!.stableId)
        assertEquals("旧目标的计划", result.selectedPlanSubject!!.currentName)
        assertEquals(com.mengyalife.app.ui.presentation.InformationGoalRelation.MISSING,
            result.selectedPlanSubject!!.goalRelation)
        assertNull(result.recordTargetGoalId)
    }

    @Test fun selectedPlanCanNamePausedGoalButCannotTurnItIntoRecordingTarget() {
        val paused = goal(7, "已暂缓目标").copy(status = GoalStatus.PAUSED)
        val selected = plan(90, 7, "继续时再阅读")
        val result = buildTodayHomePresentation(
            GoalSnapshot(roots = listOf(node(paused))), listOf(selected), emptyList(), emptyList(), today, now,
            ZoneId.of("Asia/Shanghai"),
        )
        assertEquals("已暂缓目标", result.selectedPlanSubject!!.linkedGoal!!.currentName)
        assertNull(result.recordTargetGoalId)
        assertFalse(result.needsGoalChoice)
    }

    @Test fun oneActiveGoalIsNotMadeAmbiguousByAnUnstartedIndependentDirection() {
        val active = goal(1, "正在准备比赛")
        val notStarted = goal(2, "想学一门乐器").copy(status = GoalStatus.NOT_STARTED)
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(active), node(notStarted))),
            emptyList(), emptyList(), emptyList(), today, now, ZoneId.of("Asia/Shanghai"))
        assertEquals(listOf(1L), result.goals.map { it.id })
        assertEquals(1L, result.recordTargetGoalId)
        assertFalse(result.needsGoalChoice)
    }

    @Test fun severalUnstartedGoalsRequireTheUserToChoose() {
        val a = goal(1, "想学插花").copy(status = GoalStatus.NOT_STARTED)
        val b = goal(2, "想练钢琴").copy(status = GoalStatus.NOT_STARTED)
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(a), node(b))),
            emptyList(), emptyList(), emptyList(), today, now, ZoneId.of("Asia/Shanghai"))
        assertNull(result.recordTargetGoalId)
        assertTrue(result.needsGoalChoice)
        assertEquals(2, result.goals.size)
    }

    @Test fun plansOutsideTheirResolvedDateWindowDoNotMasqueradeAsThisWeek() {
        val goal = goal(7, "阅读目标")
        val future = plan(92, 7, "下月读书")
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(goal))),
            listOf(future.copy(timeScope = "下月", createdAt = today.plusDays(30).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli())),
            emptyList(), emptyList(), today, now, ZoneId.of("Asia/Shanghai"))
        assertNull(result.selectedPlan)
    }

    @Test fun weeklyPlanProgressStaysPendingUntilTheRoomFlowEmits() {
        val g = goal(11, "阅读目标")
        val p = plan(44, 11, "阅读")
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(g))),
            listOf(p), emptyList(), emptyList(), today, now, ZoneId.of("Asia/Shanghai"), weeklyPlanProgressReady = false)
        assertNull(result.recordedPlanCount)
        assertEquals(44L, result.selectedPlanSubject!!.stableId)
    }

    @Test fun recentRecordsAreAtMostTwoRealRowsInRecordedTimeOrder() {
        val goal = goal(7, "阅读目标")
        val rows = listOf(
            record(1, 7, now - 3_000, "读完一章"),
            record(2, 7, now - 1_000, "读了十分钟"),
            record(3, 7, now - 2_000, "做了摘要"),
        )
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(goal))), emptyList(), emptyList(),
            rows, today, now, ZoneId.of("Asia/Shanghai"))
        assertEquals(listOf(2L, 3L), result.recentRecords.map { it.id })
    }

    @Test fun mixedOrAmbiguousGoalsAreNotPretendedToBeOneRecordingTarget() {
        val a = goal(1, "阅读")
        val b = goal(2, "理财")
        val result = buildTodayHomePresentation(GoalSnapshot(roots = listOf(node(a), node(b))),
            emptyList(), emptyList(), emptyList(), today, now, ZoneId.of("Asia/Shanghai"))
        assertNull(result.recordTargetGoalId)
        assertTrue(result.needsGoalChoice)
        assertEquals(2, result.goals.size)
    }
}
