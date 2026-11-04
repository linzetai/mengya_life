package com.mengyalife.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 指标数值格式化：坐标轴与数据点明细共用，必须稳定、可读、无科学计数法。 */
class MetricFormatTest {

    @Test
    fun `整数不显示小数点`() {
        assertEquals("3", MetricFormat.number(3.0))
        assertEquals("-70", MetricFormat.number(-70.0))
        assertEquals("0", MetricFormat.number(0.0))
    }

    @Test
    fun `最多两位小数并去掉尾随 0`() {
        assertEquals("71.5", MetricFormat.number(71.5))
        assertEquals("3.1416", MetricFormat.number(3.14159))
        assertEquals("3.1", MetricFormat.number(3.10))
        assertEquals("0.25", MetricFormat.number(0.25))
    }

    @Test
    fun `不使用科学计数法`() {
        assertEquals("100000000", MetricFormat.number(1.0E8))
        assertEquals("0.0001", MetricFormat.number(0.0001))
        assertEquals("0.005", MetricFormat.number(0.005))
    }

    @Test
    fun `非法值显示破折号而不是 NaN`() {
        assertEquals("—", MetricFormat.number(Double.NaN))
        assertEquals("—", MetricFormat.number(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `单位拼接`() {
        assertEquals("3.5 kg", MetricFormat.withUnit(3.5, "kg"))
        assertEquals("3.5", MetricFormat.withUnit(3.5, null))
        assertEquals("3.5", MetricFormat.withUnit(3.5, "   "))
        assertEquals("70 次/周", MetricFormat.withUnit(70.0, " 次/周 "))
    }
}
