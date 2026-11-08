package com.mengyalife.app.domain.goals

/**
 * 真实进展趋势（纯函数，可在 JVM 单元测试里直接验证）。
 *
 * 设计约束（Gate 2C）：
 * 1. 只使用 `goal_checkins` 里**用户手动录入**的数值，不做推断、不补点；
 * 2. **不同单位绝不画进同一张图**：按单位分组，每组一条序列；
 * 3. 非有限数（NaN / Infinity，历史脏数据或绕过校验写入的）一律丢弃；
 * 4. 只有 1 个点时保留该点，但调用方必须展示「无法判断趋势」的说明，
 *    不能编造曲线。
 */
object GoalTrends {

    /** 没有填写单位时的展示标签。 */
    const val UNIT_NONE = "无单位"

    /** 一条进展记录里与趋势相关的部分。 */
    data class MetricRecord(
        val checkinId: Long,
        val recordedAt: Long,
        val value: Double?,
        val unit: String?,
        val kind: String = ProgressKind.SNAPSHOT,
    )

    data class TrendPoint(
        val checkinId: Long,
        val recordedAt: Long,
        val value: Double,
    )

    /** 同一单位下的一组真实数据点，按时间升序。 */
    data class TrendSeries(
        val unitLabel: String,
        val points: List<TrendPoint>,
        val kind: String = ProgressKind.SNAPSHOT,
    ) {
        val latest: TrendPoint get() = points.last()
    }

    fun normalizeUnit(raw: String?): String =
        raw?.trim()?.takeIf { it.isNotEmpty() } ?: UNIT_NONE

    /**
     * 把记录按「单位」切成多条序列。
     *
     * @return 只包含有合法数值的记录；序列按首个数据点时间升序，
     *         单位相同的记录合并；空输入返回空列表。
     */
    fun seriesFrom(records: List<MetricRecord>): List<TrendSeries> {
        val valid = records.filter { record ->
            val value = record.value
            value != null && value.isFinite() && record.kind != ProgressKind.NONE
        }
        if (valid.isEmpty()) return emptyList()

        // 先按单位、再按语义（累计快照 / 增量）分组：两者绝不混进同一条曲线。
        return valid
            .groupBy { normalizeUnit(it.unit) to it.kind }
            .map { (key, rows) ->
                val (unit, kind) = key
                val points = rows
                    .sortedWith(compareBy<MetricRecord> { it.recordedAt }.thenBy { it.checkinId })
                    .map { TrendPoint(it.checkinId, it.recordedAt, it.value!!) }
                TrendSeries(unitLabel = unit, points = points, kind = kind)
            }
            .sortedWith(
                compareBy<TrendSeries> { it.kind != ProgressKind.SNAPSHOT }
                    .thenBy { it.points.first().recordedAt }
                    .thenBy { it.unitLabel },
            )
    }

    /** 横轴范围（真实时间），单点时为该点自身。 */
    fun timeRange(series: TrendSeries): LongRange {
        val first = series.points.first().recordedAt
        val last = series.points.last().recordedAt
        return first..last
    }

    /** 纵轴范围（真实数值），单点或等值时返回 [value, value]。 */
    fun valueRange(series: TrendSeries): ClosedFloatingPointRange<Double> {
        val values = series.points.map { it.value }
        return values.min()..values.max()
    }

    /**
     * 是否值得画折线。只有 ≥2 个点才连线；
     * 单点由界面画成「孤立数据点 + 说明」，绝不伪造曲线。
     */
    fun canDrawLine(series: TrendSeries): Boolean = series.points.size >= 2
}
