package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.DiaryIntentGate
import com.mengyalife.app.domain.advisor.RecordProposalParseResult
import com.mengyalife.app.domain.advisor.RecordProposalParser
import com.mengyalife.app.domain.advisor.RecordProposalRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.17 对话式日记：解析、闸门与“永不自动保存”。 */
class DiaryProposalTest {

    private val valid = """
        {"schemaVersion":1,"record":{"type":"DIARY","title":"一次面试",
        "body":"今天去面试了，聊了四十分钟。对方问我怎么处理冲突，我讲了一个真实项目。",
        "occurredAt":"2026-11-07","intent":"EXPLICIT"}}
    """.trimIndent()

    @Test
    fun `diary draft is parsed with body and title`() {
        val result = RecordProposalParser.parse(valid, "整理成日记吧")
        val draft = (result as RecordProposalParseResult.Success).draft!!
        assertTrue(draft.isDiary)
        assertTrue(draft.isComplete)
        assertTrue(draft.diaryBody!!.contains("面试"))
        assertTrue(draft.diaryBody!!.contains("冲突"))
        assertTrue(draft.diaryTitle == "一次面试")
    }

    @Test
    fun `empty diary body is rejected`() {
        val raw = """{"schemaVersion":1,"record":{"type":"DIARY","body":"   ","intent":"EXPLICIT"}}"""
        assertTrue(RecordProposalParser.parse(raw, "写日记") is RecordProposalParseResult.Failure)
    }

    @Test
    fun `no diary intent returns null draft`() {
        val result = RecordProposalParser.parse("""{"schemaVersion":1,"record":null}""", "今天有点难过")
        assertNull((result as RecordProposalParseResult.Success).draft)
    }

    @Test
    fun `diary is never auto applied even with explicit intent`() {
        val draft = (RecordProposalParser.parse(valid, "整理成日记吧") as RecordProposalParseResult.Success).draft!!
        assertFalse(RecordProposalRules.shouldAutoApply(draft, progressAuthorized = true))
    }

    @Test
    fun `explicit diary command triggers but pure emotion does not`() {
        assertTrue(DiaryIntentGate.explicit("把我们刚才聊的整理成日记吧"))
        assertTrue(DiaryIntentGate.looksRelevant("把我们刚才聊的整理成日记吧"))
        assertFalse(DiaryIntentGate.looksRelevant("今天有点难过。"))
        assertFalse(DiaryIntentGate.shouldSuggest("今天有点难过。"))
        assertTrue(
            DiaryIntentGate.shouldSuggest(
                "今天终于完成了拖了很久的面试，和面试官聊了四十分钟，回来的路上一直在想他说的话。",
            ),
        )
    }
}
