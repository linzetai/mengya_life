package com.mengyalife.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowSecondaryButton
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.components.goalDomainIcon
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.components.StatusChip
import com.mengyalife.app.ui.components.ThinProgressBar
import com.mengyalife.app.ui.goals.GoalAction
import com.mengyalife.app.ui.goals.GoalsUiState
import com.mengyalife.app.ui.theme.MorrowSpacing
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps
import com.mengyalife.app.ui.theme.MorrowType

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
    // 所有领域都可达：默认展开前两个领域，其余收起但可一键展开。
    val groups = remember(roots) {
        val orderedDomains = roots.map { it.goal.domain }.distinct()
        roots.groupBy { it.goal.domain }
            .toList()
            .sortedBy { (domain, _) -> orderedDomains.indexOf(domain) }
    }
    val defaultCollapsed = remember(groups) {
        if (groups.size > 2) groups.drop(2).map { it.first }.toSet() else emptySet()
    }
    var collapsedDomains by rememberSaveable(groups.size) { mutableStateOf(defaultCollapsed) }
    val allCollapsed = groups.isNotEmpty() && groups.all { it.first in collapsedDomains }

    LaunchedEffect(state.focusedGoalId, groups, state.expandedIds) {
        val id = state.focusedGoalId ?: return@LaunchedEffect
        // 从「今天」点进来时，展开目标所在领域与整条父级路径。
        fun findPath(nodes: List<GoalNode>, target: Long, trail: List<Long>): List<Long>? {
            nodes.forEach { node ->
                val next = trail + node.goal.id
                if (node.goal.id == target) return next
                findPath(node.children, target, next)?.let { return it }
            }
            return null
        }
        val path = groups.flatMap { it.second }.let { findPath(it, id, emptyList()) }.orEmpty()
        if (path.isEmpty()) return@LaunchedEffect
        val rootDomain = groups.firstOrNull { g ->
            g.second.any { root -> flattenIds(root).any { it == id } }
        }?.first
        if (rootDomain != null && rootDomain in collapsedDomains) {
            collapsedDomains = collapsedDomains - rootDomain
        }
        path.filter { it !in state.expandedIds }.forEach { onAction(GoalAction.ToggleExpand(it)) }
        val target = groups.indexOfFirst { it.first == rootDomain }
        if (target >= 0) {
            val headerIndex = 1 + groups.take(target).sumOf { (domain, nodes) ->
                1 + if (domain in collapsedDomains) 0 else nodes.size
            }
            runCatching { listState.animateScrollToItem(headerIndex) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
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
                    onSettingsClick = null,
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
            if (groups.size > 1) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (allCollapsed) "全部展开" else "全部收起",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.forest,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    collapsedDomains = if (allCollapsed) {
                                        emptySet()
                                    } else {
                                        groups.map { it.first }.toSet()
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp)
                                .testTag("panorama_toggle_all"),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${groups.size} 个领域",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
            groups.forEach { (domain, nodes) ->
                val expanded = domain !in collapsedDomains
                item(key = "domain_${domain.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                collapsedDomains = if (expanded) {
                                    collapsedDomains + domain
                                } else {
                                    collapsedDomains - domain
                                }
                            }
                            .padding(top = 8.dp, bottom = 2.dp)
                            .testTag("panorama_domain_${domain.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colors.nested),
                            contentAlignment = Alignment.Center,
                        ) {
                            MorrowIcon(
                                kind = goalDomainIcon(domain),
                                tint = colors.forest,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = domain.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = summaryLine(nodes),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.width(6.dp))
                        MorrowIcon(
                            kind = if (expanded) MorrowIconKind.CHEVRON_UP else MorrowIconKind.CHEVRON_DOWN,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (expanded) {
                    items(nodes.size, key = { nodes[it].goal.id }) { index ->
                        GoalNodeCard(
                            node = nodes[index],
                            depth = 0,
                            expandedIds = state.expandedIds,
                            onAction = onAction,
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MorrowSpacing.ButtonGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MorrowPrimaryButton(
                        text = "新增长期目标",
                        onClick = { onAction(GoalAction.NewGoal(GoalLevel.LONG_TERM, null)) },
                        modifier = Modifier.weight(1.25f),
                        testTag = "goal_new_long_term",
                    )
                    MorrowSecondaryButton(
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

        // 全景是独立页面，不挂在主 Scaffold 的 Snackbar 上；这里给保存成功一个就地确认。
        state.message?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2600)
                onAction(GoalAction.ConsumeMessage)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 20.dp, end = 20.dp)
                    .testTag("panorama_message"),
                shape = RoundedCornerShape(12.dp),
                color = colors.accent,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.canvas,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
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
    val radius = RoundedCornerShape(if (depth == 0) 17.dp else 14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (depth == 0) 0.dp else 16.dp)
            .testTag("goal_node_${goal.id}"),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(radius)
                .clickable { onAction(GoalAction.ToggleExpand(goal.id)) },
            shape = radius,
            color = if (depth == 0) colors.card else colors.nested,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 12.dp,
                    top = 13.dp,
                    bottom = 13.dp,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.title,
                            style = if (depth == 0) MaterialTheme.typography.bodyLarge
                            else MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(goal.status)
                            if (node.checkinCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${node.checkinCount} 次进展",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                            goal.dueDate?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "截止 " + Timestamps.formatDateUtc(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("goal_expand_${goal.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        MorrowIcon(
                            kind = if (expanded) MorrowIconKind.CHEVRON_UP else MorrowIconKind.CHEVRON_DOWN,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp),
                            contentDescription = if (expanded) "收起目标" else "展开目标",
                        )
                    }
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
                        Spacer(Modifier.height(11.dp))
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

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            if (node.checkinCount > 0 && node.lastCheckinNote != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "最近进展 · " + Timestamps.formatDate(node.lastCheckinAt ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = if (depth == 0) 0.dp else 16.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(node.lastCheckinNote)
                        if (node.lastCheckinValue != null) {
                            append(" · ")
                            append(MetricFormat.withUnit(node.lastCheckinValue, node.lastCheckinUnit))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = if (depth == 0) 0.dp else 16.dp)
                        .testTag("goal_last_checkin_${goal.id}"),
                )
                Spacer(Modifier.height(6.dp))
            }
            GoalExpandedActions(
                goal = goal,
                depth = depth,
                onAction = onAction,
                modifier = Modifier.padding(start = if (depth == 0) 0.dp else 16.dp),
            )
            if (hasChildren) {
                Spacer(Modifier.height(4.dp))
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
}

@Composable
private fun GoalExpandedActions(
    goal: GoalEntity,
    depth: Int,
    onAction: (GoalAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MorrowTertiaryButton(
            text = "记录进展",
            onClick = { onAction(GoalAction.OpenCheckin(goal)) },
            testTag = "goal_checkin_${goal.id}",
        )
        MorrowTertiaryButton(
            text = "编辑",
            onClick = { onAction(GoalAction.EditGoal(goal)) },
            testTag = "goal_edit_${goal.id}",
        )
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { menuOpen = true }
                    .testTag("goal_menu_${goal.id}"),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(
                    kind = MorrowIconKind.MORE,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                    contentDescription = "更多操作",
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("记录进展") },
                    onClick = { menuOpen = false; onAction(GoalAction.OpenCheckin(goal)) },
                    modifier = Modifier.testTag("goal_checkin_menu_${goal.id}"),
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
                    modifier = Modifier.testTag("goal_edit_menu_${goal.id}"),
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
}

private fun flattenIds(node: GoalNode): List<Long> =
    listOf(node.goal.id) + node.children.flatMap { flattenIds(it) }

private fun summaryLine(nodes: List<GoalNode>): String {
    val total = nodes.sumOf { flattenIds(it).size }
    val done = nodes.sumOf { countDone(it) }
    return if (done > 0) "$total 个 · 已完成 $done" else "$total 个"
}

private fun countDone(node: GoalNode): Int =
    (if (node.goal.status == GoalStatus.DONE) 1 else 0) + node.children.sumOf { countDone(it) }
