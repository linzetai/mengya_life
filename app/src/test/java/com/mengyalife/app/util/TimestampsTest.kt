package com.mengyalife.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimestampsTest {

    // 2026-11-02T04:00:00Z
    private val epochMillis = Instant.parse("2026-11-02T04:00:00Z").toEpochMilli()

    @Test
    fun `按东八区展示日期与时间`() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        assertEquals("2026年11月2日 12:00", Timestamps.formatDateTime(epochMillis, shanghai))
        assertEquals("2026年11月2日", Timestamps.formatDate(epochMillis, shanghai))
        assertEquals("12:00", Timestamps.formatTime(epochMillis, shanghai))
    }

    @Test
    fun `日期字段固定按 UTC 渲染，避免跨时区偏移一天`() {
        // Material3 DatePicker 返回「UTC 零点」的毫秒数。
        val utcMidnight = Instant.parse("2026-12-31T00:00:00Z").toEpochMilli()
        assertEquals("2026年12月31日", Timestamps.formatDateUtc(utcMidnight))
        // 同一毫秒在纽约（UTC-5）用本地时区会变成 12 月 30 日——日期字段不能这样
        assertEquals("2026年12月30日", Timestamps.formatDate(utcMidnight, ZoneId.of("America/New_York")))
        assertEquals("2026年12月31日", Timestamps.formatDateUtc(utcMidnight))
    }

    @Test
    fun `同一时间戳在不同时区展示不同本地时间`() {
        // 存的是 UTC 毫秒，展示才带时区——这是本项目的明确约定
        assertEquals("2026年11月2日 04:00", Timestamps.formatDateTime(epochMillis, ZoneId.of("UTC")))
        // 2026 年美国夏令时于 11 月 1 日结束，11 月 2 日纽约已是 EST(UTC-5)：
        // 04:00Z - 5h = 前一日 23:00
        assertEquals("2026年11月1日 23:00", Timestamps.formatDateTime(epochMillis, ZoneId.of("America/New_York")))
    }
}
