package com.mengyalife.app.advisor

import com.mengyalife.app.domain.care.CareTimePreferenceCodec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId

class CareTimePreferenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `decodes strict window and rejects invalid data`() {
        val w = CareTimePreferenceCodec.decode("SUN 20:00")!!
        assertEqualsDay(w.day)
        assertNull(CareTimePreferenceCodec.decode("SUNDAY 20:00"))
        assertNull(CareTimePreferenceCodec.decode("SUN 25:00"))
        assertNull(CareTimePreferenceCodec.decode("SUN"))
    }

    @Test
    fun `window contains only same weekday within slack`() {
        val w = CareTimePreferenceCodec.decode("SUN 20:00")!!
        val sunday20 = java.time.LocalDate.parse("2026-12-20").atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val sunday18 = java.time.LocalDate.parse("2026-12-20").atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val sunday23 = java.time.LocalDate.parse("2026-12-20").atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val monday20 = java.time.LocalDate.parse("2026-12-21").atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(w.contains(sunday20, zone))
        assertTrue(w.contains(sunday18, zone))
        assertFalse(w.contains(sunday23, zone))
        assertFalse(w.contains(monday20, zone))
    }

    @Test
    fun `time scope applies only to weekly review`() {
        assertTrue(com.mengyalife.app.domain.care.CareTimePreferenceScope.applies("WEEKLY_REVIEW"))
        assertFalse(com.mengyalife.app.domain.care.CareTimePreferenceScope.applies("PLAN_CHECKIN"))
        assertFalse(com.mengyalife.app.domain.care.CareTimePreferenceScope.applies("GOAL_STALL"))
    }

    private fun assertEqualsDay(day: DayOfWeek) = org.junit.Assert.assertEquals(DayOfWeek.SUNDAY, day)
}
