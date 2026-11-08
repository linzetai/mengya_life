package com.mengyalife.app.data.backup

import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.local.advisor.AdvisorMessageEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.growth.GrowthMemoryEntity
import com.mengyalife.app.data.local.growth.GrowthSignalEntity
import com.mengyalife.app.data.local.insight.InsightReportEntity
import com.mengyalife.app.data.local.proposal.GoalProposalEntity
import com.mengyalife.app.data.local.weekly.WeeklyAiEntity
import com.mengyalife.app.data.local.weekly.WeeklyReviewEntity

/** 10 张业务表的完整快照；统计与编码只依赖这一层。 */
data class BackupSnapshot(
    val journalEntries: List<JournalEntryEntity>,
    val goals: List<GoalEntity>,
    val goalCheckins: List<GoalCheckinEntity>,
    val advisorMessages: List<AdvisorMessageEntity>,
    val goalProposals: List<GoalProposalEntity>,
    val growthSignals: List<GrowthSignalEntity>,
    val growthMemories: List<GrowthMemoryEntity>,
    val insightReports: List<InsightReportEntity>,
    val weeklyReviews: List<WeeklyReviewEntity>,
    val weeklyAiReviews: List<WeeklyAiEntity>,
    val smallPlans: List<com.mengyalife.app.data.local.plan.SmallPlanEntity> = emptyList(),
    val planEvents: List<com.mengyalife.app.data.local.plan.PlanEventEntity> = emptyList(),
    val planProgress: List<com.mengyalife.app.data.local.plan.PlanProgressEntity> = emptyList(),
    val careOpportunities: List<com.mengyalife.app.data.local.care.CareOpportunityEntity> = emptyList(),
    val careDecisions: List<com.mengyalife.app.data.local.care.CareDecisionEntity> = emptyList(),
    val careFeedback: List<com.mengyalife.app.data.local.care.CareFeedbackEntity> = emptyList(),
    val carePreferences: List<com.mengyalife.app.data.local.care.CarePreferenceEntity> = emptyList(),
    val recordProposals: List<com.mengyalife.app.data.local.proposal.RecordProposalEntity> = emptyList(),
) {
    val totalRows: Int
        get() = journalEntries.size + goals.size + goalCheckins.size + advisorMessages.size +
            goalProposals.size + growthSignals.size + growthMemories.size + insightReports.size +
            weeklyReviews.size + weeklyAiReviews.size + smallPlans.size + planEvents.size + planProgress.size +
            careOpportunities.size + careDecisions.size + careFeedback.size + carePreferences.size + recordProposals.size

    fun counts(): Map<String, Int> = linkedMapOf(
        "journalEntries" to journalEntries.size,
        "goals" to goals.size,
        "goalCheckins" to goalCheckins.size,
        "advisorMessages" to advisorMessages.size,
        "goalProposals" to goalProposals.size,
        "growthSignals" to growthSignals.size,
        "growthMemories" to growthMemories.size,
        "insightReports" to insightReports.size,
        "weeklyReviews" to weeklyReviews.size,
        "weeklyAiReviews" to weeklyAiReviews.size,
        "smallPlans" to smallPlans.size,
        "planEvents" to planEvents.size,
        "planProgress" to planProgress.size,
        "careOpportunities" to careOpportunities.size,
        "careDecisions" to careDecisions.size,
        "careFeedback" to careFeedback.size,
        "carePreferences" to carePreferences.size,
        "recordProposals" to recordProposals.size,
    )
}

/** 设置快照：D1；绝不包含 API Key、consent、fake engine。 */
data class BackupSettingsSnapshot(
    val themeMode: String,
    val weeklyReviewEnabled: Boolean,
    val weeklyAiEnabled: Boolean,
    val notificationAsked: Boolean,
    val aiEndpoint: String,
    val aiModel: String,
)

data class BackupAppInfo(
    val appPackage: String,
    val qaBuild: Boolean,
    val versionName: String,
    val versionCode: Long,
    val androidSdk: Int,
)

data class BackupCounts(val values: Map<String, Int>) {
    fun total(): Int = values.values.sum()
}

data class BackupArtifact(
    val bytes: ByteArray,
    val backupId: String,
    val createdAt: Long,
    val payloadSha256: String,
    val counts: BackupCounts,
    val sizeBytes: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is BackupArtifact && backupId == other.backupId && createdAt == other.createdAt &&
            payloadSha256 == other.payloadSha256 && counts == other.counts && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int =
        (((backupId.hashCode() * 31 + createdAt.hashCode()) * 31 + payloadSha256.hashCode()) * 31 +
            counts.hashCode()) * 31 + bytes.contentHashCode()
}

data class CryptoParams(
    val iterations: Int,
    val salt: ByteArray,
    val nonce: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is CryptoParams && iterations == other.iterations &&
            salt.contentEquals(other.salt) && nonce.contentEquals(other.nonce)

    override fun hashCode(): Int = (iterations * 31 + salt.contentHashCode()) * 31 + nonce.contentHashCode()
}

data class EncryptedPayload(val ciphertextWithTag: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is EncryptedPayload && ciphertextWithTag.contentEquals(other.ciphertextWithTag)

    override fun hashCode(): Int = ciphertextWithTag.contentHashCode()
}

enum class BackupFileType { V1_LEGACY, V2_ENCRYPTED, UNKNOWN }

/** 面向 UI 的稳定错误码，不包含敏感内容。 */
class BackupException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
