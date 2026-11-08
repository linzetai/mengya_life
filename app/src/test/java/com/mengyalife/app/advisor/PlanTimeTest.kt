package com.mengyalife.app.advisor

import com.mengyalife.app.domain.goals.PlanTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PlanTimeTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(date: String): Long =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `this week is anchored to creation week, not current week`() {
        val created = at("2026-11-08") // Sunday, week of Nov 2-8
        assertTrue(PlanTime.isCurrent("这周", created, at("2026-11-08") + 10L * 3600_000, zone))
        // 下一周的周一，不再属于创建时的那一周
        assertFalse(PlanTime.isCurrent("这周", created, at("2026-11-09") + 10L * 3600_000, zone))
    }

    @Test
    fun `weekend and month scopes resolve from creation date`() {
        val created = at("2026-11-08")
        assertFalse(PlanTime.isCurrent("周末", created, at("2026-11-08") + 9L * 3600_000, zone))
        assertTrue(PlanTime.isCurrent("周末", created, at("2026-11-14") + 9L * 3600_000, zone))
        assertTrue(PlanTime.isCurrent("月底", created, at("2026-11-30") + 9L * 3600_000, zone))
    }

    @Test
    fun `unknown scope has no range`() {
        assertFalse(PlanTime.isCurrent("看情况", at("2026-11-08"), at("2026-11-08"), zone))
        assertFalse(PlanTime.isCurrent(null, at("2026-11-08"), at("2026-11-08"), zone))
    }
}
