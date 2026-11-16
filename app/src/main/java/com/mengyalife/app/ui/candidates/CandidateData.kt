package com.mengyalife.app.ui.candidates

import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.di.AppContainer
import com.mengyalife.app.domain.goals.PlanTime
import com.mengyalife.app.domain.insight.InsightReport
import com.mengyalife.app.domain.insight.InsightRules
import com.mengyalife.app.ui.care.CareCard
import com.mengyalife.app.ui.presentation.*
import kotlinx.coroutines.flow.first

/** Read-only adapter to the existing stores. No alternate conversation or goal database. */
data class CandidateSnapshot(
    val context: InformationContext,
    val report: InsightReport?,
    val observations: List<InformationPresentation>,
    val plans: List<InformationPresentation>,
    val care: InformationPresentation?,
    /** Null until the actual Room count query completes; absent keys then mean known zero. */
    val verifiedProgressCounts: Map<Long, Int>? = null,
)

suspend fun loadCandidateSnapshot(
    container: AppContainer,
    goals: GoalSnapshot,
    checkins: List<GoalCheckinEntity>,
    plans: List<SmallPlanEntity>,
    progressCounts: Map<Long, Int>,
    careCard: CareCard?,
    displayedReport: InsightReport? = null,
    previousSnapshot: CandidateSnapshot? = null,
    now: Long = System.currentTimeMillis(),
): CandidateSnapshot {
    val generation = container.restoreWriteBarrier.generation()
    return container.restoreWriteBarrier.withWrite(generation) {
        val before = container.aiConsentRepository.current()
        val input = container.insightService.currentInput()
        val currentReport = container.insightService.loadCurrent()
        // Reuse historical evidence only when it was already captured in this presentation session.
        val prior = previousSnapshot?.takeIf { it.context.currentBinding.consent == before }
        val historical = displayedReport?.takeIf { it.id == prior?.report?.id } ?: prior?.report
        val report = currentReport ?: historical
        val after = container.aiConsentRepository.current()
        val afterGeneration = container.restoreWriteBarrier.generation()
        val current = InformationBinding(
            dataVersion = input.dataVersion,
            sourceFingerprint = input.sourceFingerprint,
            consent = after,
            restoreGeneration = afterGeneration,
            timeWindowKey = input.dataVersion.substringAfter('|', ""),
            origin = InformationBindingOrigin.CAPTURED_WITH_RESULT,
        )
        val context = InformationContext(goals, checkins, plans, current, input, now)
        val reportBinding = report?.let {
            InformationResolver.revalidatedInsightBinding(it, input, before, after, generation, afterGeneration)
        } ?: prior?.observations?.firstOrNull()?.evidence?.binding?.takeIf { prior?.report?.id == report?.id }
        val observations = report?.insights.orEmpty().map { insight ->
            InformationResolver.resolve(
                InformationInput.Insight(report!!, insight.id, reportBinding ?: InformationBinding()),
                context,
            )
        }
        val verifiedCounts = container.smallPlanRepository.observeProgressCounts().first()
        val activePlans = plans.filter {
            it.status == "ACTIVE" && (it.timeScope.isNullOrBlank() || PlanTime.isCurrent(it.timeScope, it.createdAt, now))
        }.sortedByDescending { it.updatedAt }.map { plan ->
            InformationResolver.resolve(InformationInput.Plan(plan.id, verifiedCounts[plan.id] ?: 0), context)
        }
        val care = careCard?.let { card ->
            val opportunity = container.careRepository.find(card.opportunityId)
            val decision = container.careRepository.latestDecisionFor(card.opportunityId)
            val feedback = container.careRepository.latestFeedback(card.opportunityId)
            // Historical Care decisions lack captured validation provenance. Keep the real identity,
            // but never turn that unknown provenance into a currently authorized recommendation.
            InformationResolver.resolve(
                InformationInput.Care(card, opportunity, decision, InformationBinding(), feedback != null, feedback?.kind),
                context,
            )
        }
        CandidateSnapshot(context, report, observations, activePlans, care, verifiedCounts)
    }
}
