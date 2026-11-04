package com.mengyalife.app.domain.weekly

import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps
import java.time.ZoneId
import java.util.UUID

/**
 * 本地周回顾规则：
 * - 不调用 AI，不做后台网络；
 * - 只看本地可访问数据；
 * - 所有陈述来自确定性规则；不足明确说明；
 * - 不同单位不比较/相加；同一天的数据不描述为长期趋势。
 *
 * 隐私策略：AI 总授权开启时，尊重 GOALS/PROGRESS/JOURNAL 分类；
 * AI 总授权关闭时，纯本地回顾仍可用（但绝不发送数据给模型）。
 */
object WeeklyReviewRules {
    const val VERSION = 1
    private const val DAY = 24L * 60 * 60 * 1000

    fun build(
        snapshot: WeeklySnapshot,
        consent: AiConsentState,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeeklyReview {
        val window = WeekWindow.current(now, zone)
        val useAllLocal = !consent.granted
        val goalsAllowed = useAllLocal || consent.enabled(ConsentCategory.GOALS)
        val progressAllowed = useAllLocal || consent.enabled(ConsentCategory.PROGRESS)
        val journalAllowed = useAllLocal || consent.enabled(ConsentCategory.JOURNAL)

        val goals = if (goalsAllowed) snapshot.goals else emptyList()
        val checkins = if (progressAllowed) snapshot.checkins else emptyList()
        val memories = if (journalAllowed) snapshot.memories else emptyList()
        val goalsById = goals.associateBy { it.id }
        val current = checkins.filter { it.recordedAt in window.start until window.end }
        val previous = checkins.filter { it.recordedAt in window.previousStart until window.previousEnd }
        val currentMemories = if (journalAllowed) memories.filter {
            it.status in setOf("ACTIVE", "CORRECTED") && it.updatedAt >= window.previousStart
        } else emptyList()
        val dueSoon = goals.filter {
            it.dueDate != null && it.dueDate in now..(now + 7 * DAY) &&
                it.status != WeeklyGoalStatus.DONE && it.status != WeeklyGoalStatus.CANCELLED
        }

        val changes = mutableListOf<WeeklyFact>()
        val wins = mutableListOf<WeeklyFact>()
        val attention = mutableListOf<WeeklyFact>()
        val next = mutableListOf<WeeklyFact>()

        if (progressAllowed) {
            changes += WeeklyFact(
                "week-counts",
                "本周 ${current.size} 条进展，上周 ${previous.size} 条。" +
                    if (current.isEmpty() && previous.isEmpty()) "目前没有可比较的进展记录。" else "",
                (current + previous).takeLast(8).map {
                    WeeklySourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                },
            )
            val grouped = current.groupBy { it.goalId to it.unit.orEmpty() }
            grouped.forEach { (key, list) ->
                val numeric = list.filter { it.metricValue != null && !it.unit.isNullOrBlank() }
                    .sortedBy { it.recordedAt }
                if (numeric.size >= 2 && numeric.first().recordedAt != numeric.last().recordedAt) {
                    val first = numeric.first()
                    val last = numeric.last()
                    val delta = last.metricValue!! - first.metricValue!!
                    changes += WeeklyFact(
                        "week-trend-${key.first}-${key.second}",
                        "目标「${goalsById[key.first]?.title ?: "#${key.first}"}」本周同单位「${key.second}」从 " +
                            "${MetricFormat.withUnit(first.metricValue, key.second)} 变为 ${MetricFormat.withUnit(last.metricValue, key.second)}" +
                            "（${if (delta >= 0) "上升" else "下降"} ${MetricFormat.withUnit(kotlin.math.abs(delta), key.second)}，" +
                            "${numeric.size} 个点；${Timestamps.formatDate(first.recordedAt)} – ${Timestamps.formatDate(last.recordedAt)}" +
                            (if (Timestamps.formatDate(first.recordedAt) == Timestamps.formatDate(last.recordedAt)) {
                                "；同日记录只说明当周变化，不代表长期趋势）。"
                            } else "）。" ),
                        numeric.takeLast(6).map {
                            WeeklySourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                        },
                    )
                } else if (numeric.size == 1) {
                    changes += WeeklyFact(
                        "week-single-${key.first}-${key.second}",
                        "目标「${goalsById[key.first]?.title ?: "#${key.first}"}」本周只有 1 个「${key.second}」数值点，" +
                            "不足以判断趋势。",
                        numeric.map {
                            WeeklySourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                        },
                    )
                }
            }
            val units = current.mapNotNull { it.unit?.takeIf { u -> u.isNotBlank() } }.distinct()
            if (units.size > 1) {
                changes += WeeklyFact(
                    "week-units",
                    "本周数值包含不同单位（${units.joinToString("、")}），系统按单位分开计算，不做相加或换算。",
                    current.takeLast(6).map {
                        WeeklySourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                    },
                )
            }
        }

        if (goalsAllowed && goals.isNotEmpty()) {
            val active = goals.filter { it.status == WeeklyGoalStatus.IN_PROGRESS }
            wins += WeeklyFact(
                "goals-active",
                "当前有 ${goals.size} 个目标，其中进行中 ${active.size} 个；本周有进展记录的目标 ${current.map { it.goalId }.distinct().size} 个。",
                goals.take(6).map { WeeklySourceRef("GOAL", it.id.toString(), it.title) },
            )
            if (current.isNotEmpty()) {
                wins += WeeklyFact(
                    "wins-progress",
                    "本周留下了 ${current.size} 条可回看的进展记录。",
                    current.take(6).map {
                        WeeklySourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                    },
                )
            }
        }

        if (journalAllowed) {
            val corrected = currentMemories.filter { it.corrected }
            val visibleMemories = currentMemories
                .filter { it.corrected || it.theme !in corrected.map { c -> c.theme }.toSet() }
                .take(3)
            visibleMemories.forEach { memory ->
                val text = "成长记忆「${memory.theme}」${if (memory.corrected) "（用户纠正，优先）" else ""}：" +
                    memory.content.take(180)
                if (memory.corrected) wins += WeeklyFact(
                    "memory-${memory.id}",
                    text,
                    listOf(WeeklySourceRef("MEMORY", memory.id, memory.theme)),
                ) else changes += WeeklyFact(
                    "memory-${memory.id}",
                    text,
                    listOf(WeeklySourceRef("MEMORY", memory.id, memory.theme)),
                )
            }
        }

        dueSoon.take(3).forEach { goal ->
            attention += WeeklyFact(
                "due-${goal.id}",
                "目标「${goal.title}」将在 7 天内到期（${Timestamps.formatDate(goal.dueDate!!)}），当前状态为 ${goal.status.name}。",
                listOf(WeeklySourceRef("GOAL", goal.id.toString(), goal.title)),
            )
        }
        if (progressAllowed && current.isEmpty()) {
            attention += WeeklyFact(
                "no-progress",
                if (previous.isEmpty()) "数据不足：本周和上周都没有进展记录，无法判断变化。"
                else "本周还没有进展记录；上周有 ${previous.size} 条，变化暂不可判断。",
                previous.takeLast(3).map {
                    WeeklySourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                },
            )
        }
        if (goalsAllowed && goals.isEmpty() && current.isEmpty() && currentMemories.isEmpty()) {
            attention += WeeklyFact(
                "insufficient-all",
                "数据不足：没有目标、进展或成长记忆，本周回顾不形成结论。",
                emptyList(),
            )
        }

        // 下一步建议：本地规则，不调用 AI
        if (current.isEmpty()) {
            next += WeeklyFact(
                "next-record",
                "本周先记录 1 条具体进展（行动或数值），下周才有可比较的变化。",
                goals.take(2).map { WeeklySourceRef("GOAL", it.id.toString(), it.title) },
            )
        } else {
            next += WeeklyFact(
                "next-continue",
                "从本周有进展的目标中选一个，写下下一步可执行动作。",
                current.takeLast(3).map {
                    WeeklySourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                },
            )
        }
        dueSoon.firstOrNull()?.let { goal ->
            next += WeeklyFact(
                "next-due",
                "为「${goal.title}」确认剩余步骤或调整期限。",
                listOf(WeeklySourceRef("GOAL", goal.id.toString(), goal.title)),
            )
        }

        val allFacts = changes + wins + attention + next
        return WeeklyReview(
            id = UUID.randomUUID().toString(),
            periodKey = window.key,
            periodStart = window.start,
            periodEnd = window.end,
            generatedAt = now,
            version = VERSION,
            facts = allFacts,
            sections = WeeklySections(changes, wins, attention, next),
        )
    }
}
