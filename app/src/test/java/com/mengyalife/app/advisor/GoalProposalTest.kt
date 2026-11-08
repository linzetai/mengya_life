package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.GoalChangeKind
import com.mengyalife.app.domain.advisor.GoalIntentGate
import com.mengyalife.app.domain.advisor.RecordProposalParseResult
import com.mengyalife.app.domain.advisor.RecordProposalParser
import com.mengyalife.app.domain.advisor.RecordProposalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.18 对话式目标：闸门、解析与“永不自动建目标”。 */
class GoalProposalTest {

    private val create = """
        {"schemaVersion":1,"record":{"type":"GOAL","change":"CREATE",
        "title":"找到更有成长空间的工作","level":"LONG_TERM","domain":"CAREER",
        "dueAt":"2029-11-07","breakdown":[{"title":"梳理可迁移能力","level":"STAGE"}],
        "uncertain":["薪资"],"intent":"EXPLICIT"}}
    """.trimIndent()

    @Test
    fun `create goal draft is parsed without fabricating extra fields`() {
        val draft = (RecordProposalParser.parse(create, "设成目标") as RecordProposalParseResult.Success).draft!!
        assertTrue(draft.isGoal)
        assertEquals(GoalChangeKind.CREATE, draft.goalChange!!.change)
        assertEquals("找到更有成长空间的工作", draft.goalChange!!.title)
        assertEquals("CAREER", draft.goalChange!!.domainName)
        assertEquals(1, draft.goalChange!!.breakdown.size)
        assertTrue(draft.goalChange!!.uncertain.contains("薪资"))
        assertTrue(draft.isComplete)
    }

    @Test
    fun `update requires an authorized existing goal`() {
        val raw = """{"schemaVersion":1,"record":{"type":"GOAL","change":"UPDATE","goalId":1,
            "title":"五年内积累 50 万","dueAt":"2029-01-01","intent":"EXPLICIT"}}"""
        val ok = RecordProposalParser.parse(raw, "改成三年", allowedGoalIds = setOf(1L))
        assertTrue(ok is RecordProposalParseResult.Success)
        val bad = RecordProposalParser.parse(raw, "改成三年", allowedGoalIds = setOf(2L))
        assertTrue(bad is RecordProposalParseResult.Failure)
    }

    @Test
    fun `goal draft never auto applies`() {
        val draft = (RecordProposalParser.parse(create, "设成目标") as RecordProposalParseResult.Success).draft!!
        assertFalse(RecordProposalRules.shouldAutoApply(draft, progressAuthorized = true))
    }

    @Test
    fun `wish does not trigger but explicit and breakdown do`() {
        assertFalse(GoalIntentGate.looksRelevant("最近有点想学吉他。"))
        assertTrue(GoalIntentGate.looksRelevant("帮我把学吉他设成一个目标。"))
        assertTrue(GoalIntentGate.looksRelevant("我想换工作，但不知道从哪里开始。"))
        assertTrue(GoalIntentGate.looksRelevant("我想在三年内找到一份更有成长空间的工作。"))
    }

    @Test
    fun `plain plan or progress is not a goal`() {
        assertFalse(GoalIntentGate.looksRelevant("我下周准备练两次。"))
        assertFalse(GoalIntentGate.looksRelevant("今天练了半小时。"))
    }

    @Test
    fun `null record means no goal draft`() {
        val result = RecordProposalParser.parse("""{"schemaVersion":1,"record":null}""", "有点想学吉他")
        assertNull((result as RecordProposalParseResult.Success).draft)
    }
}
