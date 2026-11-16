package com.mengyalife.app.ui.presentation

import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.util.MetricFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.SmallPlanStatus
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.domain.insight.InsightFact
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.domain.insight.InsightReference
import com.mengyalife.app.domain.insight.InsightSourceRef
import com.mengyalife.app.domain.insight.InsightType

/** Deterministic display projection. No clock, DAO, network, write, or authorization side effect. */
object InformationResolver {
    /**
     * Caller samples consent/generation around loading report + currentInput under existing fences.
     * This proves current applicability, NOT the report's unknown original epoch/generation.
     * Does not grant network/write authority; service must revalidate on click as before.
     */
    fun revalidatedInsightBinding(
        report: com.mengyalife.app.domain.insight.InsightReport,
        currentInput: com.mengyalife.app.domain.insight.InsightInput,
        beforeConsent: com.mengyalife.app.domain.advisor.AiConsentState,
        afterConsent: com.mengyalife.app.domain.advisor.AiConsentState,
        beforeGeneration: Long,
        afterGeneration: Long,
    ): InformationBinding? {
        if (!beforeConsent.granted || beforeConsent != afterConsent || beforeGeneration != afterGeneration ||
            report.dataVersion != currentInput.dataVersion || currentInput.sourceFingerprint.isBlank() ||
            !currentInput.dataVersion.startsWith(currentInput.sourceFingerprint + "|")) return null
        return InformationBinding(
            dataVersion = report.dataVersion, sourceFingerprint = currentInput.sourceFingerprint,
            consent = afterConsent, restoreGeneration = afterGeneration,
            timeWindowKey = currentInput.dataVersion.substringAfter('|'),
            origin = InformationBindingOrigin.CURRENT_REVALIDATION,
        )
    }

    fun resolve(input: InformationInput, context: InformationContext): InformationPresentation =
        Resolution(context).resolve(input)

    private class Resolution(private val context: InformationContext) {
        private val issues = linkedSetOf<InformationState>()
        private val goals = flatten(context.goals.roots).associateBy { it.id }
        private val plans = context.plans.associateBy { it.id }
        private val checkins = context.checkins.associateBy { it.id }

        fun resolve(input: InformationInput): InformationPresentation = when (input) {
            is InformationInput.Insight -> insight(input)
            is InformationInput.Plan -> plan(input)
            is InformationInput.Care -> care(input)
        }

        private fun flatten(nodes: List<GoalNode>): List<GoalEntity> =
            nodes.flatMap { listOf(it.goal) + flatten(it.children) }

        private fun general() = InformationSubject(
            InformationSubjectType.GENERAL, null, null, displayName = "综合观察",
        )

        private fun goal(id: Long): InformationSubject {
            val entity = goals[id]
            if (entity == null) issues += InformationState.MISSING_SOURCE
            val parent = entity?.parentId?.let { parentId ->
                if (goals[parentId] == null) issues += InformationState.MISSING_SOURCE
                InformationGoalIdentity(parentId, goals[parentId]?.title)
            }
            return InformationSubject(
                InformationSubjectType.GOAL, id, entity?.title, parentGoal = parent,
                displayName = entity?.title ?: "目标已删除或不可用",
            )
        }

        private fun planSubject(id: Long): InformationSubject {
            val entity = plans[id]
            if (entity == null) {
                issues += InformationState.MISSING_SOURCE
                return InformationSubject(InformationSubjectType.PLAN, id, null,
                    goalRelation = InformationGoalRelation.MISSING, displayName = "计划已删除或不可用")
            }
            val linked = entity.goalId?.let { goalId ->
                if (goals[goalId] == null) issues += InformationState.MISSING_SOURCE
                InformationGoalIdentity(goalId, goals[goalId]?.title)
            }
            val relation = when {
                entity.goalId == null -> InformationGoalRelation.INDEPENDENT
                linked?.currentName == null -> InformationGoalRelation.MISSING
                else -> InformationGoalRelation.LINKED
            }
            return InformationSubject(
                InformationSubjectType.PLAN, id, entity.title,
                parentGoal = entity.goalId?.let { goals[it]?.parentId }?.let { parentId ->
                    if (goals[parentId] == null) issues += InformationState.MISSING_SOURCE
                    InformationGoalIdentity(parentId, goals[parentId]?.title)
                },
                linkedGoal = linked, goalRelation = relation,
                displayName = entity.title + " · " + when (relation) {
                    InformationGoalRelation.INDEPENDENT -> "独立计划"
                    InformationGoalRelation.MISSING -> "关联目标已删除或不可用"
                    else -> linked?.currentName.orEmpty()
                },
            )
        }

        private fun subjects(refs: List<InsightSourceRef>): InformationSubject {
            val details = refs.mapNotNull { ref ->
                when (ref.type.uppercase()) {
                    "GOAL" -> ref.recordId.toLongOrNull()?.let(::goal)
                        ?: run { issues += InformationState.MISSING_SOURCE; null }
                    "CHECKIN", "PROGRESS", "GOAL_CHECKIN" -> {
                        val record = ref.recordId.toLongOrNull()?.let(checkins::get)
                        if (record == null) { issues += InformationState.MISSING_SOURCE; null }
                        else goal(record.goalId)
                    }
                    "PLAN", "ACTION_PLAN" -> ref.recordId.toLongOrNull()?.let(::planSubject)
                        ?: run { issues += InformationState.MISSING_SOURCE; null }
                    else -> null // Diaries/memories have no authoritative goal association here.
                }
            }.distinctBy { it.type to it.stableId }
            if (details.isEmpty()) {
                issues += InformationState.AMBIGUOUS_SUBJECT
                return general()
            }
            if (details.size == 1) return details.single()
            // Parent and child stay distinct; never choose one or collapse into the root.
            val goalIds = details.flatMap { subject ->
                if (subject.type == InformationSubjectType.GOAL) listOfNotNull(subject.stableId)
                else listOfNotNull(subject.linkedGoal?.id)
            }.toSet()
            return InformationSubject(
                if (goalIds.size > 1) InformationSubjectType.MULTIPLE_GOALS else InformationSubjectType.COMPOSITE,
                null, null, details = details,
                displayName = if (goalIds.size > 1) "涉及多个目标" else "涉及多个对象",
            )
        }

        private fun binding(bound: InformationBinding) {
            val current = context.currentBinding
            if (bound.origin == InformationBindingOrigin.UNKNOWN ||
                bound.consent == null || current.consent == null ||
                bound.restoreGeneration == null || current.restoreGeneration == null
            ) issues += InformationState.MISSING_CONTEXT
            if (bound.consent != null && current.consent != null &&
                (!current.consent.granted || bound.consent != current.consent)
            ) issues += InformationState.AUTHORIZATION_CHANGED
            if (bound.restoreGeneration != null && current.restoreGeneration != null &&
                bound.restoreGeneration != current.restoreGeneration
            ) issues += InformationState.RESTORE_CHANGED
            if (bound.timeWindowKey != null && current.timeWindowKey != null &&
                bound.timeWindowKey != current.timeWindowKey
            ) issues += InformationState.CROSS_DAY
            if (bound.sourceFingerprint != null && current.sourceFingerprint != null &&
                bound.sourceFingerprint != current.sourceFingerprint
            ) issues += InformationState.STALE
            if (bound.dataVersion != null && current.dataVersion != null &&
                bound.dataVersion != current.dataVersion && InformationState.CROSS_DAY !in issues
            ) issues += InformationState.STALE
        }

        private fun insight(input: InformationInput.Insight): InformationPresentation {
            val report = input.report
            val reference = InsightReference(report.id, report.dataVersion, input.insightId)
            val insight = report.insights.singleOrNull { it.id == input.insightId }
            binding(input.binding)
            val current = context.currentInsightInput
            if (current == null) issues += InformationState.MISSING_CONTEXT
            if (input.binding.dataVersion != report.dataVersion) issues += InformationState.MISSING_CONTEXT
            if (current != null && current.dataVersion != report.dataVersion) issues += InformationState.STALE
            if (report.status != InsightReportStatus.READY) issues += InformationState.STALE
            if (current?.hasEnoughData == false || report.facts.any { it.id == "insufficient" }) {
                issues += InformationState.INSUFFICIENT_DATA
            }
            if (insight?.type == InsightType.SUGGESTION && !insight.isInference) {
                issues += InformationState.UNRELIABLE_FACT
            }
            val ids = insight?.factIds.orEmpty()
            val facts = ids.distinct().mapNotNull { id -> report.facts.singleOrNull { it.id == id } }
            if (insight == null || ids.isEmpty() || facts.size != ids.distinct().size) {
                issues += InformationState.MISSING_FACT
            }
            if (current != null && facts.any { old -> current.facts.singleOrNull { it.id == old.id } != old }) {
                issues += InformationState.STALE
            }
            val refs = insight?.sources.orEmpty() + facts.flatMap { it.sourceRefs }
            val allowedKeys = current?.allowedSources.orEmpty().map { it.type to it.recordId }.toSet()
            val factKeys = facts.flatMap { it.sourceRefs }.map { it.type to it.recordId }.toSet()
            if (refs.isEmpty() || facts.any { it.sourceRefs.isEmpty() } ||
                refs.any { (it.type to it.recordId) !in allowedKeys } ||
                insight?.sources.isNullOrEmpty() ||
                insight?.sources.orEmpty().any { (it.type to it.recordId) !in factKeys }
            ) issues += InformationState.MISSING_SOURCE
            val period = InformationPeriod(report.periodStart, report.periodEnd)
            val unreliable = facts.filter { !reliableFact(it, period) }.map { it.id }.toSet()
            if (unreliable.isNotEmpty()) issues += InformationState.UNRELIABLE_FACT
            val subject = subjects(refs)
            markInactive(subject)
            val evidence = InformationEvidence(ids, refs, input.binding, reportReference = reference)
            val mayShow = InformationState.AUTHORIZATION_CHANGED !in issues && InformationState.RESTORE_CHANGED !in issues
            val observation = if (!mayShow) emptyList() else facts.filter {
                !it.isInference && it.id !in unreliable && it.sourceRefs.isNotEmpty() &&
                    it.sourceRefs.all { ref -> (ref.type to ref.recordId) in allowedKeys }
            }.map { observation(it, period) }
            val suggestion = insight?.takeIf { issues.isEmpty() && it.type == InsightType.SUGGESTION }
                ?.let { InformationSuggestion(it.body) }
            val action = when {
                issues.isNotEmpty() -> InformationNextAction.Reevaluate(reference)
                subject.details.isNotEmpty() -> InformationNextAction.OpenSubjects(subject.details)
                suggestion != null -> InformationNextAction.DiscussInsight(reference)
                subject.type == InformationSubjectType.GOAL -> InformationNextAction.OpenGoal(subject.stableId!!)
                subject.type == InformationSubjectType.PLAN -> InformationNextAction.OpenPlan(subject.stableId!!)
                else -> InformationNextAction.Reevaluate(reference)
            }
            return result(subject, observation, suggestion, action, evidence)
        }

        private fun trendRecords(fact: InsightFact): List<GoalCheckinEntity> =
            fact.sourceRefs.mapNotNull { ref ->
                if (ref.type.uppercase() in setOf("CHECKIN", "PROGRESS", "GOAL_CHECKIN"))
                    ref.recordId.toLongOrNull()?.let(checkins::get) else null
            }

        /** Existing Rules trend text subtracts snapshots, never safely describes deltas/mixed series. */
        private fun reliableFact(fact: InsightFact, period: InformationPeriod): Boolean {
            if (!fact.id.startsWith("trend-")) return !fact.isInference
            val records = trendRecords(fact)
            if (records.size != fact.sourceRefs.size || records.distinctBy { it.id }.size < 2) return false
            if (records.any { it.amountKind != ProgressKind.SNAPSHOT || it.metricValue?.isFinite() != true ||
                    it.metricUnit.isNullOrBlank() || it.recordedAt !in period.startAt..period.endAt }) return false
            if (records.map { it.goalId to it.metricUnit }.distinct().size != 1) return false
            // Rules uses all-history goal+unit series, even when bounded refs hide a mixed middle row.
            val first = records.first()
            val fullSeries = context.checkins.filter { it.goalId == first.goalId && it.metricUnit == first.metricUnit }
            return fullSeries.all { it.amountKind == ProgressKind.SNAPSHOT && it.metricValue?.isFinite() == true &&
                it.recordedAt in period.startAt..period.endAt }
        }

        /** Only called for already validated facts. Never parse a title, user name, or numeric prose. */
        private fun observation(fact: InsightFact, reportPeriod: InformationPeriod): InformationObservation {
            if (!fact.id.startsWith("trend-") || issues.isNotEmpty()) {
                return InformationObservation(fact.id, fact.text, reportPeriod, fact.sourceRefs,
                    issues.isEmpty(), originalText = fact.text)
            }
            val ordered = trendRecords(fact).distinctBy { it.id }
                .sortedWith(compareBy<GoalCheckinEntity> { it.recordedAt }.thenBy { it.id })
            val first = ordered.first()
            val last = ordered.last()
            val firstDate = Instant.ofEpochMilli(first.recordedAt).atZone(context.zone)
            val lastDate = Instant.ofEpochMilli(last.recordedAt).atZone(context.zone)
            val formatter = DateTimeFormatter.ofPattern(
                if (firstDate.year == lastDate.year) "M月d日" else "yyyy年M月d日", Locale.CHINA)
            fun value(record: GoalCheckinEntity) = MetricFormat.number(record.metricValue!!) + record.metricUnit
            val text = "${formatter.format(firstDate)}累计${value(first)} → " +
                "${formatter.format(lastDate)}累计${value(last)}"
            fun point(record: GoalCheckinEntity) = InformationSnapshotPoint(
                recordId = record.id, value = record.metricValue!!,
                unit = record.metricUnit!!, recordedAt = record.recordedAt,
            )
            return InformationObservation(fact.id, text,
                InformationPeriod(first.recordedAt, last.recordedAt), fact.sourceRefs,
                issues.isEmpty(), originalText = fact.text,
                comparison = InformationSnapshotComparison(point(first), point(last)))
        }

        private fun markInactive(subject: InformationSubject) {
            val items = subject.details.ifEmpty { listOf(subject) }
            if (items.any { item ->
                    when (item.type) {
                        InformationSubjectType.PLAN -> plans[item.stableId]?.status != SmallPlanStatus.ACTIVE
                        InformationSubjectType.GOAL -> goals[item.stableId]?.status?.let {
                            it == GoalStatus.DONE || it == GoalStatus.CANCELLED || it == GoalStatus.PAUSED
                        } == true
                        else -> false
                    }
                }) issues += InformationState.INACTIVE
        }

        private fun plan(input: InformationInput.Plan): InformationPresentation {
            val subject = planSubject(input.planId)
            markInactive(subject)
            val entity = plans[input.planId]
            if (input.period != null && context.now !in input.period.startAt..input.period.endAt) {
                issues += InformationState.STALE
            }
            val refs = entity?.let { listOf(InsightSourceRef("PLAN", it.id.toString(), it.title)) }.orEmpty()
            val observation = entity?.let {
                listOf(InformationObservation(null,
                    "计划「${it.title}」 · ${SmallPlanStatus.label(it.status)} · ${it.timeScope ?: "时间未定"}" +
                        (input.progressCount?.let { count -> " · 已有 $count 次执行记录（不代表实际执行总数）" } ?: " · 执行记录数未知"),
                    input.period, refs, true))
            }.orEmpty()
            if (input.progressCount != null && input.progressCount < 0) issues += InformationState.UNRELIABLE_FACT
            return result(subject, observation,
                if (issues.isEmpty()) InformationSuggestion("可以聊聊这个计划的进展，由你决定是否继续。") else null,
                if (issues.isEmpty()) InformationNextAction.OpenPlan(input.planId) else InformationNextAction.None,
                InformationEvidence(sourceRefs = refs, binding = context.currentBinding))
        }

        private fun care(input: InformationInput.Care): InformationPresentation {
            binding(input.binding)
            val opp = input.opportunity
            val decision = input.decision
            if (opp == null || opp.id != input.card.opportunityId) issues += InformationState.MISSING_SOURCE
            if (decision == null || decision.opportunityId != opp?.id || decision.id != input.card.decisionId ||
                decision.action != "SEND" || !decision.modelOk
            ) issues += InformationState.MISSING_SOURCE
            val refs = if (opp == null || opp.id != input.card.opportunityId) emptyList() else buildList {
                opp.planId?.let { add(InsightSourceRef("PLAN", it.toString(), opp.title)) }
                // A linked plan already retains its goal. Do not artificially double-count it.
                if (opp.planId == null) opp.goalId?.let { add(InsightSourceRef("GOAL", it.toString(), opp.title)) }
            }
            if (opp?.planId != null && plans[opp.planId]?.goalId != opp.goalId) issues += InformationState.STALE
            if (opp != null && ((opp.type == "PLAN_CHECKIN" && opp.sourceId != opp.planId) ||
                    (opp.type == "GOAL_STALL" && opp.sourceId != opp.goalId))) issues += InformationState.STALE
            if (opp != null && (context.now < opp.windowStart || context.now > opp.windowEnd ||
                    context.now > opp.expiresAt)) issues += InformationState.STALE
            if (input.suppressed || input.feedbackKind != null ||
                opp?.status !in setOf("SENT", "PENDING", "PLANNED")) issues += InformationState.INACTIVE
            val subject = subjects(refs)
            markInactive(subject)
            val evidence = InformationEvidence(sourceRefs = refs, binding = input.binding,
                opportunityId = input.card.opportunityId, decisionId = input.card.decisionId,
                decisionSourceReference = decision?.sourceRefs)
            val observation = if (opp != null && issues.isEmpty()) listOf(InformationObservation(
                null, "有一条来自真实${if (opp.planId != null) "计划" else "目标"}的关心机会；不表示尚未执行。",
                InformationPeriod(opp.windowStart, opp.windowEnd), refs, true)) else emptyList()
            return result(subject, observation,
                if (issues.isEmpty()) InformationSuggestion(input.card.text) else null,
                if (issues.isEmpty() && decision != null) InformationNextAction.OpenCareConversation(
                    input.card.opportunityId, decision.id) else InformationNextAction.Reevaluate(
                    opportunityId = input.card.opportunityId), evidence)
        }

        private fun result(
            subject: InformationSubject,
            observation: List<InformationObservation>,
            suggestion: InformationSuggestion?,
            action: InformationNextAction,
            evidence: InformationEvidence,
        ) = InformationPresentation(subject, observation, suggestion, action, evidence,
            issues.firstOrNull() ?: InformationState.VALID, issues.toSet())
    }
}
