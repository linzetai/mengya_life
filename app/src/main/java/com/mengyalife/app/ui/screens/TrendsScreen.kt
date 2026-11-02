package com.mengyalife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MultiLineChart
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.components.ThinProgressBar
import com.mengyalife.app.ui.theme.ChartGreen
import com.mengyalife.app.ui.theme.ChartOchre
import com.mengyalife.app.ui.theme.ChartSlate

/**
 * 趋势：时间序列图表、多维度比较、趋势摘要。
 * 图表为自绘 Canvas，不引入第三方图表库。
 */
@Composable
fun TrendsScreen(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val chartColors = listOf(ChartGreen, ChartOchre, ChartSlate)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "看见变化，而不是凭感觉",
                subtitle = "近 12 周走势与多维度比较",
            )
        }

        item {
            SectionCard(
                title = "维度走势",
                subtitle = "近 12 周，0–100 的演示评分",
                trailing = { StatusPill(text = "12 周") },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DemoData.trendSeries.forEachIndexed { index, series ->
                        LegendDot(color = chartColors[index % chartColors.size], label = series.name)
                    }
                }
                Spacer(Modifier.height(14.dp))
                MultiLineChart(
                    series = DemoData.trendSeries,
                    colors = chartColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DemoData.trendWeeks.forEachIndexed { index, label ->
                        if (index % 3 == 0) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "多维度比较", subtitle = "当前分数与近 4 周变化") {
                DemoData.dimensionScores.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${item.score}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = if (item.delta >= 0) "+${item.delta}" else "${item.delta}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.delta >= 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ThinProgressBar(progress = item.score / 100f)
                    }
                }
            }
        }

        item {
            SectionCard(title = "趋势摘要", subtitle = "可读的结论，而不是罗列数字") {
                DemoData.trendSummary.forEachIndexed { index, text ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    BulletLine(text = text, index = index + 1)
                }
            }
        }

        item {
            SectionCard(title = "数据说明") {
                InlineNote("以上曲线与分数均为内置演示数据，不代表任何真实测量结果。")
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BulletLine(text: String, index: Int) {
    Row {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(width = 18.dp, height = 20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
