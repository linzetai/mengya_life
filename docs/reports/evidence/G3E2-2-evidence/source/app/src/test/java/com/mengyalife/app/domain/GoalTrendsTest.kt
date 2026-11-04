package com.mengyalife.app.domain

import com.mengyalife.app.domain.goals.GoalTrends
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实趋势分组的纯函数测试：单位、顺序、单点、非法值。 */
class GoalTrendsTest {

    private fun record(
        id: Long,
        at: Long,
        value: Double?,
        unit: String?,
    ) = GoalTrends.MetricRecord(id, at, value, unit)

    @Test
    fun `没有数值记录时返回空列表`() {
        assertTrue(GoalTrends.seriesFrom(emptyList()).isEmpty())
        assertTrue(
            GoalTrends.seriesFrom(
                listOf(record(1, 1000, null, "次"), record(2, 2000, null, null)),
            ).isEmpty(),
        )
    }

    @Test
    fun `NaN 与 Infinity 被过滤掉`() {
        val series = GoalTrends.seriesFrom(
            listOf(
                record(1, 1000, Double.NaN, "次"),
                record(2, 2000, Double.POSITIVE_INFINITY, "次"),
                record(3, 3000, 2.0, "次"),
            ),
        )
        assertEquals(1, series.size)
        assertEquals(1, series[0].points.size)
        assertEquals(3L, series[0].points[0].checkinId)
    }

    @Test
    fun `单点保留为一条只有一个点的序列`() {
        val series = GoalTrends.seriesFrom(listOf(record(7, 5_000, 42.0, "分")))
        assertEquals(1, series.size)
        assertEquals(1, series[0].points.size)
        assertEquals("分", series[0].unitLabel)
        assertFalse(GoalTrends.canDrawLine(series[0]))
        assertEquals(42.0, series[0].latest.value, 0.0)
    }

    @Test
    fun `同一单位按真实时间升序排列`() {
        val series = GoalTrends.seriesFrom(
            listOf(
                record(3, 3000, 3.0, "次/周"),
                record(1, 1000, 1.0, "次/周"),
                record(2, 2000, 2.0, "次/周"),
            ),
        )
        assertEquals(listOf(1L, 2L, 3L), series[0].points.map { it.checkinId })
        assertEquals(listOf(1.0, 2.0, 3.0), series[0].points.map { it.value })
        assertTrue(GoalTrends.canDrawLine(series[0]))
    }

    @Test
    fun `不同单位拆成不同序列，绝不混画`() {
        val series = GoalTrends.seriesFrom(
            listOf(
                record(1, 1000, 3.0, "小时"),
                record(2, 2000, 70.0, "公斤"),
                record(3, 3000, 4.0, "小时"),
            ),
        )
        assertEquals(2, series.size)
        val byUnit = series.associateBy { it.unitLabel }
        assertEquals(listOf(3.0, 4.0), byUnit.getValue("小时").points.map { it.value })
        assertEquals(listOf(70.0), byUnit.getValue("公斤").points.map { it.value })
        // 不做任何换算：两个序列各自保留原值
        assertEquals(3, series.sumOf { it.points.size })
    }

    @Test
    fun `空单位归一为无单位`() {
        val series = GoalTrends.seriesFrom(
            listOf(record(1, 1000, 1.0, "  "), record(2, 2000, 2.0, null)),
        )
        assertEquals(1, series.size)
        assertEquals(GoalTrends.UNIT_NONE, series[0].unitLabel)
    }

    @Test
    fun `时间与数值范围来自真实数据`() {
        val series = GoalTrends.seriesFrom(
            listOf(record(1, 1000, -2.0, "度"), record(2, 5000, 8.5, "度")),
        )[0]
        assertEquals(1000L..5000L, GoalTrends.timeRange(series))
        assertEquals(-2.0, GoalTrends.valueRange(series).start, 0.0)
        assertEquals(8.5, GoalTrends.valueRange(series).endInclusive, 0.0)
    }
}
