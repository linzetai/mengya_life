package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.weekly.WeekWindow
import com.mengyalife.app.domain.weekly.WeeklyCheckin
import com.mengyalife.app.domain.weekly.WeeklyGoal
import com.mengyalife.app.domain.weekly.WeeklyGoalLevel
import com.mengyalife.app.domain.weekly.WeeklyGoalStatus
import com.mengyalife.app.domain.weekly.WeeklyMemory
import com.mengyalife.app.domain.weekly.WeeklyReviewRules
import com.mengyalife.app.domain.weekly.WeeklySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeeklyReviewRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 11, 4).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    private val monday = LocalDate.of(2026, 11, 2).atStartOfDay(zone).toInstant().toEpochMilli()
    private val consent = AiConsentState(
        granted = true,
        textVersion = AiConsentRules.TEXT_VERSION,
        categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
        recipient = "https://api.example.com/v1",
        categories = AiConsentRules.allCategories,
        epoch = 1,
    )
    private val goal = WeeklyGoal(1, "长期储蓄", WeeklyGoalLevel.LONG_TERM, WeeklyGoalStatus.IN_PROGRESS, null, now + 3 * 86400000, now)
    private val checkins = listOf(
        WeeklyCheckin(1, 1, monday + 1000, "周一", 1000.0, "CNY"),
        WeeklyCheckin(2, 1, monday + 2000, "周二", 1500.0, "CNY"),
    )

    @Test
    fun `本周与上周时间边界正确`() {
        val w = WeekWindow.current(now, zone)
        assertEquals(monday, w.start)
        assertEquals(LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant().toEpochMilli(), w.previousStart)
        assertEquals("2026-W45", w.key)
    }

    @Test
    fun `跨年周与上周正确`() {
        val cross = LocalDate.of(2027, 1, 1).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        val w = WeekWindow.current(cross, zone)
        assertEquals("2026-W53", w.key)
        assertEquals(LocalDate.of(2026, 12, 28).atStartOfDay(zone).toInstant().toEpochMilli(), w.start)
    }

    @Test
    fun `无记录明确数据不足`() {
        val review = WeeklyReviewRules.build(WeeklySnapshot(), consent, now, zone)
        assertTrue(review.sections.attention.any { it.text.contains("数据不足") })
        assertFalse(review.sections.changes.any { it.id.startsWith("week-trend") })
    }

    @Test
    fun `同单位多点给出趋势，不同单位分开`() {
        val extra = checkins + WeeklyCheckin(3, 1, monday + 3000, "美元", 100.0, "USD")
        val review = WeeklyReviewRules.build(
            WeeklySnapshot(goals = listOf(goal), checkins = extra), consent, now, zone,
        )
        assertTrue(review.sections.changes.any { it.id == "week-trend-1-CNY" })
        assertTrue(review.sections.changes.any { it.id == "week-units" && it.text.contains("USD") })
        assertFalse(review.facts.any { it.text.contains("1100 CNY") || it.text.contains("1600 USD") })
    }

    @Test
    fun `单点不描述为趋势`() {
        val review = WeeklyReviewRules.build(
            WeeklySnapshot(goals = listOf(goal), checkins = listOf(checkins[0])), consent, now, zone,
        )
        assertTrue(review.sections.changes.any { it.id == "week-single-1-CNY" && it.text.contains("不足以判断趋势") })
        assertFalse(review.sections.changes.any { it.id.startsWith("week-trend") })
    }

    @Test
    fun `到期目标关注，已完成和已取消不进入`() {
        val due = goal.copy(id = 2, title = "到期目标", dueDate = now + 86400000)
        val done = goal.copy(id = 3, title = "已完成", status = WeeklyGoalStatus.DONE, dueDate = now + 86400001)
        val cancelled = goal.copy(id = 4, title = "已取消", status = WeeklyGoalStatus.CANCELLED, dueDate = now + 86400002)
        val review = WeeklyReviewRules.build(
            WeeklySnapshot(goals = listOf(due, done, cancelled)), consent, now, zone,
        )
        assertTrue(review.sections.attention.any { it.id == "due-2" })
        assertFalse(review.sections.attention.any { it.id == "due-3" || it.id == "due-4" })
    }

    @Test
    fun `关闭AI类别仍保留本地回顾数据`() {
        val noProgress = consent.copy(categories = consent.categories - ConsentCategory.PROGRESS)
        val review = WeeklyReviewRules.build(WeeklySnapshot(goals = listOf(goal), checkins = checkins), noProgress, now, zone)
        assertTrue(review.facts.any { it.sources.any { s -> s.type == "CHECKIN" } })
        assertTrue(review.sections.changes.any { it.text.contains("条进展") })
    }

    @Test
    fun `关闭全部AI授权仍可生成本地回顾`() {
        val review = WeeklyReviewRules.build(WeeklySnapshot(goals = listOf(goal), checkins = checkins), AiConsentState(), now, zone)
        assertTrue(review.facts.any { it.id == "week-counts" })
    }

    @Test
    fun `用户纠正记忆优先且旧记忆不展示`() {
        val old = WeeklyMemory("old", "职业成长", "旧内容", false, "ACTIVE", listOf(1), now)
        val corrected = WeeklyMemory("new", "职业成长", "用户纠正内容", true, "CORRECTED", listOf(1), now)
        val review = WeeklyReviewRules.build(
            WeeklySnapshot(goals = listOf(goal), checkins = checkins, memories = listOf(old, corrected)), consent, now, zone,
        )
        assertTrue(review.facts.any { it.id == "memory-new" && it.text.contains("用户纠正内容") })
        assertFalse(review.facts.any { it.id == "memory-old" })
    }
}
