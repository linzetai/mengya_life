package com.mengyalife.app.domain.goals

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 相对时间的锚定：以计划创建时刻和用户时区为基准，避免“这周”跨周后漂移。
 * 只用于筛选/展示；用户原话仍保存在 time_scope。
 */
object PlanTime {

    data class Range(val startAt: Long, val endAt: Long, val label: String)

    fun resolve(scope: String?, createdAt: Long, zone: ZoneId = ZoneId.systemDefault()): Range? {
        val raw = scope?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val anchor = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
        val (start, end) = when {
            raw.contains("今天") -> anchor to anchor
            raw.contains("明天") -> anchor.plusDays(1) to anchor.plusDays(1)
            raw.contains("周末") -> {
                val saturday = anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                saturday to saturday.plusDays(1)
            }
            raw.contains("下周") -> {
                val monday = anchor.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                monday to monday.plusDays(6)
            }
            raw.contains("这周") || raw.contains("本周") -> {
                val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday to monday.plusDays(6)
            }
            raw.contains("月底") || raw.contains("本月") -> {
                val first = anchor.withDayOfMonth(1)
                first to first.withDayOfMonth(first.lengthOfMonth())
            }
            else -> return null
        }
        return Range(
            startAt = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            endAt = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            label = raw,
        )
    }

    fun isCurrent(scope: String?, createdAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val range = resolve(scope, createdAt, zone) ?: return false
        return now in range.startAt..range.endAt
    }
}
