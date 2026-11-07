package com.mengyalife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.util.MetricFormat

/**
 * 极简折线图（自绘 Canvas，不引入第三方图表库）。
 * 数值约定为 0..100 的演示分数。
 */
@Composable
fun MultiLineChart(
    series: List<DemoData.TrendSeries>,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.outline,
    ),
    showGrid: Boolean = true,
    strokeWidth: Dp = 2.dp,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    // 按数据范围自适应纵轴（留 15% 余量），避免曲线全挤在中间
    val all = series.flatMap { it.values }
    val lo = all.minOrNull() ?: 0f
    val hi = all.maxOrNull() ?: 100f
    val pad = ((hi - lo) * 0.15f).coerceAtLeast(4f)
    val yMin = (lo - pad).coerceAtLeast(0f)
    val yMax = (hi + pad).coerceAtMost(100f)
    val span = (yMax - yMin).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        if (showGrid) {
            val rows = 4
            for (i in 0..rows) {
                val y = size.height * i / rows
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        series.forEachIndexed { index, s ->
            if (s.values.size < 2) return@forEachIndexed
            val color = colors[index % colors.size]
            val path = Path()
            s.values.forEachIndexed { i, raw ->
                val v = raw.coerceIn(yMin, yMax)
                val x = size.width * i / (s.values.size - 1)
                val y = size.height - ((v - yMin) / span) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // 末点高亮
            val last = s.values.last().coerceIn(yMin, yMax)
            drawCircle(
                color = color,
                radius = strokeWidth.toPx() * 1.6f,
                center = Offset(size.width, size.height - ((last - yMin) / span) * size.height),
            )
        }
    }
}

/** 单序列迷你走势线，用于总览页的趋势预览。 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp,
) {
    val lo = values.minOrNull() ?: 0f
    val hi = values.maxOrNull() ?: 100f
    val pad = ((hi - lo) * 0.2f).coerceAtLeast(3f)
    val yMin = (lo - pad).coerceAtLeast(0f)
    val yMax = (hi + pad).coerceAtMost(100f)
    val span = (yMax - yMin).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        if (values.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas
        val path = Path()
        values.forEachIndexed { i, raw ->
            val v = raw.coerceIn(yMin, yMax)
            val x = size.width * i / (values.size - 1)
            val y = size.height - ((v - yMin) / span) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Gate 2C：真实数值趋势图
//
// 与上面的演示图不同，这张图的所有坐标都来自真实记录：
// - 横轴是 goal_checkins.recorded_at（真实时间，不是等距的「第 N 次」）；
// - 纵轴是该单位的真实指标值；
// - 单点只画一个点，不连线；调用方负责展示「只有一条记录」的说明。
// 仍然使用 Compose Canvas，不引入图表库。
// ---------------------------------------------------------------------------

/** 纵轴刻度标签宽度，与下方时间标签对齐用。 */
private val TrendAxisWidth = 58.dp

@Composable
fun MetricTrendChart(
    series: com.mengyalife.app.domain.goals.GoalTrends.TrendSeries,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    chartHeight: Dp = 132.dp,
) {
    val points = series.points
    if (points.isEmpty()) return

    val values = points.map { it.value }
    val lo = values.min()
    val hi = values.max()
    val pad = ((hi - lo) * 0.15).takeIf { it > 0.0 }
        ?: (kotlin.math.abs(hi) * 0.1).coerceAtLeast(1.0)
    val yMin = lo - pad
    val yMax = hi + pad
    val span = (yMax - yMin).coerceAtLeast(1e-9)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.surface

    Column(modifier = modifier) {
        Row {
            Column(
                modifier = Modifier
                    .width(TrendAxisWidth)
                    .height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                AxisLabel(axisText(yMax, span))
                AxisLabel(axisText((yMax + yMin) / 2, span))
                AxisLabel(axisText(yMin, span))
            }
            Spacer(Modifier.width(6.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight),
            ) {
                if (size.width <= 0f || size.height <= 0f) return@Canvas

                val padTop = 5.dp.toPx()
                val padBottom = 5.dp.toPx()
                val padLeft = 4.dp.toPx()
                val padRight = 8.dp.toPx()
                val plotLeft = padLeft
                val plotWidth = (size.width - padLeft - padRight).coerceAtLeast(1f)
                val plotTop = padTop
                val plotHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val plotBottom = plotTop + plotHeight

                val t0 = points.first().recordedAt
                val t1 = points.last().recordedAt
                fun xAt(t: Long): Float = if (t1 == t0) {
                    plotLeft + plotWidth / 2f
                } else {
                    plotLeft + plotWidth * ((t - t0).toFloat() / (t1 - t0).toFloat())
                }

                fun yAt(v: Double): Float =
                    plotTop + plotHeight * (1f - ((v - yMin) / span).toFloat())

                // 3 条横向参考线（与左侧刻度对应）
                for (i in 0..2) {
                    val y = plotTop + plotHeight * i / 2f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                if (points.size >= 2) {
                    // 线下的浅色区域，让「变化」更易读（仅 ≥2 点才有面积）
                    val area = Path()
                    area.moveTo(xAt(points.first().recordedAt), plotBottom)
                    points.forEach { area.lineTo(xAt(it.recordedAt), yAt(it.value)) }
                    area.lineTo(xAt(points.last().recordedAt), plotBottom)
                    area.close()
                    drawPath(path = area, color = color.copy(alpha = 0.07f))

                    val line = Path()
                    points.forEachIndexed { index, point ->
                        val x = xAt(point.recordedAt)
                        val y = yAt(point.value)
                        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    drawPath(
                        path = line,
                        color = color,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }

                points.forEachIndexed { index, point ->
                    val center = Offset(xAt(point.recordedAt), yAt(point.value))
                    val isLast = index == points.lastIndex
                    val radius = if (isLast) 4.5.dp.toPx() else 3.2.dp.toPx()
                    // 外圈底色描边，避免点与线糊在一起
                    drawCircle(color = dotColor, radius = radius + 1.2.dp.toPx(), center = center)
                    drawCircle(color = color, radius = radius, center = center)
                }

                if (points.size == 1) {
                    // 单点：加一圈很淡的光晕，强调「这里只有一个数据点」
                    drawCircle(
                        color = color.copy(alpha = 0.16f),
                        radius = 11.dp.toPx(),
                        center = Offset(xAt(points.first().recordedAt), yAt(points.first().value)),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(TrendAxisWidth + 6.dp))
            Text(
                text = chartAxisLabel(points.first().recordedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = chartAxisLabel(points.last().recordedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 坐标轴刻度：按跨度控制小数位，避免 14.875 这类运算噪声。 */
private fun axisText(value: Double, span: Double): String {
    val decimals = when {
        span >= 20.0 -> 0
        span >= 1.0 -> 1
        span >= 0.1 -> 2
        else -> 3
    }
    return java.math.BigDecimal.valueOf(value)
        .setScale(decimals, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** 横轴日期：同年只显示月日，跨年显示年份。 */
private fun chartAxisLabel(epochMillis: Long): String {
    val time = java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
    val now = java.time.ZonedDateTime.now()
    val pattern = if (time.year == now.year) "M月d日" else "yy年M月d日"
    return java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault())
        .format(time)
}

/**
 * 数据点明细：每个点都给出真实时间与数值，和图表一一对应。
 * 只展示最近 [maxRows] 条，更早的记录仍保留在「进展记录」列表里。
 */
@Composable
fun TrendPointList(
    series: com.mengyalife.app.domain.goals.GoalTrends.TrendSeries,
    modifier: Modifier = Modifier,
    maxRows: Int = 8,
) {
    val unit = series.unitLabel.takeIf {
        it != com.mengyalife.app.domain.goals.GoalTrends.UNIT_NONE
    }
    Column(modifier = modifier.fillMaxWidth()) {
        series.points.takeLast(maxRows).forEach { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = com.mengyalife.app.util.Timestamps.formatDateTime(point.recordedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = com.mengyalife.app.util.MetricFormat.withUnit(point.value, unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (series.points.size > maxRows) {
            Text(
                text = "另有 ${series.points.size - maxRows} 条更早的记录，见下方进展历史",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
