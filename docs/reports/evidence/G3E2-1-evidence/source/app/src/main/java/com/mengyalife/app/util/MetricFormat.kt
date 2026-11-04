package com.mengyalife.app.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 指标数值的展示格式。
 *
 * 规则（与数据库无关，纯函数，可单测）：
 * - 整数不显示小数点：`3.0` → `3`；
 * - 非整数最多保留 4 位小数并去掉尾随 0：`3.14159` → `3.1416`；
 * - 不使用科学计数法（避免 `1.0E7` 这类用户看不懂的坐标轴）；
 * - 不做四舍五入以外的推断。
 */
object MetricFormat {

    fun number(value: Double): String {
        if (!value.isFinite()) return "—"
        if (value == 0.0) return "0"
        val rounded = BigDecimal.valueOf(value)
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        val plain = rounded.toPlainString()
        return if (plain == "-0") "0" else plain
    }

    fun withUnit(value: Double, unit: String?): String {
        val pretty = number(value)
        return unit?.trim()?.takeIf { it.isNotEmpty() }?.let { "$pretty $it" } ?: pretty
    }
}
