package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.GoalProposalParseResult
import com.mengyalife.app.domain.advisor.GoalProposalParser
import com.mengyalife.app.domain.goals.GoalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalProposalParserTest {

    private fun json(
        nodes: String,
        schema: Int = 1,
    ) = """{"schemaVersion":$schema,"summary":"总结","assumptions":["a"],"risks":["r"],"goals":[$nodes]}"""

    private val validNodes = """
        {"tempId":"g1","level":"LONG","title":"健康","description":null,"domain":"HEALTH",
         "parentTempId":null,"parentGoalId":null,"startDate":null,"dueDate":null,"basis":"b"},
        {"tempId":"s1","level":"STAGE","title":"阶段","description":null,"domain":"HEALTH",
         "parentTempId":"g1","parentGoalId":null,"startDate":null,"dueDate":null,"basis":"b"},
        {"tempId":"a1","level":"ACTION","title":"行动","description":null,"domain":"HEALTH",
         "parentTempId":"s1","parentGoalId":null,"startDate":null,"dueDate":null,"basis":"b"}
    """.trimIndent()

    @Test
    fun `合法三级草案解析成功并可往返编码`() {
        val result = GoalProposalParser.parse(json(validNodes), emptyMap())
        assertTrue(result is GoalProposalParseResult.Success)
        val draft = (result as GoalProposalParseResult.Success).draft
        assertEquals(3, draft.nodes.size)
        assertEquals(GoalLevel.ACTION, draft.nodes[2].level)

        val encoded = GoalProposalParser.encode(draft)
        val decoded = GoalProposalParser.decode(
            draft.schemaVersion, encoded.summary, encoded.assumptionsJson,
            encoded.risksJson, encoded.nodesJson,
        )
        assertNotNull(decoded)
        assertEquals(draft, decoded)
    }

    @Test
    fun `重复 tempId 被拒绝`() {
        val nodes = validNodes + ",{\"tempId\":\"g1\",\"level\":\"LONG\",\"title\":\"重复\",\"domain\":\"LIFE\"}"
        val result = GoalProposalParser.parse(json(nodes), emptyMap())
        assertTrue(result is GoalProposalParseResult.Failure)
        assertTrue((result as GoalProposalParseResult.Failure).message.contains("重复"))
    }

    @Test
    fun `层级不合法被拒绝`() {
        val nodes = """
            {"tempId":"a1","level":"ACTION","title":"行动","domain":"LIFE","parentTempId":null,"parentGoalId":null}
        """.trimIndent()
        val result = GoalProposalParser.parse(json(nodes), emptyMap())
        assertTrue(result is GoalProposalParseResult.Failure)
    }

    @Test
    fun `循环依赖被拒绝`() {
        val nodes = """
            {"tempId":"s1","level":"STAGE","title":"阶段","domain":"LIFE","parentTempId":"s2"},
            {"tempId":"s2","level":"STAGE","title":"阶段2","domain":"LIFE","parentTempId":"s1"}
        """.trimIndent()
        val result = GoalProposalParser.parse(json(nodes), emptyMap())
        assertTrue(result is GoalProposalParseResult.Failure)
    }

    @Test
    fun `已有父目标必须存在且层级正确`() {
        val missingNode = """{"tempId":"s1","level":"STAGE","title":"阶段","domain":"LIFE","parentGoalId":99}"""
        val missing = GoalProposalParser.parse(json(missingNode), mapOf(1L to GoalLevel.LONG_TERM))
        assertTrue(missing is GoalProposalParseResult.Failure)

        val stage = """{"tempId":"s1","level":"STAGE","title":"阶段","domain":"LIFE","parentGoalId":1}"""
        val ok = GoalProposalParser.parse(json(stage), mapOf(1L to GoalLevel.LONG_TERM))
        assertTrue(ok is GoalProposalParseResult.Success)
    }

    @Test
    fun `非法 JSON 与错误 schema 不会崩溃`() {
        assertTrue(GoalProposalParser.parse("not json", emptyMap()) is GoalProposalParseResult.Failure)
        assertTrue(GoalProposalParser.parse(json(validNodes, schema = 2), emptyMap()) is GoalProposalParseResult.Failure)
    }

    @Test
    fun `非法日期被拒绝`() {
        val nodes = """{"tempId":"g1","level":"LONG","title":"健康","domain":"HEALTH","dueDate":"2026-13-40"}"""
        val result = GoalProposalParser.parse(json(nodes), emptyMap())
        assertTrue(result is GoalProposalParseResult.Failure)
    }
}
