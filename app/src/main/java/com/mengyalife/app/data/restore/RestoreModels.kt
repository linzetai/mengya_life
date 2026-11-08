package com.mengyalife.app.data.restore

import com.mengyalife.app.data.backup.BackupFormatV2
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RestoreFileInfo(
    val name: String,
    val sizeBytes: Long,
)

data class RestoreTableCount(
    val key: String,
    val label: String,
    val backupCount: Int,
    val currentCount: Int,
    val willRestore: Boolean,
)

data class RestoreWarning(
    val code: String,
    val message: String,
    val count: Int = 1,
)

data class RestoreRepair(
    val code: String,
    val table: String,
    val action: String,
    val count: Int,
    val ids: List<String> = emptyList(),
)

data class RestorePreview(
    val backupId: String,
    val createdAt: Long,
    val sourcePackage: String,
    val sourceQa: Boolean,
    val sourceVersionName: String,
    val sourceVersionCode: Long,
    val backupFormatVersion: Int,
    val dbSchemaVersion: Int,
    val currentDbSchemaVersion: Int,
    val fileSizeBytes: Long,
    val encrypted: Boolean,
    val kdfIterations: Int,
    val payloadSha256: String,
    val counts: List<RestoreTableCount>,
    val warnings: List<RestoreWarning>,
    val repairs: List<RestoreRepair>,
    val notRestored: List<String>,
    val canExecute: Boolean = false,
    val executed: Boolean = false,
) {
    val backupTimeLabel: String
        get() = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(createdAt))
}

class RestoreException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object RestoreTables {
    const val WEEKLY_AI = "weeklyAiReviews"
    val ALL: List<String> = BackupFormatV2.TABLE_KEYS
    val RESTORED: List<String> = ALL - WEEKLY_AI
    /** 0.21+ 才出现的表：旧 v2 备份可完全缺失 counts/data。 */
    val OPTIONAL: Set<String> = setOf(
        "smallPlans", "planEvents", "planProgress",
        "careOpportunities", "careDecisions", "careFeedback", "carePreferences", "recordProposals",
    )
    val LABELS = linkedMapOf(
        "journalEntries" to "日记",
        "goals" to "目标",
        "goalCheckins" to "进展",
        "advisorMessages" to "对话",
        "goalProposals" to "草案",
        "growthSignals" to "成长线索",
        "growthMemories" to "成长记忆",
        "insightReports" to "洞察",
        "weeklyReviews" to "周回顾",
        "weeklyAiReviews" to "历史 AI 周报（仅归档）",
    )
    val SQL_TABLES = linkedMapOf(
        "journalEntries" to "journal_entries",
        "goals" to "goals",
        "goalCheckins" to "goal_checkins",
        "advisorMessages" to "advisor_messages",
        "goalProposals" to "goal_proposals",
        "growthSignals" to "growth_signals",
        "growthMemories" to "growth_memories",
        "insightReports" to "insight_reports",
        "weeklyReviews" to "weekly_reviews",
        "weeklyAiReviews" to "weekly_ai_reviews",
        "smallPlans" to "small_plans",
        "planEvents" to "plan_events",
        "planProgress" to "plan_progress",
        "careOpportunities" to "care_opportunities",
        "careDecisions" to "care_decisions",
        "careFeedback" to "care_feedback",
        "carePreferences" to "care_preferences",
        "recordProposals" to "record_proposals",
    )
}
