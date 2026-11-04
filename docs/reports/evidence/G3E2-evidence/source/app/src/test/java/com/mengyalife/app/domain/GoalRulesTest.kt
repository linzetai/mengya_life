package com.mengyalife.app.domain

import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalRef
import com.mengyalife.app.domain.goals.GoalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalRulesTest {

    private val longTerm = GoalRef(id = 1L, level = GoalLevel.LONG_TERM, parentId = null)
    private val stage = GoalRef(id = 2L, level = GoalLevel.STAGE, parentId = 1L)

    // ---------------------------------------------------------------- 标题

    @Test
    fun `空标题被拒绝`() {
        assertEquals(GoalRules.ERROR_TITLE_REQUIRED, GoalRules.validateTitle("  "))
    }

    @Test
    fun `超长标题被拒绝`() {
        assertEquals(GoalRules.ERROR_TITLE_TOO_LONG, GoalRules.validateTitle("字".repeat(61)))
    }

    @Test
    fun `正常标题通过`() {
        assertNull(GoalRules.validateTitle("把身体状态维持在可持续的基准线上"))
    }

    // ---------------------------------------------------------------- 层级

    @Test
    fun `长期目标不能有父目标`() {
        assertEquals(GoalRules.ERROR_ROOT_HAS_PARENT, GoalRules.validateParent(GoalLevel.LONG_TERM, longTerm))
    }

    @Test
    fun `长期目标无父时合法`() {
        assertNull(GoalRules.validateParent(GoalLevel.LONG_TERM, null))
    }

    @Test
    fun `阶段目标必须有父`() {
        assertEquals(GoalRules.ERROR_PARENT_REQUIRED, GoalRules.validateParent(GoalLevel.STAGE, null))
    }

    @Test
    fun `行动必须有父`() {
        assertEquals(GoalRules.ERROR_ACTION_PARENT_REQUIRED, GoalRules.validateParent(GoalLevel.ACTION, null))
    }

    @Test
    fun `阶段目标挂在长期目标下合法`() {
        assertNull(GoalRules.validateParent(GoalLevel.STAGE, longTerm))
    }

    @Test
    fun `阶段目标挂在阶段目标下非法`() {
        assertEquals(GoalRules.ERROR_PARENT_LEVEL_MISMATCH, GoalRules.validateParent(GoalLevel.STAGE, stage))
    }

    @Test
    fun `行动挂在长期目标下非法（跳级）`() {
        assertEquals(
            GoalRules.ERROR_PARENT_LEVEL_MISMATCH,
            GoalRules.validateParent(GoalLevel.ACTION, longTerm),
        )
    }

    @Test
    fun `行动挂在阶段目标下合法`() {
        assertNull(GoalRules.validateParent(GoalLevel.ACTION, stage))
    }

    // ---------------------------------------------------------------- 环

    @Test
    fun `不能挂到自己下面`() {
        assertTrue(GoalRules.wouldCreateCycle(1L, 1L, listOf(longTerm)))
    }

    @Test
    fun `不能挂到自己的后代下面`() {
        val all = listOf(
            GoalRef(1L, GoalLevel.LONG_TERM, null),
            GoalRef(2L, GoalLevel.STAGE, 1L),
            GoalRef(3L, GoalLevel.ACTION, 2L),
        )
        assertTrue(GoalRules.wouldCreateCycle(1L, 3L, all))
        assertTrue(GoalRules.wouldCreateCycle(1L, 2L, all))
    }

    @Test
    fun `挂到无关分支不算环`() {
        val all = listOf(
            GoalRef(1L, GoalLevel.LONG_TERM, null),
            GoalRef(2L, GoalLevel.STAGE, 1L),
            GoalRef(9L, GoalLevel.LONG_TERM, null),
            GoalRef(10L, GoalLevel.STAGE, 9L),
        )
        assertFalse(GoalRules.wouldCreateCycle(2L, 10L, all))
    }

    @Test
    fun `父为空时不构成环`() {
        assertFalse(GoalRules.wouldCreateCycle(1L, null, listOf(longTerm)))
    }

    // ---------------------------------------------------------------- 日期 / 进展

    @Test
    fun `截止早于开始被拒绝`() {
        assertEquals(GoalRules.ERROR_DUE_BEFORE_START, GoalRules.validateDates(200L, 100L))
        assertNull(GoalRules.validateDates(100L, 200L))
        assertNull(GoalRules.validateDates(null, 100L))
    }

    @Test
    fun `空进展说明被拒绝`() {
        assertEquals(GoalRules.ERROR_NOTE_REQUIRED, GoalRules.validateCheckinNote("   "))
        assertNull(GoalRules.validateCheckinNote("完成了 3 次训练"))
    }

    @Test
    fun `非法指标数值被拒绝`() {
        assertEquals(GoalRules.ERROR_METRIC_INVALID, GoalRules.validateMetric("abc"))
        assertNull(GoalRules.validateMetric("3.5"))
        assertNull(GoalRules.validateMetric(""))
    }

    // ---------------------------------------------------------------- 指标：非法数

    @Test
    fun `NaN 与 Infinity 文本一律被拒绝`() {
        // toDoubleOrNull 会接受这些字面量，必须由 isFinite() 兜住
        listOf("NaN", "nan", "Infinity", "-Infinity", "1e999", "-1e999").forEach { raw ->
            assertEquals(
                "输入 $raw 时应拒绝",
                GoalRules.ERROR_METRIC_INVALID,
                GoalRules.validateMetric(raw),
            )
        }
    }

    @Test
    fun `NaN 与 Infinity 的 Double 值被拒绝`() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        ).forEach { value ->
            assertEquals(
                GoalRules.ERROR_METRIC_INVALID,
                GoalRules.validateMetricValue(value),
            )
        }
        assertNull(GoalRules.validateMetricValue(null))
        assertNull(GoalRules.validateMetricValue(0.0))
        assertNull(GoalRules.validateMetricValue(-12.5))
    }

    @Test
    fun `超出范围的数值被拒绝`() {
        assertEquals(
            GoalRules.ERROR_METRIC_TOO_LARGE,
            GoalRules.validateMetricValue(GoalRules.MAX_ABS_METRIC + 1),
        )
        assertEquals(
            GoalRules.ERROR_METRIC_TOO_LARGE,
            GoalRules.validateMetric("-99999999999"),
        )
    }

    @Test
    fun `parseMetric 不会把 NaN 变成有效值`() {
        assertNull(GoalRules.parseMetric("NaN"))
        assertNull(GoalRules.parseMetric("Infinity"))
        assertNull(GoalRules.parseMetric("abc"))
        assertNull(GoalRules.parseMetric("   "))
        assertEquals(3.5, GoalRules.parseMetric(" 3.5 ")!!, 0.0)
    }

    @Test
    fun `单位过长被拒绝且空白归一为空`() {
        assertEquals(
            GoalRules.ERROR_UNIT_TOO_LONG,
            GoalRules.validateUnit("字".repeat(GoalRules.MAX_UNIT + 1)),
        )
        assertNull(GoalRules.validateUnit(" 次/周 "))
        assertNull(GoalRules.normalizeUnit("   "))
        assertEquals("次/周", GoalRules.normalizeUnit(" 次/周 "))
    }
}
