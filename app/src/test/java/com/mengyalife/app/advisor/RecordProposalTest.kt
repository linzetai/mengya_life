package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.FakeAdvisorEngine
import com.mengyalife.app.domain.advisor.AdvisorChatMessage
import com.mengyalife.app.domain.advisor.AdvisorChatResult
import com.mengyalife.app.domain.advisor.AdvisorRuntimeConfig
import com.mengyalife.app.domain.advisor.RecordAmountKind
import com.mengyalife.app.domain.advisor.RecordIntent
import com.mengyalife.app.domain.advisor.RecordIntentGate
import com.mengyalife.app.domain.advisor.RecordProposalParseResult
import com.mengyalife.app.domain.advisor.RecordProposalParser
import com.mengyalife.app.domain.advisor.RecordProposalRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话式记录候选的纯规则测试：
 * - 增量/累计快照必须区分；
 * - 缺目标/缺数值时不得当作可写入；
 * - 明确指令 + 完整 + 有进展授权才允许直接写入；
 * - QA 离线引擎的输出必须能被同一解析器接受（确定性回归）。
 */
class RecordProposalTest {

    private val goals = setOf(1L, 2L)

    private fun parse(json: String, text: String = "帮我记录一下，今天存了2000") =
        RecordProposalParser.parse(json, text, allowedGoalIds = goals)

    private fun success(json: String, text: String = "帮我记录一下，今天存了2000"): RecordProposalParseResult.Success =
        parse(json, text) as RecordProposalParseResult.Success

    @Test
    fun `delta record is parsed with goal and unit`() {
        val draft = success(
            """
            {"schemaVersion":1,"record":{"type":"PROGRESS","goalId":1,"goalTitle":"五年内积累 50 万",
            "goalCandidates":[],"amountKind":"DELTA","metricValue":2000,"metricUnit":"元",
            "note":"今天存了 2000 元","occurredAt":null,"pendingFields":[],"intent":"EXPLICIT"}}
            """.trimIndent(),
        ).draft!!

        assertEquals(RecordAmountKind.DELTA, draft.amountKind)
        assertEquals(2000.0, draft.metricValue!!, 0.0001)
        assertEquals("元", draft.metricUnit)
        assertEquals(RecordIntent.EXPLICIT, draft.intent)
        assertEquals(1L, draft.goalId)
        assertTrue(draft.isComplete)
        assertTrue(RecordProposalRules.shouldAutoApply(draft, progressAuthorized = true))
    }

    @Test
    fun `snapshot record stays snapshot instead of being treated as delta`() {
        val draft = success(
            """
            {"schemaVersion":1,"record":{"type":"PROGRESS","goalId":1,"amountKind":"SNAPSHOT",
            "metricValue":147000,"metricUnit":"元","note":"现在已经攒到14.7万了","intent":"SUGGESTED"}}
            """.trimIndent(),
            text = "现在已经攒到14.7万了",
        ).draft!!

        assertEquals(RecordAmountKind.SNAPSHOT, draft.amountKind)
        assertEquals(147000.0, draft.metricValue!!, 0.0001)
        assertEquals("累计快照 147000 元", RecordProposalRules.amountLine(draft))
        // 聊天中推断出来的记录只能是建议，不能自动写入。
        assertFalse(RecordProposalRules.shouldAutoApply(draft, progressAuthorized = true))
    }

    @Test
    fun `record null means no record intent`() {
        val result = parse("""{"schemaVersion":1,"record":null}""")
        assertTrue(result is RecordProposalParseResult.Success)
        assertNull((result as RecordProposalParseResult.Success).draft)
    }

    @Test
    fun `delta without numeric value is rejected`() {
        val result = parse(
            """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":1,"amountKind":"DELTA","note":"存了一笔"}}""",
        )
        assertTrue(result is RecordProposalParseResult.Failure)
    }

    @Test
    fun `unknown goal becomes pending clarification instead of guessing`() {
        val draft = success(
            """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":99,"amountKind":"DELTA",
            "metricValue":2000,"metricUnit":"元","note":"存了2000","intent":"EXPLICIT"}}""",
        ).draft!!

        assertNull(draft.goalId)
        assertTrue(draft.pendingFields.contains("goal"))
        assertFalse(draft.isComplete)
        assertFalse(RecordProposalRules.shouldAutoApply(draft, progressAuthorized = true))
    }

    @Test
    fun `ambiguous goals produce candidates for the user to choose`() {
        val draft = success(
            """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":null,"amountKind":"DELTA",
            "metricValue":2000,"metricUnit":"元","note":"记录一下进展，今天又存了2000",
            "goalCandidates":[{"goalId":1,"title":"五年内积累 50 万"},{"goalId":2,"title":"建立应急金"}],
            "pendingFields":["goal"],"intent":"SUGGESTED"}}""",
        ).draft!!

        assertEquals(2, draft.goalCandidates.size)
        assertTrue(draft.pendingFields.contains("goal"))
        assertFalse(draft.isComplete)
    }

    @Test
    fun `auto apply requires authorization`() {
        val draft = success(
            """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":1,"amountKind":"DELTA",
            "metricValue":2000,"metricUnit":"元","note":"今天存了2000","intent":"EXPLICIT"}}""",
        ).draft!!
        assertFalse(RecordProposalRules.shouldAutoApply(draft, progressAuthorized = false))
    }

    @Test
    fun `future occurredAt is rejected`() {
        val result = parse(
            """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":1,"amountKind":"DELTA",
            "metricValue":1,"note":"未来记录","occurredAt":"2999-01-01","intent":"EXPLICIT"}}""",
        )
        assertTrue(result is RecordProposalParseResult.Failure)
    }

    @Test
    fun `intent gate also accepts qualitative progress without numbers`() {
        assertTrue(RecordIntentGate.looksRelevant("帮我记录一下，今天存了2000"))
        assertTrue(RecordIntentGate.looksRelevant("现在已经攒到14.7万了"))
        assertTrue(RecordIntentGate.looksRelevant("昨天完成了两小时学习"))
        assertTrue(RecordIntentGate.looksRelevant("今天终于完成面试了"))
        assertTrue(RecordIntentGate.looksRelevant("我和领导认真聊了职业规划"))
        assertTrue(RecordIntentGate.looksRelevant("这周坚持运动了"))
        assertFalse(RecordIntentGate.looksRelevant("你好，最近怎么样"))
        assertFalse(RecordIntentGate.looksRelevant("嗯"))
    }

    @Test
    fun `numeric record without unit becomes pending clarification`() {
        val draft = success(
            """{"schemaVersion":1,"record":{"type":"PROGRESS","goalId":1,"amountKind":"DELTA",
            "metricValue":2000,"note":"今天存了 2000","intent":"EXPLICIT"}}""",
        ).draft!!
        assertTrue(draft.pendingFields.contains("metricUnit"))
        assertFalse(draft.isComplete)
        assertFalse(RecordProposalRules.shouldAutoApply(draft, progressAuthorized = true))
    }

    @Test
    fun `qa engine output is accepted by the same parser for delta and snapshot`() = runTest {
        val engine = FakeAdvisorEngine(delayMs = 0)
        val runtime = AdvisorRuntimeConfig("https://example.invalid/v1", "qa-fake", "test-key")
        fun prompt(vararg titles: String) = buildString {
            appendLine("你是记录提取器，只做结构化提取。")
            titles.forEachIndexed { index, title ->
                appendLine("- [目标#${index + 1}] 长期目标：$title（进行中）")
            }
        }

        val delta = engine.chat(
            runtime,
            listOf(
                AdvisorChatMessage("system", prompt("五年内积累 50 万")),
                AdvisorChatMessage("user", "帮我记录一下，今天存了2000元"),
            ),
        )
        val deltaDraft = (
            RecordProposalParser.parse(
                (delta as AdvisorChatResult.Success).content,
                "帮我记录一下，今天存了2000元",
                allowedGoalIds = setOf(1L),
            ) as RecordProposalParseResult.Success
            ).draft!!
        assertEquals(RecordAmountKind.DELTA, deltaDraft.amountKind)
        assertEquals(2000.0, deltaDraft.metricValue!!, 0.0001)
        assertEquals(1L, deltaDraft.goalId)
        assertTrue(RecordProposalRules.shouldAutoApply(deltaDraft, progressAuthorized = true))

        val snapshot = engine.chat(
            runtime,
            listOf(
                AdvisorChatMessage("system", prompt("五年内积累 50 万")),
                AdvisorChatMessage("user", "现在已经攒到14.7万了"),
            ),
        )
        val snapshotDraft = (
            RecordProposalParser.parse(
                (snapshot as AdvisorChatResult.Success).content,
                "现在已经攒到14.7万了",
                allowedGoalIds = setOf(1L),
            ) as RecordProposalParseResult.Success
            ).draft!!
        assertEquals(RecordAmountKind.SNAPSHOT, snapshotDraft.amountKind)
        assertEquals(147000.0, snapshotDraft.metricValue!!, 0.0001)
        assertFalse(RecordProposalRules.shouldAutoApply(snapshotDraft, progressAuthorized = true))

        val ambiguous = engine.chat(
            runtime,
            listOf(
                AdvisorChatMessage("system", prompt("五年内积累 50 万", "建立应急金")),
                AdvisorChatMessage("user", "帮我记录一下进展，今天又存了2000"),
            ),
        )
        val ambiguousDraft = (
            RecordProposalParser.parse(
                (ambiguous as AdvisorChatResult.Success).content,
                "帮我记录一下进展，今天又存了2000",
                allowedGoalIds = setOf(1L, 2L),
            ) as RecordProposalParseResult.Success
            ).draft!!
        assertNull(ambiguousDraft.goalId)
        assertEquals(2, ambiguousDraft.goalCandidates.size)
    }
}
