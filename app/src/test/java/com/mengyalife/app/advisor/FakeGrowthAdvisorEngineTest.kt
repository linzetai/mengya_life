package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.FakeAdvisorEngine
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorContextSnapshot
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.RecordProposalParseResult
import com.mengyalife.app.domain.advisor.RecordProposalParser
import com.mengyalife.app.domain.advisor.ContextCheckinRecord
import com.mengyalife.app.domain.advisor.ContextGoalRecord
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.insight.InsightParseResult
import com.mengyalife.app.domain.insight.InsightParser
import com.mengyalife.app.domain.insight.InsightRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeGrowthAdvisorEngineTest {
    private val now = 1_800_000_000_000L
    private val consent = AiConsentState(
        granted = true,
        textVersion = AiConsentRules.TEXT_VERSION,
        categoriesVersion = AiConsentRules.CATEGORIES_VERSION,
        recipient = "https://qa.example/v1",
        categories = ConsentCategory.entries.toSet(),
        epoch = 3,
    )
    private val goal = ContextGoalRecord(
        id = 9, title = "QA 样例：长期阅读", description = null,
        level = GoalLevel.LONG_TERM, parentId = null, domain = GoalDomain.LEARNING,
        status = GoalStatus.IN_PROGRESS, createdAt = now - 120L * 24 * 60 * 60 * 1000,
    )

    @Test
    fun `QA engine suggestion cites only an existing stale-record fact`() = runTest {
        val days = 24L * 60 * 60 * 1000
        val input = InsightRules.build(
            AdvisorContextSnapshot(
                goals = listOf(goal),
                checkins = listOf(
                    ContextCheckinRecord(1, 9, now - 90 * days, "QA 样例阅读记录 1", null, null),
                    ContextCheckinRecord(2, 9, now - 60 * days, "QA 样例阅读记录 2", null, null),
                ),
            ),
            consent,
            now,
        )
        val prompt = InsightParser.prompt(input)
        val engine = FakeAdvisorEngine(delayMs = 0)
        val result = engine.chat(
            AdvisorRuntimeConfig("https://qa.example/v1", "qa-growth-offline", "qa-placeholder"),
            listOf(
                AdvisorChatMessage("system", "GROWTH_INSIGHT_V1"),
                AdvisorChatMessage("user", prompt),
            ),
        ) as AdvisorChatResult.Success
        val parsed = InsightParser.parse(result.content, input.facts, input.allowedSources) { "qa-insight" }
            as InsightParseResult.Success
        val suggestion = parsed.insights.single()
        assertEquals("SUGGESTION", suggestion.type.name)
        assertTrue(suggestion.isInference)
        assertTrue(suggestion.factIds.single() in input.facts.map { it.id })
        assertTrue(suggestion.sources.all { source ->
            input.facts.filter { it.id in suggestion.factIds }.any { fact -> source in fact.sourceRefs }
        })
        assertFalse(suggestion.body.contains("已经完成"))
    }

    @Test
    fun `QA plan extraction returns only the explicit user action for UI regression`() = runTest {
        val userText = "这周读三次书"
        val engine = FakeAdvisorEngine(delayMs = 0)
        val result = engine.chat(
            AdvisorRuntimeConfig("https://qa.example/v1", "qa-growth-offline", "qa-placeholder"),
            listOf(
                AdvisorChatMessage("system", AdvisorRules.planExtractionPrompt(emptyList(), emptyList(), "2026-11-13")),
                AdvisorChatMessage("user", userText),
            ),
        ) as AdvisorChatResult.Success

        val parsed = RecordProposalParser.parse(result.content, userText)
        assertTrue(parsed is RecordProposalParseResult.Success)
        assertTrue((parsed as RecordProposalParseResult.Success).draft?.isPlan == true)
        assertTrue(result.content.contains("这周读三次书"))
    }

    @Test
    fun `QA engine refuses to invent recommendation when records are insufficient`() = runTest {
        val input = InsightRules.build(AdvisorContextSnapshot(goals = listOf(goal)), consent, now)
        assertFalse(input.hasEnoughData)
        val engine = FakeAdvisorEngine(delayMs = 0)
        val result = engine.chat(
            AdvisorRuntimeConfig("https://qa.example/v1", "qa-growth-offline", "qa-placeholder"),
            listOf(
                AdvisorChatMessage("system", "GROWTH_INSIGHT_V1"),
                AdvisorChatMessage("user", InsightParser.prompt(input)),
            ),
        )
        assertTrue(result is AdvisorChatResult.Failure)
    }
}
