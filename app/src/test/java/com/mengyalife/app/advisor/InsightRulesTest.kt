package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.advisor.ContextGrowthMemory
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.insight.InsightRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightRulesTest {
    private val now = 1_800_000_000_000L
    private val consent = AiConsentState(
        granted = true,
        textVersion = AiConsentRules.TEXT_VERSION,
        categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
        recipient = "https://api.example.com/v1",
        categories = AiConsentRules.allCategories,
        epoch = 1,
    )
    private val goal = ContextGoalRecord(
        1, "长期储蓄", "为未来储备", GoalLevel.LONG_TERM, null,
        GoalDomain.WEALTH, GoalStatus.IN_PROGRESS, now - 10_000,
    )
    private val checkins = listOf(
        ContextCheckinRecord(1, 1, now - 3_000, "第一期", 1000.0, "CNY"),
        ContextCheckinRecord(2, 1, now - 2_000, "第二期", 1500.0, "CNY"),
        ContextCheckinRecord(3, 1, now - 1_000, "第三期", 2000.0, "CNY"),
    )

    @Test
    fun `同单位趋势只在本单位内比较`() {
        val input = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal), checkins = checkins), consent, now)
        assertTrue(input.facts.any { it.id == "trend-1-CNY" && it.text.contains("上升") })
        assertTrue(input.facts.any { it.id == "progress-count" })
        assertTrue(input.allowedSources.any { it.type == "CHECKIN" && it.recordId == "3" })
    }

    @Test
    fun `不同单位不直接相加或跨单位比较`() {
        val mixed = checkins + ContextCheckinRecord(4, 1, now - 500, "美元", 100.0, "USD")
        val input = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal), checkins = mixed), consent, now)
        assertTrue(input.facts.any { it.id == "units-separate" })
        assertFalse(input.facts.any { it.text.contains("2100") || it.text.contains("1100 CNY") })
        assertTrue(input.facts.any { it.id == "trend-1-CNY" })
        assertFalse(input.facts.any { it.id == "trend-1-USD" }) // 单个 USD 点不构成趋势
    }

    @Test
    fun `职业目标与成长记忆进入事实且保留来源`() {
        val career = ContextGoalRecord(
            2, "职业发展", null, GoalLevel.LONG_TERM, null,
            GoalDomain.CAREER, GoalStatus.IN_PROGRESS, now - 20_000,
        )
        val memory = ContextGrowthMemory(
            id = "m1", theme = "职业成长", content = "持续承担项目汇报",
            isInference = false, corrected = true, insufficient = false,
            recentChange = "最近一次记录：客户反馈", sourceDiaryIds = listOf(10L, 11L),
        )
        val input = InsightRules.build(
            AdvisorContextSnapshot(goals = listOf(career), growthMemories = listOf(memory)),
            consent, now,
        )
        assertTrue(input.facts.any { it.id == "memory-count" && it.text.contains("职业成长") })
        assertTrue(input.facts.any { it.id == "memory-content-m1" && it.text.contains("用户纠正") })
        assertTrue(input.facts.any { it.id == "memory-content-m1" && it.text.contains("持续承担项目汇报") })
        assertTrue(input.allowedSources.any { it.type == "MEMORY" && it.recordId == "m1" })
    }

    @Test
    fun `只有一个目标没有进展时仍是不足`() {
        val input = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal)), consent, now)
        assertFalse(input.hasEnoughData)
        assertTrue(input.facts.any { it.id == "insufficient" })
    }

    @Test
    fun `趋势来源必须包含最新点`() {
        val many = (1..8).map {
            ContextCheckinRecord(it.toLong(), 1, now - (9 - it) * 1000L, "第${it}期", it * 100.0, "CNY")
        }
        val input = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal), checkins = many), consent, now)
        val trend = input.facts.first { it.id == "trend-1-CNY" }
        assertTrue(trend.sourceRefs.any { it.recordId == "8" })
        assertTrue(trend.text.contains("区间"))
    }

    @Test
    fun `同主题用户纠正记忆优先且旧记忆内容不进入事实`() {
        val old = ContextGrowthMemory("old", "职业成长", "旧 AI 内容", false, false, false, null, listOf(1L))
        val new = ContextGrowthMemory("new", "职业成长", "用户纠正内容", false, true, false, null, listOf(1L))
        val input = InsightRules.build(
            AdvisorContextSnapshot(goals = listOf(goal), growthMemories = listOf(old, new)), consent, now,
        )
        assertTrue(input.facts.any { it.id == "memory-content-new" && it.text.contains("用户纠正内容") })
        assertFalse(input.facts.any { it.id == "memory-content-old" })
    }

    @Test
    fun `目标描述和日期分桶改变 dataVersion`() {
        val base = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal), checkins = checkins), consent, now)
        val changed = InsightRules.build(
            AdvisorContextSnapshot(goals = listOf(goal.copy(description = "新的描述")), checkins = checkins),
            consent, now,
        )
        val nextDay = InsightRules.build(
            AdvisorContextSnapshot(goals = listOf(goal), checkins = checkins), consent, now + 24L * 60 * 60 * 1000,
        )
        assertNotEquals(base.dataVersion, changed.dataVersion)
        assertNotEquals(base.dataVersion, nextDay.dataVersion)
    }

    @Test
    fun `无数据明确写信息不足`() {
        val input = InsightRules.build(AdvisorContextSnapshot(), consent, now)
        assertFalse(input.hasEnoughData)
        assertTrue(input.facts.any { it.id == "insufficient" && it.text.contains("数据不足") })
    }

    @Test
    fun `数据版本随数据或权限改变`() {
        val a = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal), checkins = checkins), consent, now)
        val b = InsightRules.build(
            AdvisorContextSnapshot(goals = listOf(goal), checkins = checkins.dropLast(1)), consent, now,
        )
        val noGoals = consent.copy(categories = consent.categories - ConsentCategory.GOALS)
        val c = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal), checkins = checkins), noGoals, now)
        assertNotEquals(a.dataVersion, b.dataVersion)
        assertNotEquals(a.dataVersion, c.dataVersion)
    }
}
