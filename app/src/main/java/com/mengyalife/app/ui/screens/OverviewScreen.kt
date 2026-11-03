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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.Sparkline
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.components.ThinProgressBar
import com.mengyalife.app.util.Timestamps

/**
 * 总览。
 *
 * Gate 2B 起这里是**混合页**：
 * - 「长期目标」区块读的是真实 Room 数据（无演示标签）；
 * - 「人生方向 / 成长趋势 / 近期洞察」仍是演示内容，逐张卡片标注「演示」，
 *   不让人误以为是真实统计。
 */
@Composable
fun OverviewScreen(
    listState: LazyListState,
    goals: GoalSnapshot,
    onOpenGoals: () -> Unit,
    onCreateGoal: () -> Unit,
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
                subtitle = "长期目标读的是你的真实记录，其余为演示内容",
                showDemoTag = false,
            )
        }

        item {
            SectionCard(
                title = "长期目标",
                subtitle = if (goals.longTermCount == 0) "还没有长期目标" else "共 ${goals.longTermCount} 个长期目标",
                modifier = Modifier.testTag("overview_goals"),
                trailing = {
                    if (goals.totalGoals > 0) {
                        StatusPill(text = "${goals.inProgressCount} 个进行中")
                    }
                },
            ) {
                if (goals.roots.isEmpty()) {
                    InlineNote("还没有任何目标。先建一个长期目标，总览才会显示真实进展。")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onCreateGoal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_create_goal"),
                    ) {
                        Text("新建长期目标")
                    }
                } else {
                    goals.roots.take(3).forEachIndexed { index, node ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        OverviewGoalRow(node = node)
                    }
                    Spacer(Modifier.height(12.dp))
                    InlineNote(
                        goals.lastCheckinAt?.let { "最近一次进展记录：" + Timestamps.formatDateTime(it) }
                            ?: "还没有进展记录",
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenGoals,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_open_goals"),
                    ) {
                        Text("查看全部目标")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "人生方向",
                subtitle = "十年尺度上，我希望一直变好的事",
                trailing = { StatusPill(text = "演示") },
            ) {
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
                title = "成长趋势预览",
                subtitle = "演示曲线，不是你的真实数据",
                trailing = { StatusPill(text = "演示") },
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "72",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "/ 100 健康指数（演示）",
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
                InlineNote("这一块尚未接入真实统计；真实进展以上方「长期目标」为准。")
            }
        }

        item {
            SectionCard(
                title = "近期洞察",
                subtitle = "演示文案，未基于你的日记做分析",
                trailing = { StatusPill(text = "演示") },
            ) {
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
private fun OverviewGoalRow(node: GoalNode) {
    val goal = node.goal
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = goal.domain.label)
            Spacer(Modifier.size(8.dp))
            Text(
                text = goal.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusChip(goal.status)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append("${node.children.size} 个阶段")
                    if (node.checkinCount > 0) append(" · ${node.checkinCount} 次进展")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            node.lastCheckinAt?.let {
                Text(
                    text = Timestamps.formatDate(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 真实进度：按「已完成子目标占比」计算，而不是凭空给一个人生综合分
        val done = node.children.count { it.goal.status == com.mengyalife.app.domain.goals.GoalStatus.DONE }
        ThinProgressBar(
            progress = if (node.children.isEmpty()) 0f else done.toFloat() / node.children.size,
        )
    }
}
