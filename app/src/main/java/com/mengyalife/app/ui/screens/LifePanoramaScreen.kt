package com.mengyalife.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.ui.components.GentleEmptyCard
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowGlyph
import com.mengyalife.app.ui.components.MorrowGlyphKind
import com.mengyalife.app.ui.components.MorrowQuietButton
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.components.ThinProgressBar
import com.mengyalife.app.ui.goals.GoalAction
import com.mengyalife.app.ui.goals.GoalsUiState
import com.mengyalife.app.ui.theme.MorrowSpacing
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.util.Timestamps

/**
 * 二级页面「人生全景」。
 *
 * 只把用户真实的目标按领域分组展示：
 * 首屏是 3–4 个领域的摘要，点开才逐层看到阶段目标与短期行动。
 * 增删改、状态切换、进展记录与历史全部复用原有 GoalsViewModel / Repository。
 */
@Composable
fun LifePanoramaScreen(
    listState: LazyListState,
    state: GoalsUiState,
    onAction: (GoalAction) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    val roots = state.snapshot.roots
    val groups = remember(roots) {
        val orderedDomains = roots.map { it.goal.domain }.distinct()
        roots.groupBy { it.goal.domain }
            .toList()
            .sortedBy { (domain, _) -> orderedDomains.indexOf(domain) }
            .take(4)
    }
    val shownRootIds = remember(groups) { groups.flatMap { g -> g.second.map { it.goal.id } }.toSet() }
    val hiddenCount = roots.count { it.goal.id !in shownRootIds }

    LaunchedEffect(state.focusedGoalId, groups) {
        val id = state.focusedGoalId ?: return@LaunchedEffect
        var index = 1
        var target: Int? = null
        for ((_, nodes) in groups) {
            index += 1 // 领域标题
            val position = nodes.indexOfFirst { it.goal.id == id || it.children.any { c -> c.goal.id == id } }
            if (position >= 0) {
                target = index + position
                break
            }
            index += nodes.size
        }
        if (target != null) {
            runCatching { listState.animateScrollToItem(target) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("life_panorama_screen"),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                MorrowTopBar(
                    label = "人生全景",
                    onSettingsClick = onOpenSettings,
                    onBack = onBack,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "把在意的事情，\n放在更长的时间里看。",
                    style = MorrowType.PageTitle,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "长期方向 → 阶段目标 → 近期行动。它们都保存在这台设备上。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(18.dp))
                if (roots.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${roots.size} 个长期方向",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(colors.textTertiary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.snapshot.lastCheckinAt?.let { "最近进展 " + Timestamps.formatDate(it) }
                                ?: "还没有进展记录",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        if (roots.isEmpty()) {
            item {
                GentleEmptyCard(
                    title = "先聊聊你想要怎样的生活",
                    body = "不必先有一个完整答案。可以从一次简短的探索开始，也可以直接写下一个想去的方向；" +
                        "只有你确认后，它才会进入目标树。",
                    actionLabel = "探索人生方向",
                    onAction = onOpenExplore,
                    secondaryLabel = "直接新增长期目标",
                    onSecondary = { onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null)) },
                    modifier = Modifier.testTag("panorama_empty"),
                )
            }
        } else {
            groups.forEach { (domain, nodes) ->
                item(key = "domain_${domain.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.nested),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = domainGlyph(domain.label),
                                style = MorrowType.Brand.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                color = colors.forest,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = domain.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${nodes.size} 个方向",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
                items(nodes.size, key = { nodes[it].goal.id }) { index ->
                    GoalNodeCard(
                        node = nodes[index],
                        depth = 0,
                        expandedIds = state.expandedIds,
                        onAction = onAction,
                    )
                }
            }

            if (hiddenCount > 0) {
                item {
                    InlineNote("还有 $hiddenCount 个方向没有在这一屏展示；它们仍完整保存在目标树中。")
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorrowQuietButton(
                        text = "新增长期目标",
                        onClick = { onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null)) },
                        modifier = Modifier.weight(1f),
                        testTag = "goal_new_long_term",
                    )
                    Spacer(Modifier.width(10.dp))
                    MorrowQuietButton(
                        text = "探索方向",
                        onClick = onOpenExplore,
                        modifier = Modifier.weight(1f),
                        testTag = "panorama_explore",
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

private fun domainGlyph(label: String): String = when (label) {
    "健康" -> "健"
    "职业" -> "职"
    "财富" -> "财"
    "学习" -> "学"
    else -> "生"
}

@Composable
private fun GoalNodeCard(
    node: GoalNode,
    depth: Int,
    expandedIds: Set<Long>,
    onAction: (GoalAction) -> Unit,
) {
    val colors = MorrowTheme.colors
    val goal = node.goal
    val expanded = goal.id in expandedIds
    val hasChildren = node.children.isNotEmpty()
    val done = node.children.count { it.goal.status == GoalStatus.DONE }
    val progress = if (node.children.isEmpty()) 0f else done.toFloat() / node.children.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (depth == 0) 0.dp else 12.dp)
            .testTag("goal_node_${goal.id}"),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (depth == 0) 17.dp else 14.dp)),
            shape = RoundedCornerShape(if (depth == 0) 17.dp else 14.dp),
            color = if (depth == 0) colors.card else colors.nested.copy(alpha = 0.55f),
            border = if (depth == 0) BorderStroke(1.dp, colors.hairline) else null,
        ) {
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasChildren) {
                        IconButton(
                            onClick = { onAction(GoalAction.ToggleExpand(goal.id)) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("goal_expand_${goal.id}"),
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                                else Icons.Filled.KeyboardArrowRight,
                                contentDescription = if (expanded) "收起" else "展开",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(3.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (hasChildren) {
                                    onAction(GoalAction.ToggleExpand(goal.id))
                                } else {
                                    onAction(GoalAction.OpenCheckin(goal))
                                }
                            },
                    ) {
                        Text(
                            text = goal.title,
                            style = if (depth == 0) MaterialTheme.typography.bodyLarge
                            else MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(goal.status)
                            if (node.checkinCount > 0) {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = "${node.checkinCount} 次进展",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                            goal.dueDate?.let {
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = "截止 " + Timestamps.formatDateUtc(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }

                    GoalActionsMenu(goal = goal, depth = depth, onAction = onAction)
                }

                if (depth == 0) {
                    goal.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (node.children.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        ThinProgressBar(progress = progress, color = colors.accent)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "${done} / ${node.children.size} 个阶段已完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
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
private fun GoalActionsMenu(
    goal: GoalEntity,
    depth: Int,
    onAction: (GoalAction) -> Unit,
) {
    val colors = MorrowTheme.colors
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
                tint = colors.textTertiary,
                modifier = Modifier.size(19.dp),
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
                text = { Text("删除", color = colors.danger) },
                onClick = { menuOpen = false; onAction(GoalAction.RequestDelete(goal)) },
                modifier = Modifier.testTag("goal_delete_${goal.id}"),
            )
        }
    }
}
