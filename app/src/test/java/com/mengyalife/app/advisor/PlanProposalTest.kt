package com.mengyalife.app.advisor

import com.mengyalife.app.domain.advisor.PlanChangeKind
import com.mengyalife.app.domain.advisor.PlanIntentGate
import com.mengyalife.app.domain.advisor.RecordProposalParseResult
import com.mengyalife.app.domain.advisor.RecordProposalParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProposalTest {
    @Test
    fun `future action becomes plan draft`() {
        val raw = """{"schemaVersion":1,"record":{"type":"PLAN","change":"CREATE",
            "title":"练三次吉他","timeScope":"这周","intent":"EXPLICIT"}}"""
        val draft = (RecordProposalParser.parse(raw, "这周准备练三次吉他") as RecordProposalParseResult.Success).draft!!
        assertTrue(draft.isPlan)
        assertEquals("练三次吉他", draft.planChange!!.title)
        assertEquals("这周", draft.planChange!!.timeScope)
    }

    @Test
    fun `status update parses plan and status`() {
        val raw = """{"schemaVersion":1,"record":{"type":"PLAN","change":"STATUS","planId":3,
            "status":"PAUSED","note":"先暂停","intent":"EXPLICIT"}}"""
        val draft = (RecordProposalParser.parse(raw, "跑步计划先暂停") as RecordProposalParseResult.Success).draft!!
        assertEquals(PlanChangeKind.STATUS, draft.planChange!!.change)
        assertEquals("PAUSED", draft.planChange!!.newStatus)
    }

    @Test
    fun `gate separates plans facts and wishes`() {
        assertTrue(PlanIntentGate.looksLikeCreate("这周我准备练三次吉他。"))
        assertTrue(PlanIntentGate.looksLikeCreate("周末打算把简历整理一下。"))
        assertTrue(PlanIntentGate.looksLikeStatusUpdate("跑步计划先暂停一下。"))
        assertFalse(PlanIntentGate.looksLikeCreate("今天练了半小时。"))
        assertFalse(PlanIntentGate.looksLikeCreate("最近有点想学吉他。"))
        assertTrue(PlanIntentGate.looksLikeProgress("昨天练了一次吉他。"))
        assertTrue(PlanIntentGate.looksLikeProgress("这周已经练了两次。"))
        assertFalse(PlanIntentGate.looksLikeProgress("三次都练完了。"))
    }

    @Test
    fun `no plan returns null`() {
        assertNull((RecordProposalParser.parse("""{"schemaVersion":1,"record":null}""", "随便聊聊") as RecordProposalParseResult.Success).draft)
    }
}
