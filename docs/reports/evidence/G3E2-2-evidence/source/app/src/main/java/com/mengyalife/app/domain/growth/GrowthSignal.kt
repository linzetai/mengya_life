package com.mengyalife.app.domain.growth

/** 日记提取的成长线索分类。 */
enum class GrowthCategory(val label: String) {
    EVENT("事件"),
    PROGRESS("进展"),
    BLOCKER("阻碍"),
    HABIT("习惯"),
    EMOTION("情绪"),
    ACTION_CANDIDATE("可行动"),
}

enum class GrowthSignalStatus {
    ACTIVE,
    CORRECTED,
    DELETED,
    STALE,
}

enum class GrowthSignalOrigin {
    AI,
    USER,
}

/** 日记分析状态（存 journal_entries.analysis_status）。 */
object DiaryAnalysisStatus {
    const val PENDING = "PENDING"
    const val ANALYZING = "ANALYZING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"

    fun label(raw: String): String = when (raw) {
        PENDING -> "待分析"
        ANALYZING -> "分析中"
        COMPLETED -> "已完成"
        FAILED -> "失败"
        else -> "待分析"
    }
}

data class GrowthSignal(
    val id: String,
    val sourceType: String,
    val sourceId: Long,
    val sourceVersion: String,
    val category: GrowthCategory,
    val content: String,
    val evidence: String,
    val isInference: Boolean,
    val confidence: Double?,
    val extractedAt: Long,
    val status: GrowthSignalStatus,
    val origin: GrowthSignalOrigin,
    val sourceTitle: String?,
    val updatedAt: Long,
)
