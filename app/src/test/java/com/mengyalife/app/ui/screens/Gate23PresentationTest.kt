package com.mengyalife.app.ui.screens

import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.GoalTrend
import com.mengyalife.app.domain.goals.GoalTrends
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.domain.advisor.AiConsentState
import com.mengyalife.app.domain.insight.InsightReport
import com.mengyalife.app.domain.insight.InsightFact
import com.mengyalife.app.ui.insight.additionalInsightFacts
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.ui.insight.historicalInsightEvidence
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightSourceRef
import com.mengyalife.app.domain.insight.InsightType
import com.mengyalife.app.ui.insight.distinctInsightCards
import com.mengyalife.app.ui.insight.friendlyInsightSource
import org.junit.Assert.*
import org.junit.Test

class Gate23PresentationTest {
    private fun trend(id: Long, kind: String, first: Double, last: Double) = GoalTrend(
        id, "目标$id", GoalTrends.TrendSeries("分钟", listOf(
            GoalTrends.TrendPoint(1, 100, first), GoalTrends.TrendPoint(2, 200, last),
        ), kind),
    )

    @Test fun `actual snapshot change precedes activity counts`() {
        val lines = buildChanges(listOf(
            trend(1, ProgressKind.DELTA, 10.0, 5.0),
            trend(2, ProgressKind.SNAPSHOT, 20.0, 30.0),
        ), null, emptyList(), 0, 300)
        assertTrue(lines.first().text.contains("目标2"))
        assertTrue(lines.first().text.contains("累计从"))
    }

    @Test fun `delta records never imply cumulative decline`() {
        val text = buildChanges(listOf(trend(1, ProgressKind.DELTA, 20.0, 5.0)), null, emptyList(), 0, 300).first().text
        assertTrue(text.contains("新增记录"))
        assertFalse(text.contains("累计从"))
        assertFalse(text.contains("下降"))
    }

    @Test fun `journal facts use selected window and exclude future records`() {
        fun entry(at: Long) = JournalEntryEntity(id = at, body = "记录", createdAt = at, updatedAt = at)
        val lines = buildChanges(emptyList(), null, listOf(entry(50), entry(100), entry(200), entry(301)), 100, 300)
        assertEquals(1, lines.size)
        assertEquals("所选时段留下了 2 篇日记。", lines.single().text)
        assertFalse(lines.single().text.contains("这周"))
    }

    @Test fun `single point never produces trend conclusion`() {
        val one = trend(1, ProgressKind.SNAPSHOT, 10.0, 20.0)
        assertTrue(buildChanges(listOf(one.copy(series = one.series.copy(points = one.points.take(1)))), null, emptyList(), 0, 300).isEmpty())
    }

    @Test fun `historical evidence is cleared on consent withdrawal or epoch change`() {
        val report = InsightReport("old", "v1", InsightReportStatus.READY, "", emptyList(), emptyList(), 0, 100, 100)
        val consent = AiConsentState(granted = true, epoch = 7)
        assertSame(report, historicalInsightEvidence(report, 7, consent, consent))
        assertNull(historicalInsightEvidence(report, 7, consent, consent.copy(granted = false)))
        assertNull(historicalInsightEvidence(report, 7, consent, consent.copy(epoch = 8)))
        assertNull(historicalInsightEvidence(report, 6, consent, consent))
        assertNull(historicalInsightEvidence(report, null, consent, consent))
    }

    @Test fun `explanation quoted facts are not repeated but distinct evidence is kept`() {
        val quoted = InsightFact("a", "本周记录了 2 次进展。", emptyList())
        val additional = InsightFact("b", "最近一次记录在周五。", emptyList())
        val facts = listOf(quoted, additional, additional.copy(id = "c"))
        assertEquals(listOf(additional), additionalInsightFacts("观察：本周记录了2次进展。可以继续。", facts))
        assertEquals(listOf(quoted, additional), additionalInsightFacts("尚需更多解释", facts))
        assertEquals(3, facts.size)
    }

    @Test fun `sources are readable without internal ids`() {
        assertEquals("进展记录 · 跑步", friendlyInsightSource(InsightSourceRef("CHECKIN", "123", "跑步")))
        assertEquals("日记", friendlyInsightSource(InsightSourceRef("JOURNAL", "123", "")))
        assertEquals("原始记录 · 留下的片段", friendlyInsightSource(InsightSourceRef("FUTURE_TYPE", "123", "留下的片段")))
    }

    @Test fun `duplicate conclusions are one entry without mutating evidence`() {
        val insight = GrowthInsight("1", InsightType.SUGGESTION, "先走一步", "解释", true, null, emptyList())
        val cards = distinctInsightCards(listOf(insight, insight.copy(id = "2", title = " 先走一步 ")))
        assertEquals(listOf(insight), cards)
    }
}
