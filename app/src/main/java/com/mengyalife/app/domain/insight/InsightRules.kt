package com.mengyalife.app.domain.insight

import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

object InsightRules {
    private val json = Json

    fun build(
        snapshot: AdvisorContextSnapshot,
        consent: AiConsentState,
        now: Long,
    ): InsightInput {
        val facts = mutableListOf<InsightFact>()
        val allowed = mutableListOf<InsightSourceRef>()
        val periodStart = now - 30L * 24 * 60 * 60 * 1000
        val goalsById = snapshot.goals.associateBy { it.id }

        snapshot.goals.takeIf { consent.enabled(ConsentCategory.GOALS) }?.forEach { goal ->
            allowed.add(InsightSourceRef("GOAL", goal.id.toString(), goal.title))
        }
        snapshot.checkins.takeIf { consent.enabled(ConsentCategory.PROGRESS) }?.forEach { checkin ->
            allowed.add(
                InsightSourceRef(
                    "CHECKIN",
                    checkin.id.toString(),
                    goalsById[checkin.goalId]?.title ?: "目标#${checkin.goalId}",
                ),
            )
        }
        snapshot.growthMemories.takeIf { consent.enabled(ConsentCategory.JOURNAL) }?.forEach { memory ->
            allowed.add(InsightSourceRef("MEMORY", memory.id, memory.theme))
        }
        snapshot.journals.takeIf { consent.enabled(ConsentCategory.JOURNAL) }?.forEach { journal ->
            allowed.add(InsightSourceRef("JOURNAL", journal.id.toString(), journal.title ?: "无标题日记"))
        }

        if (consent.enabled(ConsentCategory.GOALS) && snapshot.goals.isNotEmpty()) {
            val active = snapshot.goals.filter { it.status == GoalStatus.IN_PROGRESS }
            facts += InsightFact(
                "goal-count",
                "共有 ${snapshot.goals.size} 个目标，其中进行中 ${active.size} 个（长期 ${snapshot.goals.count { it.level == GoalLevel.LONG_TERM }} / 阶段 ${snapshot.goals.count { it.level == GoalLevel.STAGE }} / 行动 ${snapshot.goals.count { it.level == GoalLevel.ACTION }}）。",
                snapshot.goals.take(6).map { InsightSourceRef("GOAL", it.id.toString(), it.title) },
            )
            snapshot.goals.filter {
                it.dueDate != null && it.dueDate in now..(now + 14L * 24 * 60 * 60 * 1000) &&
                    it.status != GoalStatus.DONE && it.status != GoalStatus.CANCELLED
            }.take(3).forEach { goal ->
                facts += InsightFact(
                    "deadline-${goal.id}",
                    "目标「${goal.title}」将在 ${Timestamps.formatDate(goal.dueDate!!)} 到期，当前状态为 ${goal.status.label}。",
                    listOf(InsightSourceRef("GOAL", goal.id.toString(), goal.title)),
                )
            }
        }

        if (consent.enabled(ConsentCategory.PROGRESS) && snapshot.checkins.isNotEmpty()) {
            val recent = snapshot.checkins.filter { it.recordedAt >= periodStart }
            val grouped = snapshot.checkins.groupBy { it.goalId }
            facts += InsightFact(
                "progress-count",
                "进展记录：全部历史 ${snapshot.checkins.size} 条，最近 30 天 ${recent.size} 条，覆盖 ${grouped.size} 个目标。",
                snapshot.checkins.sortedBy { it.recordedAt }.takeLast(8).map {
                    InsightSourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                },
            )
            // 同目标 + 同单位才比较；不同单位绝不相加/换算
            val series = snapshot.checkins
                .filter { it.metricValue != null && !it.metricUnit.isNullOrBlank() }
                .groupBy { it.goalId to it.metricUnit.orEmpty() }
            series.forEach { (key, records) ->
                if (records.size >= 2) {
                    val ordered = records.sortedBy { it.recordedAt }
                    val first = ordered.first().metricValue!!
                    val last = ordered.last().metricValue!!
                    val delta = last - first
                    val direction = when {
                        delta > 0 -> "上升"
                        delta < 0 -> "下降"
                        else -> "持平"
                    }
                    facts += InsightFact(
                        "trend-${key.first}-${key.second}",
                        "目标「${goalsById[key.first]?.title ?: "#${key.first}"}」的「${key.second}」记录从 ${Timestamps.formatDate(ordered.first().recordedAt)} 的 ${MetricFormat.withUnit(first, key.second)} 变为 ${Timestamps.formatDate(ordered.last().recordedAt)} 的 ${MetricFormat.withUnit(last, key.second)}（$direction ${MetricFormat.withUnit(kotlin.math.abs(delta), key.second)}，${ordered.size} 个点；区间 ${Timestamps.formatDate(ordered.first().recordedAt)} – ${Timestamps.formatDate(ordered.last().recordedAt)}）。",
                        // 有界来源：首点 + 最近 6 点，保证首尾都能核对
                        (listOf(ordered.first()) + ordered.takeLast(6))
                            .distinctBy { it.id }
                            .map {
                                InsightSourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                            },
                    )
                }
            }
            val units = snapshot.checkins.mapNotNull { it.metricUnit?.takeIf { u -> u.isNotBlank() } }.distinct()
            if (units.size > 1) {
                facts += InsightFact(
                    "units-separate",
                    "数值记录包含 ${units.joinToString("、")} 等不同单位；系统按单位分开计算，不做跨单位相加或换算。",
                    snapshot.checkins.sortedBy { it.recordedAt }.takeLast(6).map {
                        InsightSourceRef("CHECKIN", it.id.toString(), goalsById[it.goalId]?.title ?: "目标#${it.goalId}")
                    },
                )
            }
        }

        if (consent.enabled(ConsentCategory.GOALS) && consent.enabled(ConsentCategory.PROGRESS)) {
            val childrenByParent = snapshot.goals.mapNotNull { goal ->
                goal.parentId?.let { it to goal.id }
            }.groupBy({ it.first }, { it.second })
            fun goalAndDescendants(rootId: Long): Set<Long> {
                val result = linkedSetOf(rootId)
                val pending = mutableListOf(rootId)
                while (pending.isNotEmpty()) {
                    val parent = pending.removeAt(pending.lastIndex)
                    childrenByParent[parent].orEmpty().forEach { child ->
                        if (result.add(child)) pending += child
                    }
                }
                return result
            }
            snapshot.goals
                .filter { it.status == GoalStatus.IN_PROGRESS }
                .sortedBy { it.id }
                .take(4)
                .forEach { goal ->
                    val scopeIds = goalAndDescendants(goal.id)
                    val latest = snapshot.checkins.filter { it.goalId in scopeIds }.maxByOrNull { it.recordedAt }
                    if (latest == null || latest.recordedAt < periodStart) {
                        val detail = latest?.let {
                            "最近可见记录为 ${Timestamps.formatDate(it.recordedAt)}。"
                        } ?: "目前没有找到进展记录。"
                        val refs = listOf(InsightSourceRef("GOAL", goal.id.toString(), goal.title)) +
                            listOfNotNull(latest?.let {
                                val sourceGoal = goalsById[it.goalId]?.title ?: goal.title
                                InsightSourceRef("CHECKIN", it.id.toString(), sourceGoal)
                            })
                        facts += InsightFact(
                            id = "no-recent-progress-${goal.id}",
                            text = "系统记录显示，最近 30 天没有目标「${goal.title}」及其子行动的新进展记录。$detail 这只说明没有记录，不代表没有执行。",
                            sourceRefs = refs,
                        )
                    }
                }
        }

        if (consent.enabled(ConsentCategory.JOURNAL) && snapshot.growthMemories.isNotEmpty()) {
            facts += InsightFact(
                "memory-count",
                "有 ${snapshot.growthMemories.size} 条可用成长记忆，覆盖主题：${snapshot.growthMemories.joinToString("、") { it.theme }}。",
                snapshot.growthMemories.take(4).map {
                    InsightSourceRef("MEMORY", it.id, it.theme)
                },
            )
            // 用户纠正优先；同主题只取纠正版本，避免旧 AI 记忆覆盖用户事实
            val correctedThemes = snapshot.growthMemories.filter { it.corrected }.map { it.theme }.toSet()
            snapshot.growthMemories
                .filter { it.corrected || it.theme !in correctedThemes }
                .take(3)
                .forEach { memory ->
                    val rawDiaryRefs = memory.sourceDiaryIds.mapNotNull { id ->
                        val journal = snapshot.journals.firstOrNull { it.id == id } ?: return@mapNotNull null
                        InsightSourceRef("JOURNAL", id.toString(), journal.title ?: "无标题日记")
                    }
                    facts += InsightFact(
                        "memory-content-${memory.id}",
                        "成长记忆「${memory.theme}」${if (memory.corrected) "（用户纠正，优先）" else ""}：${memory.content.take(240)}" +
                            "（来源 ${memory.sourceDiaryIds.size} 篇日记）",
                        listOf(InsightSourceRef("MEMORY", memory.id, memory.theme)) + rawDiaryRefs,
                        isInference = memory.isInference && !memory.corrected,
                    )
                }
        }

        // 只有一个目标、没有任何进展/记忆，不能算“数据充分”
        val hasEnough = (consent.enabled(ConsentCategory.PROGRESS) && snapshot.checkins.size >= 2) ||
            (consent.enabled(ConsentCategory.JOURNAL) && snapshot.growthMemories.isNotEmpty())
        if (facts.isEmpty()) {
            facts += InsightFact(
                "insufficient",
                "数据不足：目前没有足够的目标、进展或成长记忆可供可靠分析。",
                emptyList(),
            )
        } else if (!hasEnough) {
            facts += InsightFact(
                "insufficient",
                "数据不足：记录还很少，以下只能作为方向性观察，不能当作结论。",
                emptyList(),
            )
        }

        val version = dataVersion(snapshot, consent, now)
        return InsightInput(
            dataVersion = version,
            periodStart = periodStart,
            periodEnd = now,
            facts = facts,
            insights = emptyList(),
            allowedSources = allowed.distinctBy { it.type + ":" + it.recordId },
            hasEnoughData = hasEnough,
        )
    }

    fun dataVersion(
        snapshot: AdvisorContextSnapshot,
        consent: AiConsentState,
        now: Long = System.currentTimeMillis(),
    ): String {
        val root = buildJsonObject {
            put("categories", consent.categories.sortedBy { it.ordinal }.joinToString(","))
            put("consentGranted", consent.granted)
            put("consentEpoch", consent.epoch)
            put("consentRecipient", AiConsentRules.normalizeRecipient(consent.recipient))
            put("timeBucket", now / (24L * 60 * 60 * 1000))
            put("goals", buildJsonObject {
                snapshot.goals.sortedBy { it.id }.forEach { goal ->
                    put(goal.id.toString(), "${goal.title}|${goal.description}|${goal.level}|${goal.domain}|${goal.status}|${goal.startDate}|${goal.dueDate}|${goal.createdAt}|${goal.updatedAt}|${goal.parentId}")
                }
            })
            put("checkins", buildJsonObject {
                snapshot.checkins.sortedBy { it.id }.forEach { checkin ->
                    put(checkin.id.toString(), "${checkin.goalId}|${checkin.recordedAt}|${checkin.note}|${checkin.metricValue}|${checkin.metricUnit}")
                }
            })
            put("memories", buildJsonObject {
                snapshot.growthMemories.sortedBy { it.id }.forEach { memory ->
                    put(memory.id, "${memory.theme}|${memory.content}|${memory.isInference}|${memory.corrected}|${memory.insufficient}|${memory.recentChange}|${memory.sourceDiaryIds}")
                }
            })
            put("journals", buildJsonObject {
                if (consent.enabled(ConsentCategory.JOURNAL)) {
                    snapshot.journals.sortedBy { it.id }.forEach { journal ->
                        put(journal.id.toString(), "${journal.title}|${journal.body}|${journal.updatedAt}")
                    }
                }
            })
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(root.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
