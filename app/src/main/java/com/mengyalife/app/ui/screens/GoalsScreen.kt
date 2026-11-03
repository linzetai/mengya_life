package com.mengyalife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.goals.GoalAction
import com.mengyalife.app.ui.goals.GoalsUiState
import com.mengyalife.app.util.Timestamps

/**
 * 目标页：**真实数据**（Room v2）。
 *
 * 结构：长期目标卡片（摘要优先）→ 展开看阶段 → 再展开看短期行动。
 * 默认全部收起，避免第一屏被一个巨大卡片占满。
 */
@Composable
fun GoalsScreen(
    listState: LazyListState,
    state: GoalsUiState,
    onAction: (GoalAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("goals_list"),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                title = "把方向拆成能推进的层级",
                subtitle = "长期目标 → 阶段目标 → 短期行动，全部保存在这台设备上",
                // 目标页是真实数据，不挂「演示数据」标签
                showDemoTag = false,
            )
        }

        item { GoalSummaryCard(state = state) }

        if (state.snapshot.roots.isEmpty()) {
            item {
                SectionCard(
                    title = "还没有目标",
                    subtitle = "从一个十年尺度的方向开始",
                ) {
                    InlineNote("先建一个长期目标，再往下拆阶段和短期行动。这里不会预置任何示例目标。")
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_new_long_term"),
                    ) {
                        Text("新建长期目标")
                    }
                }
            }
        } else {
            items(state.snapshot.roots.size, key = { state.snapshot.roots[it].goal.id }) { index ->
                GoalNodeCard(
                    node = state.snapshot.roots[index],
                    depth = 0,
                    expandedIds = state.expandedIds,
                    onAction = onAction,
                )
            }

            item {
                Button(
                    onClick = { onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_new_long_term"),
                ) {
                    Text("新建长期目标")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun GoalSummaryCard(state: GoalsUiState) {
    val snapshot = state.snapshot
    SectionCard(
        title = "目标概览",
        subtitle = if (snapshot.totalGoals == 0) "还没有任何目标" else "共 ${snapshot.totalGoals} 个目标（含全部层级）",
        modifier = Modifier.testTag("goal_summary"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SummaryMetric("长期目标", "${snapshot.longTermCount}")
            SummaryMetric("阶段目标", "${snapshot.stageCount}")
            SummaryMetric("短期行动", "${snapshot.actionCount}")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SummaryMetric("进行中（全部层级）", "${snapshot.inProgressCount}")
            SummaryMetric("已完成（全部层级）", "${snapshot.doneCount}")
        }
        Spacer(Modifier.height(10.dp))
        InlineNote(
            "「进行中」只统计状态为「进行中」的目标，不包含未开始 / 已暂停 / 已取消。" +
                "长期目标里进行中 ${snapshot.longTermInProgressCount} 个。",
        )
        Spacer(Modifier.height(8.dp))
        InlineNote(
            snapshot.lastCheckinAt?.let { "最近一次进展记录：" + Timestamps.formatDateTime(it) }
                ?: "还没有进展记录",
        )
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 一个目标节点：自身一行 + 展开后的子节点。 */
@Composable
private fun GoalNodeCard(
    node: GoalNode,
    depth: Int,
    expandedIds: Set<Long>,
    onAction: (GoalAction) -> Unit,
) {
    val goal = node.goal
    val expanded = goal.id in expandedIds
    val hasChildren = node.children.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 12).dp)
            .testTag("goal_node_${goal.id}"),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (depth == 0) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasChildren) {
                        IconButton(
                            onClick = { onAction(GoalAction.ToggleExpand(goal.id)) },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("goal_expand_${goal.id}"),
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                                else Icons.Filled.KeyboardArrowRight,
                                contentDescription = if (expanded) "收起" else "展开",
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.title,
                            style = if (depth == 0) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(goal.status)
                            Spacer(Modifier.width(6.dp))
                            if (depth == 0) {
                                StatusPill(text = goal.domain.label)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (node.checkinCount > 0) {
                                Text(
                                    text = "${node.checkinCount} 次进展",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    GoalMenu(goal = goal, depth = depth, onAction = onAction)
                }

                if (depth == 0) {
                    goal.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                goal.dueDate?.let { due ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "截止 " + Timestamps.formatDateUtc(due),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (expanded && hasChildren) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { child ->
                    GoalNodeCard(
                        node = child,
                        depth = depth + 1,
                        expandedIds = expandedIds,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalMenu(
    goal: GoalEntity,
    depth: Int,
    onAction: (GoalAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .size(32.dp)
                .testTag("goal_menu_${goal.id}"),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("记录进展") },
                onClick = { menuOpen = false; onAction(GoalAction.OpenCheckin(goal)) },
                modifier = Modifier.testTag("goal_checkin_${goal.id}"),
            )
            DropdownMenuItem(
                text = { Text("进展历史") },
                onClick = { menuOpen = false; onAction(GoalAction.OpenHistory(goal)) },
                modifier = Modifier.testTag("goal_history_${goal.id}"),
            )
            DropdownMenuItem(
                text = { Text("修改状态") },
                onClick = { menuOpen = false; onAction(GoalAction.RequestStatusChange(goal)) },
                modifier = Modifier.testTag("goal_status_${goal.id}"),
            )
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = { menuOpen = false; onAction(GoalAction.EditGoal(goal)) },
                modifier = Modifier.testTag("goal_edit_${goal.id}"),
            )
            if (depth == 0) {
                DropdownMenuItem(
                    text = { Text("新建阶段目标") },
                    onClick = {
                        menuOpen = false
                        onAction(GoalAction.NewGoal(GoalLevel.STAGE, goal.id, goal.title))
                    },
                    modifier = Modifier.testTag("goal_add_stage_${goal.id}"),
                )
            }
            if (depth == 1) {
                DropdownMenuItem(
                    text = { Text("新建短期行动") },
                    onClick = {
                        menuOpen = false
                        onAction(GoalAction.NewGoal(GoalLevel.ACTION, goal.id, goal.title))
                    },
                    modifier = Modifier.testTag("goal_add_action_${goal.id}"),
                )
            }
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onAction(GoalAction.RequestDelete(goal)) },
                modifier = Modifier.testTag("goal_delete_${goal.id}"),
            )
        }
    }
}

/** 状态胶囊：不同状态不同底色，但保持低饱和。 */
@Composable
fun StatusChip(status: GoalStatus) {
    val (container, content) = statusColors(status)
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

private fun statusColors(status: GoalStatus): Pair<Color, Color> = when (status) {
    GoalStatus.NOT_STARTED -> Color(0xFFE7E9DC) to Color(0xFF45483B)
    GoalStatus.IN_PROGRESS -> Color(0xFFCFE8A9) to Color(0xFF15200A)
    GoalStatus.DONE -> Color(0xFFB5D39C) to Color(0xFF1E3610)
    GoalStatus.PAUSED -> Color(0xFFF3DFC0) to Color(0xFF2C1F0A)
    GoalStatus.CANCELLED -> Color(0xFFF0D9D7) to Color(0xFF410E0B)
}
