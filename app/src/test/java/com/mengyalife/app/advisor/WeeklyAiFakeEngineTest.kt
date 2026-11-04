package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.FakeAdvisorEngine
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.weekly.WeeklyAiParser
import com.mengyalife.app.domain.weekly.WeeklyAiParseResult
import com.mengyalife.app.domain.weekly.WeeklyFact
import com.mengyalife.app.domain.weekly.WeeklySourceRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 3F：QA 假引擎的周回顾协议。
 *
 * 真机用例依赖「模型名决定成功/失败/超时」这一确定性约定；这里先把它锁住，
 * 避免假引擎漂移让真机用例变成模糊测试。
 */
class WeeklyAiFakeEngineTest {

    private val facts = listOf(
        WeeklyFact("week-counts", "本周 1 条进展。", listOf(WeeklySourceRef("CHECKIN", "1", "QAGoal"))),
        WeeklyFact("wins-progress", "本周留下了 1 条进展记录。", listOf(WeeklySourceRef("CHECKIN", "1", "QAGoal"))),
    )

    private fun config(model: String) = AdvisorRuntimeConfig(
        endpoint = "https://api.example.com/v1",
        model = model,
        apiKey = "sk-qa-fake-only",
    )

    private fun messages() = listOf(
        AdvisorChatMessage("system", "只输出严格 JSON。"),
        AdvisorChatMessage("user", WeeklyAiParser.prompt(facts, "GOALS,PROGRESS")),
    )

    @Test
    fun `weekly success output parses and keeps only authorized fact ids`() = runTest {
        val result = FakeAdvisorEngine(delayMs = 0).chat(config("qa-fake"), messages(), 900)
        assertTrue("应成功: $result", result is AdvisorChatResult.Success)
        val raw = (result as AdvisorChatResult.Success).content
        val parsed = WeeklyAiParser.parse(raw, facts, "GOALS,PROGRESS")
        assertTrue("应可解析: $parsed", parsed is WeeklyAiParseResult.Success)
        val ok = parsed as WeeklyAiParseResult.Success
        assertEquals(listOf("week-counts"), ok.factIds)
        assertTrue(ok.summary.contains("QA 离线测试引擎"))
        assertEquals(listOf("CHECKIN:1"), ok.highlights.first().sources.map { it.type + ":" + it.recordId })
    }

    @Test
    fun `weekly model name deterministically simulates failures`() = runTest {
        val engine = FakeAdvisorEngine(delayMs = 0)
        assertTrue(engine.chat(config("qa-fake-timeout"), messages(), 900) is AdvisorChatResult.Failure)
        assertTrue(engine.chat(config("qa-fake-401"), messages(), 900) is AdvisorChatResult.Failure)
        assertTrue(engine.chat(config("qa-fake-offline"), messages(), 900) is AdvisorChatResult.Failure)
        val invalid = engine.chat(config("qa-fake-invalid"), messages(), 900)
        assertTrue(invalid is AdvisorChatResult.Success)
        assertTrue(
            WeeklyAiParser.parse((invalid as AdvisorChatResult.Success).content, facts, "GOALS") is WeeklyAiParseResult.Failure,
        )
    }
}
