package com.mengyalife.app.ui.insight

import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.insight.InsightFact
import com.mengyalife.app.domain.insight.InsightReport
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightSourceRef

/** Only a report already viewed under the unchanged authorization may remain as history. */
internal fun historicalInsightEvidence(
    previous: InsightReport?,
    evidenceEpoch: Long?,
    before: AiConsentState,
    after: AiConsentState,
): InsightReport? = previous.takeIf {
    before.granted && after.granted && before == after && evidenceEpoch == after.epoch
}

/** Presentation only: never changes a report or its evidence. */
internal fun distinctInsightCards(insights: List<GrowthInsight>): List<GrowthInsight> =
    insights.distinctBy { it.title.trim().replace(Regex("\\s+"), " ") }

/** Do not recite evidence already quoted verbatim in the explanation; keep every other fact. */
internal fun additionalInsightFacts(body: String, facts: List<InsightFact>): List<InsightFact> {
    fun normalized(text: String) = text.replace(Regex("\\s+"), "")
    val explanation = normalized(body)
    return facts.distinctBy { normalized(it.text) }.filter {
        normalized(it.text).let { text -> text.isEmpty() || !explanation.contains(text) }
    }
}

internal fun friendlyInsightSource(source: InsightSourceRef): String {
    val kind = when (source.type.uppercase()) {
        "GOAL" -> "目标"
        "PROGRESS", "CHECKIN", "GOAL_CHECKIN" -> "进展记录"
        "JOURNAL", "JOURNAL_ENTRY", "DIARY" -> "日记"
        "MEMORY", "GROWTH_MEMORY" -> "成长记忆"
        "PLAN", "ACTION_PLAN" -> "行动计划"
        else -> "原始记录"
    }
    return source.label.trim().takeIf { it.isNotEmpty() }?.let { "$kind · $it" } ?: kind
}
