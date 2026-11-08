package com.mengyalife.app.advisor

import com.mengyalife.app.domain.goals.GoalTrends
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.domain.goals.ProgressNotes
import com.mengyalife.app.domain.goals.ProgressUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.16A 记录语义：
 * - 增量与累计快照绝不混进同一条趋势；
 * - 单位缺失不静默合并，可可靠换算时保留原始值 + 归一值；
 * - 修改数值后说明里的旧数字同步更新。
 */
class RecordingSemanticsTest {

    private fun record(id: Long, at: Long, value: Double, unit: String?, kind: String) =
        GoalTrends.MetricRecord(id, at, value, unit, kind)

    @Test
    fun `delta and snapshot never share one series`() {
        val series = GoalTrends.seriesFrom(
            listOf(
                record(1, 100, 2000.0, "元", ProgressKind.DELTA),
                record(2, 200, 1500.0, "元", ProgressKind.DELTA),
                record(3, 300, 147000.0, "元", ProgressKind.SNAPSHOT),
            ),
        )
        assertEquals(2, series.size)
        val snapshot = series.first { it.kind == ProgressKind.SNAPSHOT }
        val delta = series.first { it.kind == ProgressKind.DELTA }
        assertEquals(listOf(147000.0), snapshot.points.map { it.value })
        // 两次增量各自是独立贡献，不会被画成累计下降。
        assertEquals(listOf(2000.0, 1500.0), delta.points.map { it.value })
        assertFalse(snapshot.points.any { it.value == 1500.0 })
    }

    @Test
    fun `qualitative records are excluded from numeric series`() {
        val series = GoalTrends.seriesFrom(
            listOf(record(1, 100, 0.0, null, ProgressKind.NONE)),
        )
        assertTrue(series.isEmpty())
    }

    @Test
    fun `missing unit inherits only a unique existing unit`() {
        val inherited = ProgressUnits.resolveAgainstExisting(2000.0, null, setOf("元"))
        assertEquals(2000.0, inherited!!.value!!, 0.0001)
        assertEquals("元", inherited.unit)
        assertTrue(inherited.normalized)

        val ambiguous = ProgressUnits.resolveAgainstExisting(2000.0, null, setOf("元", "小时"))
        assertNull(ambiguous)
    }

    @Test
    fun `convertible units keep original value and normalized value`() {
        val converted = ProgressUnits.normalize(14.7, "万元", "元")
        assertEquals(147000.0, converted.value!!, 0.0001)
        assertEquals("元", converted.unit)
        assertTrue(converted.normalized)

        val notConvertible = ProgressUnits.normalize(2.0, "小时", "元")
        assertEquals(2.0, notConvertible.value!!, 0.0001)
        assertEquals("小时", notConvertible.unit)
        assertFalse(notConvertible.normalized)
    }

    @Test
    fun `editing value rewrites the old number in the note`() {
        assertEquals("今天又存了 1500 元", ProgressNotes.rewriteValue("今天又存了 2000 元", 2000.0, 1500.0))
        // 说明里没有旧数字时不编造。
        assertEquals("完成了一次面试", ProgressNotes.rewriteValue("完成了一次面试", 2000.0, 1500.0))
    }

    @Test
    fun `kind labels stay explicit for the card and chart`() {
        assertEquals("新增", ProgressKind.label(ProgressKind.DELTA))
        assertEquals("累计快照", ProgressKind.label(ProgressKind.SNAPSHOT))
        assertEquals("定性进展", ProgressKind.label(ProgressKind.NONE))
    }
}
