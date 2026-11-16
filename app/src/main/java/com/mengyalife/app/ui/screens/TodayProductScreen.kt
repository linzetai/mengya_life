package com.mengyalife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.ui.presentation.InformationBinding
import com.mengyalife.app.ui.presentation.InformationContext
import com.mengyalife.app.ui.presentation.InformationInput
import com.mengyalife.app.ui.presentation.InformationResolver
import com.mengyalife.app.ui.presentation.InformationSubject
import com.mengyalife.app.ui.presentation.InformationGoalRelation
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.goals.PlanTime
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.theme.MorrowSpacing
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.ui.theme.isDarkTheme
import com.mengyalife.app.ui.components.rememberToday
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps
import java.time.LocalDate
import java.time.ZoneId

/** Reusable layout tokens for the Morrow-based Today family. */
internal object TodayPageSpacing {
    val Horizontal = MorrowSpacing.PageHorizontal
    val SectionGap = 20.dp
    val SectionTitleGap = 8.dp
    val RowGap = 6.dp
    val FieldGap = 5.dp
    val SummaryPadding = 14.dp
    val SummaryStackFontScale = 1.25f
    val SummaryStackMinWidth = 380.dp
    val ActionGap = 10.dp
    val CornerRadius = 14.dp
}

/** Semantic Today surfaces inherit the established Diary Morrow palette. */
internal object TodayPageColors {
    val SummarySurface: Color @Composable get() = MorrowTheme.colors.nested
    val ActionSurface: Color @Composable get() = MorrowTheme.colors.selectedNav
    val Divider: Color @Composable get() = MorrowTheme.colors.hairline
    val Accent: Color @Composable get() = MorrowTheme.colors.forest
}

/** Pure, read-only projection of today's real repository state. */
data class TodayHomePresentation(
    val dateLabel: String,
    val goals: List<com.mengyalife.app.data.local.goal.GoalEntity>,
    val activePlans: List<SmallPlanEntity>,
    val selectedPlan: SmallPlanEntity?,
    /** Typed identity projection for the selected real plan, including its current goal relation. */
    val selectedPlanSubject: InformationSubject?,
    val recordedPlanCount: Int?,
    val recentRecords: List<GoalCheckinEntity>,
    val recordTargetGoalId: Long?,
    val needsGoalChoice: Boolean,
    val goalsReady: Boolean,
    val plansReady: Boolean,
    val checkinsReady: Boolean,
    val weeklyRecordCount: Int?,
) {
    val contentReady: Boolean get() = goalsReady && plansReady && checkinsReady &&
        (selectedPlan == null || recordedPlanCount != null)
}

fun buildTodayHomePresentation(
    goals: GoalSnapshot,
    allPlans: List<SmallPlanEntity>,
    weeklyPlanProgress: List<com.mengyalife.app.data.local.plan.PlanProgressEntity>,
    checkins: List<GoalCheckinEntity>,
    today: LocalDate,
    now: Long,
    zone: ZoneId,
    weeklyPlanProgressReady: Boolean = true,
    goalsReady: Boolean = true,
    plansReady: Boolean = true,
    checkinsReady: Boolean = true,
): TodayHomePresentation {
    val flattened = mutableListOf<com.mengyalife.app.data.local.goal.GoalEntity>()
    fun visit(nodes: List<GoalNode>) { nodes.forEach { flattened += it.goal; visit(it.children) } }
    if (goalsReady) visit(goals.roots)

    val activePlans = (if (plansReady) allPlans else emptyList()).filter { plan ->
        plan.status == "ACTIVE" && (plan.timeScope.isNullOrBlank() || PlanTime.isCurrent(plan.timeScope, plan.createdAt, now))
    }.sortedWith(compareByDescending<SmallPlanEntity> { it.updatedAt }.thenByDescending { it.id })
    val plan = activePlans.firstOrNull()
    val planSubject = plan?.takeIf { goalsReady && plansReady }?.let { selected ->
        InformationResolver.resolve(
            InformationInput.Plan(planId = selected.id),
            InformationContext(
                goals = goals,
                checkins = if (checkinsReady) checkins else emptyList(),
                plans = if (plansReady) allPlans else emptyList(),
                currentBinding = InformationBinding(),
                now = now,
                zone = zone,
            ),
        ).subject
    }
    val rootGoals = if (goalsReady) goals.roots.map { it.goal } else emptyList()
    val activeRoots = rootGoals.filter { it.status == GoalStatus.IN_PROGRESS }
    // Current focus means actively progressing; include unstarted roots only when nothing is active.
    val roots = activeRoots.ifEmpty { rootGoals.filter { it.status == GoalStatus.NOT_STARTED } }
    val linkedGoal = plan?.goalId?.let { id -> flattened.firstOrNull { it.id == id } }
    val linkedGoalCanRecord = linkedGoal?.status == GoalStatus.IN_PROGRESS || linkedGoal?.status == GoalStatus.NOT_STARTED
    val actionGoal = when {
        !goalsReady || !plansReady -> null
        linkedGoalCanRecord -> linkedGoal?.id
        linkedGoal != null -> null // A completed, paused or cancelled association is not a new target.
        plan?.goalId != null -> null // A deleted association is not a new standalone plan.
        roots.size == 1 -> roots.single().id
        else -> null
    }
    val inGoalTree = checkins.asSequence().filter { record -> flattened.any { it.id == record.goalId } }.toList()
    val weekStart = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
    val weeklyRecordCount = if (checkinsReady && goalsReady) {
        inGoalTree.count { it.recordedAt in weekStart..now }
    } else null
    val latest = if (!checkinsReady || !goalsReady) emptyList() else inGoalTree
        .sortedWith(compareByDescending<GoalCheckinEntity> { it.recordedAt }.thenByDescending { it.id })
        .take(2)
    val weekday = today.dayOfWeek.value
    val weekdayLabel = when (weekday) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日"
    }
    return TodayHomePresentation(
        dateLabel = "${today.monthValue}月${today.dayOfMonth}日 $weekdayLabel",
        goals = roots.ifEmpty {
            when {
                linkedGoal != null -> listOf(linkedGoal)
                else -> rootGoals.take(2)
            }
        },
        activePlans = activePlans,
        selectedPlan = plan,
        selectedPlanSubject = planSubject,
        recordedPlanCount = plan?.let { selected ->
            if (weeklyPlanProgressReady) weeklyPlanProgress.count { it.planId == selected.id } else null
        },
        recentRecords = latest,
        recordTargetGoalId = actionGoal,
        needsGoalChoice = goalsReady && plansReady && actionGoal == null && plan?.goalId == null && roots.size > 1,
        goalsReady = goalsReady,
        plansReady = plansReady,
        checkinsReady = checkinsReady,
        weeklyRecordCount = weeklyRecordCount,
    )
}

@Composable
fun TodayProductScreen(
    listState: LazyListState,
    goals: GoalSnapshot,
    plans: List<SmallPlanEntity>,
    weeklyPlanProgress: List<com.mengyalife.app.data.local.plan.PlanProgressEntity>,
    weeklyPlanProgressReady: Boolean,
    plansReady: Boolean,
    goalsReady: Boolean,
    checkinsReady: Boolean,
    checkins: List<GoalCheckinEntity>,
    onOpenGoal: (Long) -> Unit,
    onOpenGoals: () -> Unit,
    onCreateGoal: () -> Unit,
    onRecordGoal: (Long) -> Unit,
    onOpenRecord: (GoalCheckinEntity) -> Unit,
    onTalk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = rememberToday()
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val presentation = remember(goals, plans, plansReady, weeklyPlanProgress, weeklyPlanProgressReady, goalsReady, checkins, checkinsReady, today) {
        buildTodayHomePresentation(goals, plans, weeklyPlanProgress, checkins, today, now, zone,
            weeklyPlanProgressReady, goalsReady, plansReady, checkinsReady)
    }
    val colors = MorrowTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("today_product_screen_${if (isDarkTheme()) "dark" else "light"}"),
        state = listState,
        contentPadding = PaddingValues(horizontal = TodayPageSpacing.Horizontal, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.SectionGap),
    ) {
        item { PageHeader("今天", presentation.dateLabel, presentation.contentReady) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.SectionTitleGap)) {
                SectionTitle("目标")
                when {
                    !presentation.goalsReady -> UnifiedChevronRow(
                        title = "正在读取目标", detail = "稍后即可记录进展", onClick = {}, tag = "today_goal_loading", enabled = false,
                    )
                    presentation.goals.isEmpty() -> UnifiedChevronRow(
                        title = "还没有目标", detail = "创建一个目标并写下名称", onClick = onCreateGoal,
                        tag = "today_goal_empty",
                    )
                    presentation.goals.size == 1 -> {
                        val goal = presentation.goals.single()
                        val parent = goals.roots.flatMap { flattenHomeNode(it) }.firstOrNull { it.goal.id == goal.id }
                            ?.goal?.parentId?.let { parentId -> goals.roots.flatMap { flattenHomeNode(it) }.firstOrNull { it.goal.id == parentId }?.goal?.title }
                        UnifiedChevronRow(
                            title = goal.title,
                            detail = when (goal.status) {
                                GoalStatus.IN_PROGRESS -> parent?.let { "进行中 · 上级目标 $it" } ?: "进行中"
                                GoalStatus.NOT_STARTED -> parent?.let { "尚未开始 · 上级目标 $it" } ?: "尚未开始"
                                GoalStatus.PAUSED -> parent?.let { "已暂缓 · 上级目标 $it" } ?: "已暂缓"
                                GoalStatus.DONE -> parent?.let { "已完成 · 上级目标 $it" } ?: "已完成"
                                GoalStatus.CANCELLED -> "已取消"
                            },
                            onClick = { onOpenGoal(goal.id) }, tag = "today_goal_${goal.id}",
                        )
                    }
                    else -> {
                        presentation.goals.take(2).forEach { goal ->
                            UnifiedChevronRow(goal.title, goalStatusLabel(goal.status), { onOpenGoal(goal.id) }, "today_goal_${goal.id}")
                        }
                        if (presentation.goals.size > 2) TextButtonRow("查看其余 ${presentation.goals.size - 2} 个目标", onOpenGoals)
                    }
                }
            }
        }

        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val stacked = density.fontScale >= TodayPageSpacing.SummaryStackFontScale && maxWidth < TodayPageSpacing.SummaryStackMinWidth
            val plan = presentation.selectedPlan
            val planRelation = presentation.selectedPlanSubject?.let { subject ->
                when (subject.goalRelation) {
                    InformationGoalRelation.INDEPENDENT -> "独立计划"
                    InformationGoalRelation.LINKED -> "关联目标 · ${subject.linkedGoal?.currentName.orEmpty()}"
                    InformationGoalRelation.MISSING -> "原关联目标已不可用"
                    InformationGoalRelation.NOT_APPLICABLE -> null
                }
            }
            SummarySplitBlock(
                stacked = stacked,
                left = {
                    Text("本周小计划", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Text(
                        when {
                            !presentation.plansReady -> "正在读取安排"
                            plan == null -> "本周还没有安排"
                            else -> plan.title
                        },
                        style = MaterialTheme.typography.titleSmall, color = colors.textPrimary,
                    )
                    planRelation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
                    if (presentation.plansReady && presentation.activePlans.size > 1) {
                        Text("另有 ${presentation.activePlans.size - 1} 项安排", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                },
                right = {
                    Text("本周进展", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Text(
                        when {
                            !presentation.plansReady -> "正在读取进展"
                            plan != null && presentation.recordedPlanCount == null -> "记录数待核对"
                            plan != null -> "计划记录 ${presentation.recordedPlanCount} 次"
                            !presentation.checkinsReady -> "正在读取记录"
                            presentation.weeklyRecordCount == 0 -> "暂无进展记录"
                            else -> "已记录 ${presentation.weeklyRecordCount} 条进展"
                        },
                        style = MaterialTheme.typography.titleSmall, color = colors.textPrimary,
                    )
                },
            )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.RowGap)) {
                SectionTitle("最近记录")
                if (!presentation.checkinsReady || !presentation.goalsReady) {
                    Text("正在读取最近记录", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                } else if (presentation.recentRecords.isEmpty()) {
                    Text("还没有最近记录", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                } else {
                    presentation.recentRecords.forEach { record ->
                        val goalName = flattenGoalNodes(goals.roots).firstOrNull { it.id == record.goalId }?.title ?: "目标已不可用"
                        val date = Timestamps.formatDate(record.recordedAt)
                        SimpleRecordRow(
                            title = record.note.ifBlank { "进展记录" },
                            detail = "$date · $goalName" + record.metricValue?.takeIf { it.isFinite() }?.let { value ->
                                " · ${when (record.amountKind) {
                                    ProgressKind.DELTA -> "新增 ${MetricFormat.withUnit(value, record.metricUnit)}"
                                    ProgressKind.SNAPSHOT -> "累计 ${MetricFormat.withUnit(value, record.metricUnit)}"
                                    else -> MetricFormat.withUnit(value, record.metricUnit)
                                }}"
                            }.orEmpty(),
                            onClick = { onOpenRecord(record) },
                            tag = "today_recent_${record.id}",
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.SectionTitleGap)) {
                SectionTitle("今天可以做")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TodayPageSpacing.ActionGap)) {
                    val recordReady = presentation.goalsReady && presentation.plansReady &&
                        (presentation.selectedPlan == null || presentation.recordedPlanCount != null)
                    val recordText = when {
                        !recordReady -> "正在读取目标"
                        presentation.recordTargetGoalId != null -> "记录进展"
                        presentation.needsGoalChoice -> "选择目标"
                        presentation.goals.isEmpty() -> "新建目标"
                        else -> "查看目标"
                    }
                    val recordDescription = when {
                        !recordReady -> "正在读取目标和计划"
                        presentation.recordTargetGoalId != null -> "关联 · ${flattenGoalNodes(goals.roots).firstOrNull { it.id == presentation.recordTargetGoalId }?.title.orEmpty()}"
                        presentation.needsGoalChoice -> "先选择要记录的目标"
                        presentation.goals.isEmpty() -> "创建目标并写下名称"
                        else -> "打开目标列表"
                    }
                    TodayActionRow(
                        label = recordText, description = recordDescription,
                        icon = MorrowIconKind.PENCIL, modifier = Modifier.weight(1f), tag = "today_action_record", enabled = recordReady,
                    ) {
                        when {
                            presentation.recordTargetGoalId != null -> onRecordGoal(presentation.recordTargetGoalId)
                            presentation.needsGoalChoice -> onOpenGoals()
                            presentation.goals.isEmpty() -> onCreateGoal()
                            else -> onOpenGoals()
                        }
                    }
                    TodayActionRow(
                        label = "和 MengYa 聊聊", description = when {
                            presentation.recordTargetGoalId != null -> "关于 · ${flattenGoalNodes(goals.roots).firstOrNull { it.id == presentation.recordTargetGoalId }?.title.orEmpty()}"
                            presentation.goals.isEmpty() -> "聊聊想开始做的事"
                            else -> "聊聊最近的进展"
                        }, icon = MorrowIconKind.SPARKLE,
                        modifier = Modifier.weight(1f), tag = "today_action_talk",
                    ) { onTalk(presentation.recordTargetGoalId?.let { id ->
                        val name = flattenGoalNodes(goals.roots).firstOrNull { it.id == id }?.title ?: "最近的进展"
                        "想聊聊「$name」的进展。"
                    } ?: if (presentation.goals.isEmpty()) "我还没有创建目标，想聊聊如何开始。" else "想聊聊最近的进展。") }
                }
            }
        }
    }
}

@Composable
internal fun PageHeader(title: String, subtitle: String, dataReady: Boolean) {
    Column(
        Modifier.testTag(if (dataReady) "today_product_data_ready" else "today_product_data_loading"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = MorrowType.DisplaySmall, color = MorrowTheme.colors.textPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MorrowTheme.colors.textSecondary)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MorrowType.SectionTitle, color = MorrowTheme.colors.textPrimary)
}

@Composable
internal fun SummarySplitBlock(
    stacked: Boolean,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    if (stacked) {
        Column(
            Modifier.fillMaxWidth().background(TodayPageColors.SummarySurface, RoundedCornerShape(TodayPageSpacing.CornerRadius))
                .padding(TodayPageSpacing.SummaryPadding),
            verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.SectionTitleGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.FieldGap), content = left)
            Box(Modifier.fillMaxWidth().height(1.dp).background(TodayPageColors.Divider))
            Column(verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.FieldGap), content = right)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .background(TodayPageColors.SummarySurface, RoundedCornerShape(TodayPageSpacing.CornerRadius))
                .padding(horizontal = TodayPageSpacing.SummaryPadding, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).defaultMinSize(minHeight = 64.dp), verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.FieldGap), content = left)
            Box(Modifier.width(1.dp).fillMaxHeight().background(TodayPageColors.Divider))
            Column(Modifier.weight(1f).defaultMinSize(minHeight = 64.dp), verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.FieldGap), content = right)
        }
    }
}

@Composable
internal fun UnifiedChevronRow(title: String, detail: String, onClick: () -> Unit, tag: String, enabled: Boolean = true) {
    val colors = MorrowTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clickable(enabled = enabled, onClick = onClick)
            .testTag(tag).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = colors.textTertiary, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
internal fun SimpleRecordRow(title: String, detail: String, onClick: () -> Unit, tag: String) =
    UnifiedChevronRow(title, detail, onClick, tag)

@Composable
private fun TextButtonRow(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick, modifier = Modifier.defaultMinSize(minHeight = MorrowSpacing.MinTouch)) { Text(label) }
}

@Composable
internal fun TodayActionRow(
    label: String,
    description: String,
    icon: MorrowIconKind,
    modifier: Modifier = Modifier,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MorrowTheme.colors
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 80.dp).clickable(enabled = enabled, onClick = onClick).testTag(tag),
        color = if (enabled) TodayPageColors.ActionSurface else colors.nested,
        shape = RoundedCornerShape(TodayPageSpacing.CornerRadius),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(TodayPageSpacing.FieldGap)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MorrowIcon(icon, if (enabled) TodayPageColors.Accent else colors.textDisabled, Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.titleMedium, color = if (enabled) TodayPageColors.Accent else colors.textDisabled)
            }
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (enabled) colors.textPrimary else colors.textDisabled)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

private fun goalStatusLabel(status: GoalStatus): String = when (status) {
    GoalStatus.IN_PROGRESS -> "进行中"
    GoalStatus.NOT_STARTED -> "尚未开始"
    GoalStatus.PAUSED -> "已暂缓"
    GoalStatus.DONE -> "已完成"
    GoalStatus.CANCELLED -> "已取消"
}

private fun flattenHomeNode(node: GoalNode): List<GoalNode> = listOf(node) + node.children.flatMap(::flattenHomeNode)
private fun flattenGoalNodes(nodes: List<GoalNode>): List<com.mengyalife.app.data.local.goal.GoalEntity> =
    nodes.flatMap { flattenHomeNode(it).map(GoalNode::goal) }
