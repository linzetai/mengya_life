package com.mengyalife.app.domain.weekly

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class WeeklyGoalLevel { LONG_TERM, STAGE, ACTION }
enum class WeeklyGoalStatus { NOT_STARTED, IN_PROGRESS, PAUSED, DONE, CANCELLED }

data class WeeklySourceRef(val type: String, val recordId: String, val label: String)
data class WeeklyFact(val id: String, val text: String, val sources: List<WeeklySourceRef> = emptyList())
data class WeeklySections(
    val changes: List<WeeklyFact>,
    val wins: List<WeeklyFact>,
    val attention: List<WeeklyFact>,
    val nextSteps: List<WeeklyFact>,
)
data class WeeklyReview(
    val id: String,
    val periodKey: String,
    val periodStart: Long,
    val periodEnd: Long,
    val generatedAt: Long,
    val version: Int,
    val dataVersion: String,
    val aiConsentVersion: String,
    val facts: List<WeeklyFact>,
    val sections: WeeklySections,
)

data class WeeklyGoal(
    val id: Long,
    val title: String,
    val level: WeeklyGoalLevel,
    val status: WeeklyGoalStatus,
    val startDate: Long?,
    val dueDate: Long?,
    val updatedAt: Long,
)
data class WeeklyCheckin(
    val id: Long,
    val goalId: Long,
    val recordedAt: Long,
    val note: String,
    val metricValue: Double?,
    val unit: String?,
)
data class WeeklyMemory(
    val id: String,
    val theme: String,
    val content: String,
    val corrected: Boolean,
    val status: String,
    val sourceDiaryIds: List<Long>,
    val updatedAt: Long,
)
data class WeeklySnapshot(
    val goals: List<WeeklyGoal> = emptyList(),
    val checkins: List<WeeklyCheckin> = emptyList(),
    val memories: List<WeeklyMemory> = emptyList(),
)

data class WeekPeriod(
    val start: Long,
    val end: Long,
    val previousStart: Long,
    val previousEnd: Long,
    val key: String,
)

object WeekWindow {
    fun current(now: Long, zone: ZoneId = ZoneId.systemDefault()): WeekPeriod {
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        val monday = nowZ.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate().atStartOfDay(zone)
        val prev = monday.minusWeeks(1)
        val iso = monday.toLocalDate().let { date ->
            val week = date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
            val year = date.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
            "%d-W%02d".format(year, week)
        }
        return WeekPeriod(
            start = monday.toInstant().toEpochMilli(),
            end = now,
            previousStart = prev.toInstant().toEpochMilli(),
            previousEnd = monday.toInstant().toEpochMilli(),
            key = iso,
        )
    }
}
