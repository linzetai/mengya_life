package com.mengyalife.app.domain.insight

enum class InsightType(val label: String) {
    POSITIVE("做得好"),
    RISK("需关注"),
    SUGGESTION("下一步建议"),
}

enum class InsightReportStatus { READY, FAILED }

data class InsightSourceRef(
    val type: String,
    val recordId: String,
    val label: String,
)

data class InsightFact(
    val id: String,
    val text: String,
    val sourceRefs: List<InsightSourceRef>,
    val isInference: Boolean = false,
)

data class GrowthInsight(
    val id: String,
    val type: InsightType,
    val title: String,
    val body: String,
    val isInference: Boolean,
    val confidence: Double?,
    val sources: List<InsightSourceRef>,
    val factIds: List<String> = emptyList(),
)

/** Durable pointer carried by the single advisor conversation; never stores a second insight copy. */
data class InsightReference(
    val reportId: String,
    val dataVersion: String,
    val insightId: String,
)

data class ResolvedInsightReference(
    val reference: InsightReference,
    val insight: GrowthInsight,
    val facts: List<InsightFact>,
)

sealed interface InsightReferenceResolution {
    data class Current(val value: ResolvedInsightReference) : InsightReferenceResolution
    data class Stale(val reason: String) : InsightReferenceResolution
}

data class InsightReport(
    val id: String,
    val dataVersion: String,
    val status: InsightReportStatus,
    val summary: String,
    val facts: List<InsightFact>,
    val insights: List<GrowthInsight>,
    val periodStart: Long,
    val periodEnd: Long,
    val generatedAt: Long,
    val error: String? = null,
    val model: String? = null,
)

data class InsightInput(
    val dataVersion: String,
    val periodStart: Long,
    val periodEnd: Long,
    val facts: List<InsightFact>,
    val insights: List<GrowthInsight>,
    val allowedSources: List<InsightSourceRef>,
    val hasEnoughData: Boolean,
    /** Hash of authorized source facts, excluding the observation-window date. */
    val sourceFingerprint: String = "",
)
