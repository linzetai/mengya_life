package com.mengyalife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
