package com.mengyalife.app.care

import com.mengyalife.app.domain.care.CarePolicy
import com.mengyalife.app.domain.care.CarePreferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * care 时间判断的 API 26 兼容修复回归。
 *
 * 原实现使用 `LocalTime.ofInstant` / `LocalDate.ofInstant`（API 31/34），
 * minSdk 26 上会抛 NoSuchMethodError；这里锁定等价语义。
 */
class CareTimeApi26Test {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val utc = ZoneId.of("UTC")

    @Test
    fun `免打扰按设备时区判断`() {
        // 2026-11-10 15:30Z = 上海 23:30（在 22–8 内），UTC 15:30（不在）
        val now = Instant.parse("2026-11-10T15:30:00Z").toEpochMilli()
        assertTrue(CarePolicy.inDnd(now, CarePolicy.Prefs(), shanghai))
        assertFalse(CarePolicy.inDnd(now, CarePolicy.Prefs(), utc))
    }

    @Test
    fun `免打扰结束时刻是开区间`() {
        // 上海 08:00 整不处于免打扰
        val now = Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()
        assertFalse(CarePolicy.inDnd(now, CarePolicy.Prefs(), shanghai))
        // 上海 07:59 仍处于免打扰
        assertTrue(CarePolicy.inDnd(now - 60_000L, CarePolicy.Prefs(), shanghai))
    }

    @Test
    fun `免打扰起始时刻是闭区间`() {
        // 上海 22:00 整进入免打扰
        val now = Instant.parse("2026-11-10T14:00:00Z").toEpochMilli()
        assertTrue(CarePolicy.inDnd(now, CarePolicy.Prefs(), shanghai))
        // 上海 21:59 不在免打扰
        assertFalse(CarePolicy.inDnd(now - 60_000L, CarePolicy.Prefs(), shanghai))
    }

    @Test
    fun `周结束时间按本地周日 23-59 计算`() {
        val now = Instant.parse("2026-11-10T03:00:00Z").toEpochMilli() // 上海周二 11:00
        val actual = CarePreferenceParser.endOfWeek(now, shanghai)
        val expected = Instant.parse("2026-11-15T15:59:00Z").toEpochMilli() // 上海周日 23:59
        val diff = kotlin.math.abs(actual - expected)
        assertTrue("expected≈$expected actual=$actual", diff <= 60_000L)
        val local = Instant.ofEpochMilli(actual).atZone(shanghai)
        assertEquals(LocalTime.of(23, 59), local.toLocalTime())
        assertEquals(7, local.dayOfWeek.value)
    }
}
