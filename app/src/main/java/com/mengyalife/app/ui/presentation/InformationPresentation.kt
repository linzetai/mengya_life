package com.mengyalife.app.ui.presentation

import com.mengyalife.app.data.local.care.CareDecisionEntity
import com.mengyalife.app.data.local.care.CareOpportunityEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.insight.InsightInput
import com.mengyalife.app.domain.insight.InsightReference
import com.mengyalife.app.domain.insight.InsightReport
import com.mengyalife.app.domain.insight.InsightSourceRef
import com.mengyalife.app.ui.care.CareCard

enum class InformationBindingOrigin { UNKNOWN, CAPTURED_WITH_RESULT, CURRENT_REVALIDATION }

/** Original capture and a current exact-version revalidation are explicitly distinguished. */
data class InformationBinding(
    val dataVersion: String? = null,
    val sourceFingerprint: String? = null,
    val consent: AiConsentState? = null,
    val restoreGeneration: Long? = null,
    val timeWindowKey: String? = null,
    val origin: InformationBindingOrigin = InformationBindingOrigin.UNKNOWN,
)

data class InformationPeriod(val startAt: Long, val endAt: Long)

/** These are existing repository entities; this is not a second goal store. */
data class InformationContext(
    val goals: GoalSnapshot,
    val checkins: List<GoalCheckinEntity>,
    val plans: List<SmallPlanEntity>,
    val currentBinding: InformationBinding,
    val currentInsightInput: InsightInput? = null,
    val now: Long,
    /** Capture the display zone with the snapshot; resolver never reads a global zone. */
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

sealed interface InformationInput {
    data class Insight(
        val report: InsightReport,
        val insightId: String,
        val binding: InformationBinding,
    ) : InformationInput

    /** A null count means unknown (including unauthorized), never zero. */
    data class Plan(
        val planId: Long,
        val progressCount: Int? = null,
        val period: InformationPeriod? = null,
    ) : InformationInput

    /** Identity comes from the actual opportunity, not card wording or sourceType. */
    data class Care(
        val card: CareCard,
        val opportunity: CareOpportunityEntity?,
        val decision: CareDecisionEntity?,
        val binding: InformationBinding,
        val suppressed: Boolean,
        val feedbackKind: String? = null,
    ) : InformationInput
}

enum class InformationSubjectType { GOAL, PLAN, MULTIPLE_GOALS, COMPOSITE, GENERAL }
enum class InformationGoalRelation { NOT_APPLICABLE, LINKED, INDEPENDENT, MISSING }
data class InformationGoalIdentity(val id: Long, val currentName: String?)

data class InformationSubject(
    val type: InformationSubjectType,
    val stableId: Long?,
    val currentName: String?,
    val parentGoal: InformationGoalIdentity? = null,
    val linkedGoal: InformationGoalIdentity? = null,
    val goalRelation: InformationGoalRelation = InformationGoalRelation.NOT_APPLICABLE,
    val details: List<InformationSubject> = emptyList(),
    val displayName: String,
)

/** Raw, validated snapshot endpoints for adaptive UI; never parsed from display text. */
data class InformationSnapshotPoint(
    val recordId: Long,
    val value: Double,
    val unit: String,
    val recordedAt: Long,
)

data class InformationSnapshotComparison(
    val first: InformationSnapshotPoint,
    val last: InformationSnapshotPoint,
)

data class InformationObservation(
    val factId: String?,
    val text: String,
    val period: InformationPeriod?,
    val sources: List<InsightSourceRef>,
    /** Stale evidence is not a current conclusion. */
    val isCurrent: Boolean,
    /** Full original rule fact for evidence detail; text is the readable observation. */
    val originalText: String? = null,
    val comparison: InformationSnapshotComparison? = null,
)

data class InformationSuggestion(val text: String, val isInference: Boolean = true)

/** Navigation only. No command here grants permission to mutate anything. */
sealed interface InformationNextAction {
    data class OpenGoal(val goalId: Long) : InformationNextAction
    data class OpenPlan(val planId: Long) : InformationNextAction
    data class OpenSubjects(val subjects: List<InformationSubject>) : InformationNextAction
    data class DiscussInsight(val reference: InsightReference) : InformationNextAction
    data class OpenCareConversation(val opportunityId: Long, val decisionId: Long) : InformationNextAction
    data class Reevaluate(
        val reference: InsightReference? = null,
        val opportunityId: Long? = null,
    ) : InformationNextAction
    data object None : InformationNextAction
}

enum class InformationState {
    VALID, STALE, MISSING_SOURCE, MISSING_FACT, INSUFFICIENT_DATA, CROSS_DAY,
    AUTHORIZATION_CHANGED, RESTORE_CHANGED, MISSING_CONTEXT, AMBIGUOUS_SUBJECT,
    INACTIVE, UNRELIABLE_FACT,
}

data class InformationEvidence(
    val factIds: List<String> = emptyList(),
    /** Original labels/IDs are kept verbatim for audit, never used as current object names. */
    val sourceRefs: List<InsightSourceRef> = emptyList(),
    val binding: InformationBinding,
    val reportReference: InsightReference? = null,
    val opportunityId: Long? = null,
    val decisionId: Long? = null,
    val decisionSourceReference: String? = null,
)

data class InformationPresentation(
    val subject: InformationSubject,
    val observation: List<InformationObservation>,
    val suggestion: InformationSuggestion?,
    val nextAction: InformationNextAction,
    val evidence: InformationEvidence,
    val state: InformationState,
    val issues: Set<InformationState>,
)
