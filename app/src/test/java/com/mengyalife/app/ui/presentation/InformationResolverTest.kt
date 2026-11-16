package com.mengyalife.app.ui.presentation

import com.mengyalife.app.data.local.care.CareDecisionEntity
import com.mengyalife.app.data.local.care.CareOpportunityEntity
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.insight.*
import com.mengyalife.app.ui.care.CareCard
import org.junit.Assert.*
import org.junit.Test

class InformationResolverTest {
    private val consent = AiConsentState(granted = true, epoch = 7,
        categories = setOf(ConsentCategory.GOALS, ConsentCategory.PROGRESS), recipient = "https://example.test")
    private val binding = InformationBinding("hash|day", "hash", consent, 3, "day",
        InformationBindingOrigin.CAPTURED_WITH_RESULT)
    private fun goal(id: Long = 1, title: String = "当前目标", parent: Long? = null,
        status: GoalStatus = GoalStatus.IN_PROGRESS) = GoalEntity(id, title,
        level = if (parent == null) GoalLevel.LONG_TERM else GoalLevel.STAGE,
        parentId = parent, domain = GoalDomain.LIFE, status = status, createdAt = 1, updatedAt = 2)
    private fun node(goal: GoalEntity, children: List<GoalNode> = emptyList()) = GoalNode(goal, children, 0, null)
    private fun plan(id: Long = 20, goalId: Long? = 1, status: String = "ACTIVE") = SmallPlanEntity(
        id, "真实计划名", "本周", goalId, status, "message", 1, 2)
    private fun checkin(id: Long = 9, goalId: Long = 1, at: Long = 20,
        kind: String = "snapshot", unit: String = "小时") = GoalCheckinEntity(
        id, goalId, at, "真实记录", 2.0, unit, "manual", amountKind = kind)
    private fun ref(type: String = "GOAL", id: String = "1", label: String = "旧缓存名称") =
        InsightSourceRef(type, id, label)
    private fun fact(id: String = "fact", refs: List<InsightSourceRef> = listOf(ref()), inference: Boolean = false) =
        InsightFact(id, "真实的规则事实，不表示没有实际执行", refs, inference)
    private fun report(facts: List<InsightFact> = listOf(fact()),
        refs: List<InsightSourceRef> = facts.flatMap { it.sourceRefs },
        ids: List<String> = facts.map { it.id }, title: String = "模糊模型标题：财富与健康") = InsightReport(
        "report", binding.dataVersion!!, InsightReportStatus.READY, "", facts,
        listOf(GrowthInsight("insight", InsightType.SUGGESTION, title, "可以讨论下一步", true, null, refs, ids)),
        10, 100, 90)
    private fun context(report: InsightReport = report(), roots: List<GoalNode> = listOf(node(goal())),
        checkins: List<GoalCheckinEntity> = emptyList(), plans: List<SmallPlanEntity> = emptyList(),
        currentBinding: InformationBinding = binding) = InformationContext(
        GoalSnapshot(roots = roots), checkins, plans, currentBinding,
        InsightInput(report.dataVersion, 10, 100, report.facts, emptyList(),
            report.facts.flatMap { it.sourceRefs }, true, "hash"), now = 50)
    private fun resolve(report: InsightReport = report(), context: InformationContext = context(report),
        bound: InformationBinding = binding) = InformationResolver.resolve(
        InformationInput.Insight(report, "insight", bound), context)
    private fun assertBlocked(result: InformationPresentation, reason: InformationState) {
        assertTrue("${result.issues} should contain $reason", reason in result.issues)
        assertNull(result.suggestion)
        assertFalse(result.nextAction is InformationNextAction.DiscussInsight)
        assertFalse(result.nextAction is InformationNextAction.OpenCareConversation)
    }

    @Test fun resolvesNewGoalByRealIdWithoutHardcodedNames() {
        val report = report(listOf(fact(refs = listOf(ref(id = "987")))))
        val result = resolve(report, context(report, roots = listOf(node(goal(987, "新增方向")))))
        assertEquals(InformationState.VALID, result.state)
        assertEquals(987L, result.subject.stableId)
        assertEquals("新增方向", result.subject.currentName)
        assertTrue(result.suggestion!!.isInference)
        assertEquals(InformationNextAction.DiscussInsight(InsightReference("report", "hash|day", "insight")), result.nextAction)
    }

    @Test fun renameUsesCurrentTreeNotOldSourceLabelOrModelTitle() {
        val report = report()
        val result = resolve(report, context(report, roots = listOf(node(goal(title = "改名后")))))
        assertEquals("改名后", result.subject.currentName)
        assertEquals("旧缓存名称", result.evidence.sourceRefs.first().label)
    }

    @Test fun deletedGoalNeverResolvesBySameNameReplacement() {
        val result = resolve(context = context(roots = listOf(node(goal(2, "旧缓存名称")))))
        assertBlocked(result, InformationState.MISSING_SOURCE)
        assertEquals(1L, result.subject.stableId)
        assertNull(result.subject.currentName)
    }

    @Test fun parentChildSourcesRemainTwoObjects() {
        val report = report(listOf(fact(refs = listOf(ref(), ref(id = "2")))))
        val result = resolve(report, context(report, roots = listOf(node(goal(), listOf(node(goal(2, "子目标", 1)))))))
        assertEquals(InformationSubjectType.MULTIPLE_GOALS, result.subject.type)
        assertEquals("涉及多个目标", result.subject.displayName)
        assertEquals(listOf(1L, 2L), result.subject.details.map { it.stableId })
        assertEquals(InformationGoalIdentity(1, "当前目标"), result.subject.details[1].parentGoal)
        assertTrue(result.nextAction is InformationNextAction.OpenSubjects)
    }

    @Test fun unrelatedMultipleGoalsNeverPickArbitraryFirst() {
        val report = report(listOf(fact(refs = listOf(ref(id = "2"), ref()))))
        val result = resolve(report, context(report, roots = listOf(node(goal()), node(goal(2, "另一个")))))
        assertNull(result.subject.stableId)
        assertEquals(setOf(1L, 2L), result.subject.details.map { it.stableId }.toSet())
        assertFalse(result.nextAction is InformationNextAction.OpenGoal)
    }

    @Test fun factSourceSetCannotBeNarrowedByModelSources() {
        val report = report(listOf(fact(refs = listOf(ref(), ref(id = "2")))), refs = listOf(ref()))
        val result = resolve(report, context(report, roots = listOf(node(goal()), node(goal(2, "第二目标")))))
        assertEquals(2, result.subject.details.size)
    }

    @Test fun checkinIdResolvesThroughActualGoalIdNotNumericallyMatchingGoal() {
        val report = report(listOf(fact(refs = listOf(ref("CHECKIN", "9")))))
        val result = resolve(report, context(report, roots = listOf(node(goal()), node(goal(9, "错误同号目标"))),
            checkins = listOf(checkin())))
        assertEquals(1L, result.subject.stableId)
    }

    @Test fun missingCheckinDoesNotGuessFromLabel() {
        val report = report(listOf(fact(refs = listOf(ref("CHECKIN", "9", "当前目标")))))
        assertBlocked(resolve(report), InformationState.MISSING_SOURCE)
    }

    @Test fun checkinLinkedToDeletedGoalDoesNotRedirect() {
        val report = report(listOf(fact(refs = listOf(ref("CHECKIN", "9")))))
        assertBlocked(resolve(report, context(report, checkins = listOf(checkin(goalId = 77)))), InformationState.MISSING_SOURCE)
    }

    @Test fun planShowsPlanAndCurrentGoalAndNavigatesByPlanId() {
        val result = InformationResolver.resolve(InformationInput.Plan(20, 0), context(plans = listOf(plan())))
        assertEquals("真实计划名 · 当前目标", result.subject.displayName)
        assertEquals(InformationGoalRelation.LINKED, result.subject.goalRelation)
        assertEquals(InformationNextAction.OpenPlan(20), result.nextAction)
        assertTrue(result.observation.single().text.contains("不代表实际执行总数"))
    }

    @Test fun independentPlanIsExplicitOnlyForNullGoalId() {
        val result = InformationResolver.resolve(InformationInput.Plan(20), context(plans = listOf(plan(goalId = null))))
        assertEquals(InformationGoalRelation.INDEPENDENT, result.subject.goalRelation)
        assertEquals("真实计划名 · 独立计划", result.subject.displayName)
        assertTrue(result.observation.single().text.contains("未知"))
    }

    @Test fun deletedPlanGoalIsNotIndependentPlan() {
        val result = InformationResolver.resolve(InformationInput.Plan(20), context(plans = listOf(plan(goalId = 99))))
        assertBlocked(result, InformationState.MISSING_SOURCE)
        assertEquals(InformationGoalRelation.MISSING, result.subject.goalRelation)
        assertFalse(result.subject.displayName.contains("独立计划"))
    }

    @Test fun completedPausedCancelledOrRejectedPlanDoesNotNag() {
        listOf("COMPLETED", "CANCELLED", "PAUSED", "REJECTED").forEach { status ->
            val result = InformationResolver.resolve(InformationInput.Plan(20, 0), context(plans = listOf(plan(status = status))))
            assertBlocked(result, InformationState.INACTIVE)
            assertEquals(InformationNextAction.None, result.nextAction)
        }
    }

    @Test fun completedOrPausedGoalDoesNotInviteAction() {
        listOf(GoalStatus.DONE, GoalStatus.PAUSED, GoalStatus.CANCELLED).forEach { status ->
            assertBlocked(resolve(context = context(roots = listOf(node(goal(status = status))))), InformationState.INACTIVE)
        }
    }

    @Test fun missingFactDoesNotBorrowUnrelatedReportFact() {
        val report = report(ids = listOf("missing"))
        val result = resolve(report)
        assertBlocked(result, InformationState.MISSING_FACT)
        assertTrue(result.observation.isEmpty())
        assertEquals(listOf("missing"), result.evidence.factIds)
    }

    @Test fun factlessSuggestionIsNotActionable() {
        assertBlocked(resolve(report(ids = emptyList())), InformationState.MISSING_FACT)
    }

    @Test fun missingAuthorizedSourceIsNotTrusted() {
        val report = report()
        val ctx = context(report)
        assertBlocked(resolve(report, ctx.copy(currentInsightInput = ctx.currentInsightInput!!.copy(allowedSources = emptyList()))),
            InformationState.MISSING_SOURCE)
    }

    @Test fun changedFactOrDataVersionMakesHistoryNonCurrent() {
        val report = report()
        val ctx = context(report)
        val result = resolve(report, ctx.copy(currentInsightInput = ctx.currentInsightInput!!.copy(dataVersion = "new")))
        assertBlocked(result, InformationState.STALE)
        assertFalse(result.observation.single().isCurrent)
        assertEquals("hash|day", result.evidence.reportReference!!.dataVersion)
    }

    @Test fun authorizationEpochCategoriesRecipientAndRevocationInvalidate() {
        listOf(consent.copy(epoch = 8), consent.copy(categories = emptySet()),
            consent.copy(recipient = "https://other.test"), consent.copy(granted = false)).forEach { changed ->
            val result = resolve(context = context(currentBinding = binding.copy(consent = changed)))
            assertBlocked(result, InformationState.AUTHORIZATION_CHANGED)
            assertTrue(result.observation.isEmpty())
            assertEquals(7L, result.evidence.binding.consent!!.epoch)
        }
    }

    @Test fun restoreGenerationChangeSuppressesOldTextAndRetainsPointer() {
        val result = resolve(context = context(currentBinding = binding.copy(restoreGeneration = 4)))
        assertBlocked(result, InformationState.RESTORE_CHANGED)
        assertTrue(result.observation.isEmpty())
        assertEquals(3L, result.evidence.binding.restoreGeneration)
    }

    @Test fun insufficientDataDoesNotProduceSuggestion() {
        val report = report()
        val ctx = context(report)
        assertBlocked(resolve(report, ctx.copy(currentInsightInput = ctx.currentInsightInput!!.copy(hasEnoughData = false))),
            InformationState.INSUFFICIENT_DATA)
    }

    @Test fun insufficientSentinelBlocksEvenWhenUnreferenced() {
        val report = report().let { it.copy(facts = it.facts + fact("insufficient", emptyList())) }
        assertBlocked(resolve(report), InformationState.INSUFFICIENT_DATA)
    }

    @Test fun crossDayIsDistinctFromChangedSourceAndCannotAct() {
        val result = resolve(context = context(currentBinding = binding.copy(timeWindowKey = "next-day", dataVersion = "hash|next-day")))
        assertBlocked(result, InformationState.CROSS_DAY)
        assertEquals(InformationState.CROSS_DAY, result.state)
    }

    @Test fun ambiguousModelTitleAndDiaryLabelCannotCreateGoalIdentity() {
        val report = report(listOf(fact(refs = listOf(ref("JOURNAL", "1", "当前目标")))), title = "你应该关注当前目标")
        val result = resolve(report)
        assertBlocked(result, InformationState.AMBIGUOUS_SUBJECT)
        assertEquals("综合观察", result.subject.displayName)
        assertNull(result.subject.stableId)
        assertTrue(result.nextAction is InformationNextAction.Reevaluate)
    }

    @Test fun unknownHistoricalProvenanceIsNotInvented() {
        val result = resolve(bound = InformationBinding(dataVersion = "hash|day"))
        assertBlocked(result, InformationState.MISSING_CONTEXT)
        assertNull(result.evidence.binding.consent)
        assertNull(result.evidence.binding.restoreGeneration)
    }

    @Test fun exactCurrentVersionCanBeRevalidatedWithoutClaimingOriginalEpoch() {
        val report = report()
        val ctx = context(report)
        val verified = InformationResolver.revalidatedInsightBinding(report, ctx.currentInsightInput!!,
            consent, consent, 3, 3)!!
        val result = resolve(report, ctx.copy(currentBinding = verified), verified)
        assertEquals(InformationState.VALID, result.state)
        assertEquals(InformationBindingOrigin.CURRENT_REVALIDATION, result.evidence.binding.origin)
    }

    @Test fun revalidationRefusesVersionConsentAndGenerationRaces() {
        val report = report()
        val input = context(report).currentInsightInput!!
        assertNull(InformationResolver.revalidatedInsightBinding(report, input, consent, consent.copy(epoch = 8), 3, 3))
        assertNull(InformationResolver.revalidatedInsightBinding(report, input, consent, consent, 3, 4))
        assertNull(InformationResolver.revalidatedInsightBinding(report, input.copy(dataVersion = "changed"), consent, consent, 3, 3))
        assertNull(InformationResolver.revalidatedInsightBinding(report, input, consent.copy(granted = false), consent.copy(granted = false), 3, 3))
    }

    @Test fun trendRefusesDeltaMixedSemanticsAndOutsideWindow() {
        val refs = listOf(ref("CHECKIN", "9"), ref("CHECKIN", "10"))
        val report = report(listOf(fact("trend-1-小时", refs)))
        listOf(
            listOf(checkin(), checkin(10, kind = "delta")),
            listOf(checkin(kind = "delta"), checkin(10, kind = "delta")),
            listOf(checkin(at = 1), checkin(10)),
            listOf(checkin(), checkin(10, unit = "次")),
            listOf(checkin(), checkin(10), checkin(11, kind = "delta")),
        ).forEach { records ->
            val result = resolve(report, context(report, checkins = records))
            assertBlocked(result, InformationState.UNRELIABLE_FACT)
            assertTrue(result.observation.isEmpty())
        }
    }

    @Test fun validSnapshotTrendRemainsUsable() {
        val report = report(listOf(fact("trend-1-小时", listOf(ref("CHECKIN", "9"), ref("CHECKIN", "10")))))
        val result = resolve(report, context(report, checkins = listOf(checkin(), checkin(10, at = 30))))
        assertEquals(InformationState.VALID, result.state)
        assertEquals(1, result.observation.size)
    }

    @Test fun readableTrendUsesActualSortedSnapshotDatesValuesAndPreservesOriginalFact() {
        val zone = java.time.ZoneOffset.UTC
        val firstAt = java.time.LocalDate.of(2026, 11, 9).atTime(21, 0).toInstant(zone).toEpochMilli()
        val lastAt = java.time.LocalDate.of(2026, 11, 15).atTime(22, 0).toInstant(zone).toEpochMilli()
        val original = "完整规则原句保留对象、区间、点数与来源，不从这句话提取数值或日期。"
        val fact = fact("trend-1-分钟", listOf(ref("CHECKIN", "10"), ref("CHECKIN", "9")))
            .copy(text = original)
        val report = report(listOf(fact), title = "模型标题：另一人于1月1日读999小时")
            .copy(periodStart = firstAt - 1000, periodEnd = lastAt + 1000)
        val ctx = context(report, checkins = listOf(
            checkin(10, at = lastAt, unit = "分钟").copy(metricValue = 20.5),
            checkin(9, at = firstAt, unit = "分钟").copy(metricValue = 10.0),
        )).copy(zone = zone)
        val result = resolve(report, ctx)
        val observation = result.observation.single()
        assertEquals(InformationState.VALID, result.state)
        assertEquals("11月9日累计10分钟 → 11月15日累计20.5分钟", observation.text)
        assertEquals(InformationPeriod(firstAt, lastAt), observation.period)
        assertEquals(original, observation.originalText)
        assertEquals(fact.sourceRefs, observation.sources)
        assertEquals(InformationSnapshotComparison(
            first = InformationSnapshotPoint(9, 10.0, "分钟", firstAt),
            last = InformationSnapshotPoint(10, 20.5, "分钟", lastAt),
        ), observation.comparison)
        assertEquals("当前目标", result.subject.currentName)
        assertFalse(observation.text.contains("999"))
    }

    @Test fun readableTrendUsesExplicitDisplayZoneAndKeepsYearsWhenCrossingYearBoundary() {
        val zone = java.time.ZoneOffset.UTC
        val firstAt = java.time.Instant.parse("2026-12-31T23:00:00Z").toEpochMilli()
        val lastAt = java.time.Instant.parse("2027-01-01T01:00:00Z").toEpochMilli()
        val report = report(listOf(fact("trend-1-分钟", listOf(ref("CHECKIN", "9"), ref("CHECKIN", "10")))))
            .copy(periodStart = firstAt, periodEnd = lastAt)
        val ctx = context(report, checkins = listOf(
            checkin(9, at = firstAt, unit = "分钟").copy(metricValue = 10.0),
            checkin(10, at = lastAt, unit = "分钟").copy(metricValue = 20.0),
        )).copy(zone = zone)
        assertEquals("2026年12月31日累计10分钟 → 2027年1月1日累计20分钟", resolve(report, ctx).observation.single().text)
        assertEquals("1月1日累计10分钟 → 1月1日累计20分钟",
            resolve(report, ctx.copy(zone = java.time.ZoneOffset.ofHours(8))).observation.single().text)
    }

    @Test fun readableTrendRejectsSinglePointDuplicatePointMissingUnitAndMixedUnits() {
        val single = report(listOf(fact("trend-1-小时", listOf(ref("CHECKIN", "9")))))
        assertBlocked(resolve(single, context(single, checkins = listOf(checkin()))), InformationState.UNRELIABLE_FACT)
        val duplicate = report(listOf(fact("trend-1-小时", listOf(ref("CHECKIN", "9"), ref("CHECKIN", "9")))))
        val duplicateResult = resolve(duplicate, context(duplicate, checkins = listOf(checkin())))
        assertBlocked(duplicateResult, InformationState.UNRELIABLE_FACT)
        assertTrue(duplicateResult.observation.isEmpty())
        val report = report(listOf(fact("trend-1-小时", listOf(ref("CHECKIN", "9"), ref("CHECKIN", "10")))))
        listOf(
            listOf(checkin(), checkin(10, unit = "")),
            listOf(checkin(), checkin(10).copy(metricValue = Double.NaN)),
            listOf(checkin(), checkin(10, unit = "分钟")),
            listOf(checkin(), checkin(10, kind = "delta")),
        ).forEach { records ->
            val result = resolve(report, context(report, checkins = records))
            assertBlocked(result, InformationState.UNRELIABLE_FACT)
            assertTrue(result.observation.isEmpty())
        }
    }

    @Test fun noRecentObservationKeepsExactCautiousOriginalSentence() {
        val original = "最近30天未找到目标及其子行动的新记录。这只说明没有记录，不代表没有执行。"
        val fact = fact("no-recent-progress-1").copy(text = original)
        val result = resolve(report(listOf(fact)))
        assertEquals(original, result.observation.single().text)
        assertEquals(original, result.observation.single().originalText)
    }

    @Test fun staleTrendDoesNotRewriteHistoricalOriginalWithCurrentRowValues() {
        val fact = fact("trend-1-小时", listOf(ref("CHECKIN", "9"), ref("CHECKIN", "10")))
        val report = report(listOf(fact))
        val result = resolve(report, context(report, checkins = listOf(checkin(), checkin(10, at = 30)),
            currentBinding = binding.copy(dataVersion = "hash|tomorrow", timeWindowKey = "tomorrow")))
        val observation = result.observation.single()
        assertFalse(observation.isCurrent)
        assertEquals(fact.text, observation.text)
        assertEquals(fact.text, observation.originalText)
        assertEquals(InformationPeriod(report.periodStart, report.periodEnd), observation.period)
        assertNull(observation.comparison)
    }

    @Test fun nonTrendFactsDoNotInventSnapshotComparison() {
        assertNull(resolve().observation.single().comparison)
    }

    private fun care(planId: Long? = 20, goalId: Long? = 1, title: String = "不能用来猜的旧标题") : InformationInput.Care {
        val opp = CareOpportunityEntity(60, if (planId != null) "PLAN_CHECKIN" else "GOAL_STALL",
            planId ?: goalId ?: 0, planId, goalId, title, 10, 10, 100, 110, "NORMAL", "SENT", "dedupe", 10, 20)
        val decision = CareDecisionEntity(70, 60, "SEND", "AI推断", "original-reference", "聊聊吗？", null,
            null, null, true, 30)
        return InformationInput.Care(CareCard(60, title, "聊聊吗？", opp.type, decisionId = 70),
            opp, decision, binding, suppressed = false)
    }

    @Test fun careResolvesActualOpportunityPlanAndGoalNotCardWording() {
        val result = InformationResolver.resolve(care(), context(plans = listOf(plan())))
        assertEquals(InformationState.VALID, result.state)
        assertEquals("真实计划名 · 当前目标", result.subject.displayName)
        assertEquals(InformationNextAction.OpenCareConversation(60, 70), result.nextAction)
        assertEquals("original-reference", result.evidence.decisionSourceReference)
        assertTrue(result.suggestion!!.isInference)
    }

    @Test fun careMissingOpportunityCannotGuessFromModelTitle() {
        assertBlocked(InformationResolver.resolve(care(title = "当前目标").copy(opportunity = null), context()),
            InformationState.MISSING_SOURCE)
    }

    @Test fun careStaleDecisionExpiredWindowAndIdentityMismatchBlock() {
        val care = care()
        val ctx = context(plans = listOf(plan()))
        assertBlocked(InformationResolver.resolve(care.copy(decision = care.decision!!.copy(id = 999)), ctx), InformationState.MISSING_SOURCE)
        assertBlocked(InformationResolver.resolve(care, ctx.copy(now = 101)), InformationState.STALE)
        assertBlocked(InformationResolver.resolve(care.copy(opportunity = care.opportunity!!.copy(goalId = 2)), ctx), InformationState.STALE)
    }

    @Test fun careCompletedDeclinedSilencedOrFeedbackDoesNotNag() {
        val care = care()
        assertBlocked(InformationResolver.resolve(care, context(plans = listOf(plan(status = "COMPLETED")))), InformationState.INACTIVE)
        assertBlocked(InformationResolver.resolve(care.copy(suppressed = true), context(plans = listOf(plan()))), InformationState.INACTIVE)
        listOf("IGNORED", "SILENCED", "ENGAGED", "DEFERRED").forEach { feedback ->
            assertBlocked(InformationResolver.resolve(care.copy(feedbackKind = feedback), context(plans = listOf(plan()))), InformationState.INACTIVE)
        }
    }

    @Test fun expiredTodayPlanWindowDoesNotKeepPromptingNextDay() {
        val result = InformationResolver.resolve(InformationInput.Plan(20, period = InformationPeriod(10, 40)),
            context(plans = listOf(plan())))
        assertBlocked(result, InformationState.STALE)
        assertEquals(InformationNextAction.None, result.nextAction)
    }

    @Test fun inferenceFactIsNotDisplayedAsActualObservation() {
        val report = report(listOf(fact(inference = true)))
        val result = resolve(report)
        assertBlocked(result, InformationState.UNRELIABLE_FACT)
        assertTrue(result.observation.isEmpty())
    }

    @Test fun incorrectlyUnmarkedSuggestionDoesNotBecomeTrustedAction() {
        val report = report().let { it.copy(insights = it.insights.map { insight -> insight.copy(isInference = false) }) }
        assertBlocked(resolve(report), InformationState.UNRELIABLE_FACT)
    }

    @Test fun independentCarePlanAndDeletedGoalRemainDistinct() {
        val independent = InformationResolver.resolve(care(goalId = null), context(plans = listOf(plan(goalId = null))))
        assertEquals(InformationGoalRelation.INDEPENDENT, independent.subject.goalRelation)
        assertEquals(InformationState.VALID, independent.state)
        val deleted = InformationResolver.resolve(care(goalId = 99), context(plans = listOf(plan(goalId = 99))))
        assertBlocked(deleted, InformationState.MISSING_SOURCE)
        assertEquals(InformationGoalRelation.MISSING, deleted.subject.goalRelation)
    }

    @Test fun careMissingDecisionCannotPromoteGeneratedTextToFact() {
        val result = InformationResolver.resolve(care().copy(decision = null), context(plans = listOf(plan())))
        assertBlocked(result, InformationState.MISSING_SOURCE)
        assertTrue(result.observation.isEmpty())
        assertEquals(InformationNextAction.Reevaluate(opportunityId = 60), result.nextAction)
    }

    @Test fun careRecoveryRoutesByCardOpportunityIdEvenWhenSourceIsMissing() {
        val input = care(title = "标题不能决定恢复路由").copy(opportunity = null)
        val result = InformationResolver.resolve(input, context())
        val action = result.nextAction as InformationNextAction.Reevaluate
        assertEquals(input.card.opportunityId, action.opportunityId)
        assertNull(action.reference)
    }

    @Test fun insightRecoveryKeepsReportReferenceWithoutCareOpportunityId() {
        val result = resolve(report(ids = listOf("missing")))
        val action = result.nextAction as InformationNextAction.Reevaluate
        assertEquals(InsightReference("report", "hash|day", "insight"), action.reference)
        assertNull(action.opportunityId)
    }

    @Test fun doesNotMutateSourceEntitiesOrOriginalPointersAndIsDeterministic() {
        val report = report()
        val ctx = context(report)
        val input = InformationInput.Insight(report, "insight", binding)
        val originalFacts = report.facts.toList()
        assertEquals(InformationResolver.resolve(input, ctx), InformationResolver.resolve(input, ctx))
        assertEquals(originalFacts, report.facts)
        assertEquals("旧缓存名称", report.insights.single().sources.single().label)
        assertEquals(7L, ctx.currentBinding.consent!!.epoch)
    }
}
