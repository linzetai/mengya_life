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
)
