package com.mengyalife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.Sparkline
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.components.ThinProgressBar

/**
 * 总览：人生方向、长期目标摘要、成长趋势预览、近期洞察、AI 顾问入口。
 */
@Composable
fun OverviewScreen(
    listState: LazyListState,
    onOpenAdvisor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "你正在走的方向",
                subtitle = "以及最近发生的变化",
            )
        }

        item {
            SectionCard(title = "人生方向", subtitle = "十年尺度上，我希望一直变好的事") {
                Text(
                    text = DemoData.lifeDirectionTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = DemoData.lifeDirectionStatement,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoData.lifePillars.take(4).forEach { pillar ->
                        StatusPill(text = pillar)
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "长期目标摘要",
                subtitle = "四条主线，各自的推进情况",
                trailing = { StatusPill(text = "${DemoData.goals.size} 条") },
            ) {
                DemoData.goals.take(3).forEachIndexed { index, goal ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    GoalSummaryRow(
                        dimension = goal.dimension.label,
                        title = goal.title,
                        progress = goal.progress,
                    )
                }
                Spacer(Modifier.height(12.dp))
                InlineNote("当前只展示四条主线中的三条，完整列表见「目标」页。")
            }
        }

        item {
            SectionCard(
                title = "成长趋势预览",
                subtitle = "近 12 周健康维度走势",
                trailing = { StatusPill(text = "+8") },
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "72",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "/ 100 健康指数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Sparkline(
                    values = DemoData.trendSeries[0].values,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniMetric(label = "目标推进", value = "41%")
                    MiniMetric(label = "连续记录", value = "12 天")
                    MiniMetric(label = "本周投入", value = "6.5 h")
                }
            }
        }

        item {
            SectionCard(title = "近期洞察", subtitle = "从趋势与日记中归纳出的观察") {
                DemoData.insights.forEachIndexed { index, insight ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    Column {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = insight.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "AI 顾问",
                subtitle = "把方向变成下一步可执行的动作",
                trailing = { StatusPill(text = "后续接入") },
            ) {
                Text(
                    text = "当前版本不接入任何模型服务，仅展示入口与对话界面结构。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenAdvisor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overview_open_advisor"),
                ) {
                    Text("进入顾问")
                }
            }
        }
    }
}

@Composable
private fun GoalSummaryRow(dimension: String, title: String, progress: Float) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = dimension)
            Spacer(Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        ThinProgressBar(progress = progress)
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
