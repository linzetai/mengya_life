package com.mengyalife.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.components.ThinProgressBar

/**
 * 目标：财富 / 职业 / 健康 / 生活多维度，
 * 以及「长期 → 阶段 → 短期」三层结构。
 */
@Composable
fun GoalsScreen(
    listState: LazyListState,
    selectedDimension: DemoData.GoalDimension,
    onDimensionChange: (DemoData.GoalDimension) -> Unit,
    doneShortGoals: Set<String>,
    onToggleShortGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleGoals = if (selectedDimension == DemoData.GoalDimension.ALL) {
        DemoData.goals
    } else {
        DemoData.goals.filter { it.dimension == selectedDimension }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "把方向拆成能推进的层级",
                subtitle = "长期目标 → 阶段目标 → 短期动作",
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DemoData.GoalDimension.entries.forEach { dimension ->
                    FilterChip(
                        selected = selectedDimension == dimension,
                        onClick = { onDimensionChange(dimension) },
                        label = { Text(dimension.label) },
                        modifier = Modifier.testTag(dimension.testTag),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        if (visibleGoals.isEmpty()) {
            item {
                SectionCard(title = "该维度暂无目标") {
                    InlineNote("当前筛选条件下没有演示目标，切换其它维度查看。")
                }
            }
        }

        items(visibleGoals.size) { index ->
            GoalCard(
                goal = visibleGoals[index],
                doneShortGoals = doneShortGoals,
                onToggleShortGoal = onToggleShortGoal,
            )
        }

        item {
            SectionCard(title = "关于目标层级") {
                InlineNote("长期目标以年为单位；阶段目标通常 1–3 个月；短期目标是可以在一周内完成的动作。")
                Spacer(Modifier.height(8.dp))
                InlineNote("勾选短期目标只是演示交互，本轮不做持久化，重启后会恢复。")
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: DemoData.LongGoal,
    doneShortGoals: Set<String>,
    onToggleShortGoal: (String) -> Unit,
) {
    SectionCard(
        title = goal.title,
        subtitle = goal.horizon,
        trailing = { StatusPill(text = goal.dimension.label) },
    ) {
        Text(
            text = goal.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "整体推进",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(goal.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        ThinProgressBar(progress = goal.progress)

        goal.stages.forEach { stage ->
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stage.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(stage.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            ThinProgressBar(
                progress = stage.progress,
                color = MaterialTheme.colorScheme.secondary,
            )
            stage.shortGoals.forEach { shortGoal ->
                val done = shortGoal in doneShortGoals
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("short_goal_$shortGoal")
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = { onToggleShortGoal(shortGoal) },
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = shortGoal,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}
