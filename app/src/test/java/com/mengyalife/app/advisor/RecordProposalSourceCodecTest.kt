package com.mengyalife.app.advisor

import com.mengyalife.app.data.advisor.RecordProposalSourceCodec
import com.mengyalife.app.domain.advisor.PlanChangeDraft
import com.mengyalife.app.domain.advisor.PlanChangeKind
import com.mengyalife.app.domain.advisor.RecordAmountKind
import com.mengyalife.app.domain.advisor.RecordIntent
import com.mengyalife.app.domain.advisor.RecordKind
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.insight.InsightReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordProposalSourceCodecTest {
    private fun planDraft(
        ids: List<String> = emptyList(),
        known: Boolean = true,
        reference: InsightReference? = null,
    ) = RecordProposalDraft(
        kind = RecordKind.PLAN,
        goalId = null,
        goalTitle = null,
        goalCandidates = emptyList(),
        amountKind = RecordAmountKind.NONE,
        metricValue = null,
        metricUnit = null,
        note = "读书计划",
        occurredAt = null,
        pendingFields = emptyList(),
        intent = RecordIntent.EXPLICIT,
        originalText = "这周读三次书",
        sourceMessageIds = ids,
        sourceLinkKnown = known,
        sourceInsightReference = reference,
        planChange = PlanChangeDraft(PlanChangeKind.CREATE, title = "读书计划"),
    )

    @Test
    fun `新候选稳定保存 Insight 指针和关联消息`() {
        val reference = InsightReference("report-7", "a".repeat(64) + "|2026-11-13", "insight-3")
        val raw = RecordProposalSourceCodec.encode(planDraft(listOf("user-1", "user-9"), reference = reference))
        val decoded = RecordProposalSourceCodec.decode(raw)

        assertTrue(decoded.known)
        assertFalse(decoded.invalid)
        assertEquals(listOf("user-1", "user-9"), decoded.messageIds)
        assertEquals(reference, decoded.insightReference)
    }

    @Test
    fun `旧版 JSON 数组来源保持普通草案兼容`() {
        val decoded = RecordProposalSourceCodec.decode("[\"old-user-1\",\"old-user-2\"]")

        assertEquals(listOf("old-user-1", "old-user-2"), decoded.messageIds)
        assertFalse(decoded.known)
        assertFalse(decoded.invalid)
        assertNull(decoded.insightReference)
    }

    @Test
    fun `新建无 Insight 来源草案明确记录无链接`() {
        val decoded = RecordProposalSourceCodec.decode(RecordProposalSourceCodec.encode(planDraft()) )

        assertTrue(decoded.known)
        assertFalse(decoded.invalid)
        assertNull(decoded.insightReference)
    }

    @Test
    fun `损坏的来源 envelope 不会被当成无来源普通草案`() {
        val decoded = RecordProposalSourceCodec.decode(
            """{"format":"record-source-v1","messageIds":[],"sourceLinkKnown":true,"insightReference":{"reportId":"r"}}""",
        )

        assertTrue(decoded.known)
        assertTrue(decoded.invalid)
        assertNull(decoded.insightReference)

        val missingKnownFlag = RecordProposalSourceCodec.decode(
            """{"format":"record-source-v1","messageIds":["old-user"],"insightReference":null}""",
        )
        assertTrue(missingKnownFlag.invalid)
    }
}
