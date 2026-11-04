package com.mengyalife.app.advisor

import com.mengyalife.app.domain.insight.InsightFact
import com.mengyalife.app.domain.insight.InsightParseResult
import com.mengyalife.app.domain.insight.InsightParser
import com.mengyalife.app.domain.insight.InsightSourceRef
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightParserTest {
    private val refs = listOf(
        InsightSourceRef("GOAL", "1", "长期储蓄"),
        InsightSourceRef("CHECKIN", "2", "第一期"),
    )
    private val facts = listOf(InsightFact("f1", "储蓄从 1000 上升到 1500 CNY", refs))

    private fun json(source: String = "\"type\":\"CHECKIN\",\"id\":\"2\",\"label\":\"第一期\"", body: String = "储蓄在稳步增加") =
        """{"schemaVersion":1,"summary":"s","insights":[{"type":"POSITIVE","title":"稳步储蓄","body":"$body","isInference":true,"confidence":0.8,"factIds":["f1"],"sources":[{$source}]}]}"""

    @Test
    fun `合法来源通过`() {
        val result = InsightParser.parse(json(), facts, refs) { "i1" }
        assertTrue(result is InsightParseResult.Success)
    }

    @Test
    fun `来源不在授权上下文被拒绝`() {
        val result = InsightParser.parse(
            json(source = "\"type\":\"CHECKIN\",\"id\":\"99\",\"label\":\"捏造\""),
            facts, refs,
        ) { "i1" }
        assertTrue(result is InsightParseResult.Failure)
        assertTrue((result as InsightParseResult.Failure).message.contains("来源不在"))
    }

    @Test
    fun `factId 不在本轮事实中被拒绝`() {
        val bad = """{"schemaVersion":1,"summary":"s","insights":[{"type":"POSITIVE","title":"t","body":"b","isInference":true,"factIds":["f9"],"sources":[{"type":"CHECKIN","id":"2","label":"x"}]}]}"""
        assertTrue(InsightParser.parse(bad, facts, refs) { "i1" } is InsightParseResult.Failure)
    }

    @Test
    fun `非法 JSON 或空来源被拒绝`() {
        assertTrue(InsightParser.parse("no json", facts, refs) { "i1" } is InsightParseResult.Failure)
        val emptySources = """{"schemaVersion":1,"summary":"s","insights":[{"type":"RISK","title":"t","body":"b","isInference":true,"factIds":["f1"],"sources":[]}]}"""
        assertTrue(InsightParser.parse(emptySources, facts, refs) { "i1" } is InsightParseResult.Failure)
    }
}
