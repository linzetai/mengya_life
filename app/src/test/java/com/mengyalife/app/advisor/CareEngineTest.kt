package com.mengyalife.app.advisor

import com.mengyalife.app.domain.care.CareAction
import com.mengyalife.app.domain.care.CareCandidate
import com.mengyalife.app.domain.care.CareOpportunityFactory
import com.mengyalife.app.domain.care.CareParser
import com.mengyalife.app.domain.care.CarePolicy
import com.mengyalife.app.domain.care.CarePreferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CareEngineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = 1_760_000_000_000L
    private val candidate = CareCandidate(
        opportunityId = 3, type = "PLAN_CHECKIN", title = "练吉他", planId = 3, goalId = null,
        status = "PENDING", timeScope = "这周", progressCount = 0, lastProgressAt = null,
        windowEnd = now + 3600_000, expiresAt = now + 3600_000,
    )
    private val prefs = CarePolicy.Prefs()

    @Test
    fun `policy allows a fresh candidate`() {
        assertTrue(
            CarePolicy.allowSend(now, prefs, true, candidate, 0, null, null, false, 0, zone)
                is CarePolicy.Result.Allow,
        )
    }

    @Test
    fun `policy denies dnd revoked silence and daily limit`() {
        assertTrue(CarePolicy.inDnd(now, prefs.copy(dndStartHour = 0, dndEndHour = 23), zone))
        assertTrue(CarePolicy.allowSend(now, prefs, false, candidate, 0, null, null, false, 0, zone) is CarePolicy.Result.Deny)
        assertTrue(CarePolicy.allowSend(now, prefs, true, candidate, 0, null, null, true, 0, zone) is CarePolicy.Result.Deny)
        assertTrue(CarePolicy.allowSend(now, prefs, true, candidate, 1, null, null, false, 0, zone) is CarePolicy.Result.Deny)
    }

    @Test
    fun `decision budget denies further care`() {
        assertTrue(
            CarePolicy.allowSend(now, prefs, true, candidate, 0, null, null, false, 5, zone)
                is CarePolicy.Result.Deny,
        )
    }

    @Test
    fun `expired opportunity is denied`() {
        val expired = candidate.copy(expiresAt = now - 1, windowEnd = now - 1)
        assertTrue(CarePolicy.allowSend(now, prefs, true, expired, 0, null, null, false, 0, zone) is CarePolicy.Result.Deny)
    }

    @Test
    fun `parser accepts only provided candidates and valid actions`() {
        val ok = CareParser.parse(
            """{"action":"DROP","opportunityId":3,"reason":"计划已完成，不该催促"}""", setOf(3L),
        )
        assertEquals(CareAction.DROP, ok!!.action)
        assertNull(CareParser.parse("""{"action":"SEND","opportunityId":99,"reason":"x"}""", setOf(3L)))
        assertNull(CareParser.parse("""not json""", setOf(3L)))
    }

    @Test
    fun `natural language preferences parse pause silence time and busy`() {
        val plans = listOf(5L to "整理一次作品集", 4L to "整理一次简历")
        val pause = CarePreferenceParser.parse("这周比较忙，先别催我整理一次作品集。", plans, emptyList(), now, zone)!!
        assertEquals("BUSY", pause.kind)
        assertEquals("plan:5", pause.topicKey)
        assertTrue(pause.expiresAt != null)

        val silence = CarePreferenceParser.parse("以后别提醒我整理一次简历了。", plans, emptyList(), now, zone)!!
        assertEquals("SILENCED", silence.value)
        assertEquals("plan:4", silence.topicKey)

        val time = CarePreferenceParser.parse("我其实更喜欢周日晚上回顾这一周。", plans, emptyList(), now, zone)!!
        assertEquals("TIME", time.kind)
        assertEquals("SUN 20:00", time.windowNote)
    }

    @Test
    fun `parser rejects questions negations and supports revoke`() {
        val plans = listOf(5L to "整理一次作品集", 4L to "整理一次简历")
        assertNull(CarePreferenceParser.parse("你觉得我以后别提醒自己合理吗", plans, emptyList(), now, zone))
        assertNull(CarePreferenceParser.parse("我不喜欢周日晚上复盘", plans, emptyList(), now, zone))
        val silence = CarePreferenceParser.parse("以后别提醒简历", plans, emptyList(), now, zone)!!
        assertEquals("plan:4", silence.topicKey)
        val revoke = CarePreferenceParser.parse("我刚才说错了，恢复简历提醒", plans, emptyList(), now, zone)!!
        assertEquals("REVOKE", revoke.kind)
        assertEquals("plan:4", revoke.topicKey)
        val busy = CarePreferenceParser.parse("我最近比较忙", plans, emptyList(), now, zone)!!
        assertEquals("BUSY", busy.kind)
        assertNull(busy.topicKey)
    }

    @Test
    fun `future plans outside current week are not opportunities`() {
        val createdNextWeek = java.time.LocalDate.parse("2026-11-16")
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val plan = com.mengyalife.app.data.local.plan.SmallPlanEntity(
            id = 1, title = "练吉他", timeScope = "这周", goalId = null, status = "ACTIVE",
            sourceMessageId = "m", createdAt = createdNextWeek, updatedAt = createdNextWeek,
        )
        val drafts = CareOpportunityFactory.fromPlans(listOf(plan), emptyMap(), now, zone)
        assertTrue(drafts.isEmpty())
    }
}
