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
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.data.repository.GoalTrend
import com.mengyalife.app.data.repository.TrendSnapshot
import com.mengyalife.app.domain.goals.GoalTrends
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MetricTrendChart
import com.mengyalife.app.ui.components.MultiLineChart
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.TrendPointList
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.components.ThinProgressBar
import com.mengyalife.app.ui.theme.ChartGreen
import com.mengyalife.app.ui.theme.ChartOchre
import com.mengyalife.app.ui.theme.ChartSlate

/**
 * 趋势页 = **真实数值进展** + **明确标注的演示区**。
 *
 * 上半部分只读 `goal_checkins` 里用户手动填写的数值：
 * 横轴是真实时间、纵轴是真实指标值，按单位分开绘制，不做评分。
 * 下半部分保留 Gate 1/2 的演示图与摘要，但整体放入「演示区」并继续逐张标注，
 * 避免和真实趋势混淆。
 */
@Composable
fun TrendsScreen(
    listState: LazyListState,
    trends: TrendSnapshot,
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
                subtitle = "你的真实数值进展 + 明确标注的演示区",
                showDemoTag = false,
            )
        }

        // ---------------- 真实进展（Gate 2C） ----------------
        item {
            SectionCard(
                title = "我的数值进展",
                subtitle = "只来自你在目标里手动记录的进展",
                modifier = Modifier.testTag("trend_real_section"),
                trailing = { StatusPill(text = "真实记录") },
            ) {
                if (trends.isEmpty) {
                    Text(
                        text = "还没有可用于趋势的数值记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("trend_real_empty"),
                    )
                    Spacer(Modifier.height(8.dp))
                    InlineNote(
                        "在目标页 → 记录进展时填写「数值」与「单位」，这里就会出现真实曲线。" +
                            "只有文字说明、没有数值的记录不会画图。",
                    )
                    Spacer(Modifier.height(6.dp))
                    InlineNote(
                        "不会根据日记或演示数据推断趋势，也不会给出「人生整体评分」。",
                    )
                } else {
                    trends.trends.take(3).forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(18.dp))
                        GoalTrendBlock(trend = item, index = index)
                    }
                    Spacer(Modifier.height(14.dp))
                    InlineNote(
                        "共 ${trends.numericCheckinCount} 条数值记录，覆盖 " +
                            "${trends.goalsWithNumeric} 个目标。" +
                            if (trends.trends.size > 3) {
                                "这里只显示最近 3 组，共 ${trends.trends.size} 组。"
                            } else {
                                ""
                            },
                    )
                    Spacer(Modifier.height(6.dp))
                    InlineNote(
                        "同一目标的不同单位分开绘制，不做单位换算；" +
                            "只有 1 条记录时只显示单点，不伪造曲线。",
                    )
                }
            }
        }

        // ---------------- 演示区（明确分隔） ----------------
        item {
            Column(modifier = Modifier.testTag("trend_demo_section")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "演示区（非真实数据）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(text = "演示")
                }
                Spacer(Modifier.height(6.dp))
                InlineNote(
                    "以下图表与分数都是内置演示内容，与你的记录、日记、目标进展无关；" +
                        "仅用于展示界面结构。",
                )
            }
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
private fun GoalTrendBlock(trend: GoalTrend, index: Int) {
    val unit = trend.unitLabel.takeIf { it != GoalTrends.UNIT_NONE }
    Column(modifier = Modifier.testTag("trend_block_$index")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = trend.goalTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusPill(text = trend.unitLabel)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "最近一次：" +
                com.mengyalife.app.util.MetricFormat.withUnit(trend.latest.value, unit) +
                " · ${trend.points.size} 条记录",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        MetricTrendChart(series = trend.series, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        if (trend.points.size == 1) {
            Text(
                text = "只有 1 条数值记录，无法判断趋势；这里只显示该数据点。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("trend_block_single_$index"),
            )
        } else {
            Text(
                text = "横轴为真实记录时间，纵轴为指标值。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        TrendPointList(series = trend.series, maxRows = 5)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "数据来源：你手动记录的进展",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("trend_block_source_$index"),
        )
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
