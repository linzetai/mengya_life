package com.mengyalife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.repository.DeleteImpact
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.goals.GoalTrends
import com.mengyalife.app.ui.components.MetricTrendChart
import com.mengyalife.app.ui.components.MorrowChoicePill
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.components.morrowTextFieldColors
import com.mengyalife.app.ui.components.TrendPointList
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.ui.goals.CheckinDraftState
import com.mengyalife.app.ui.goals.GoalAction
import com.mengyalife.app.ui.goals.GoalEditorState
import com.mengyalife.app.ui.goals.GoalsUiState
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps

private fun Modifier.dialogSemantics(): Modifier = this.semantics { testTagsAsResourceId = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    editor: GoalEditorState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDomainChange: (GoalDomain) -> Unit,
    onStatusChange: (GoalStatus) -> Unit,
    onDueDateChange: (Long?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var datePickerOpen by remember { mutableStateOf(false) }

    val heading = when {
        editor.isEdit -> "编辑目标"
        editor.level == GoalLevel.LONG_TERM -> "新建长期目标"
        editor.level == GoalLevel.STAGE -> "新建阶段目标"
        else -> "新建短期行动"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MorrowTheme.colors.card,
        scrimColor = MorrowTheme.colors.textPrimary.copy(alpha = 0.22f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .dialogSemantics(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
            Text(heading, style = MorrowType.SheetTitle, color = MorrowTheme.colors.textPrimary)
            editor.parentTitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "上级：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MorrowTheme.colors.textSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))

            GoalOutlinedField(
                value = editor.title,
                onValueChange = onTitleChange,
                label = "标题（必填）",
                modifier = Modifier.testTag("goal_editor_title"),
                singleLine = true,
                enabled = !editor.saving,
            )
            Spacer(Modifier.height(12.dp))

            GoalOutlinedField(
                value = editor.description,
                onValueChange = onDescriptionChange,
                label = "描述（可选）",
                modifier = Modifier.testTag("goal_editor_desc"),
                minLines = 3,
                enabled = !editor.saving,
            )

            Spacer(Modifier.height(14.dp))
            Text("领域", style = MaterialTheme.typography.labelMedium, color = MorrowTheme.colors.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GoalDomain.entries.forEach { domain ->
                    MorrowChoicePill(
                        text = domain.label,
                        selected = editor.domain == domain,
                        onClick = { onDomainChange(domain) },
                        modifier = Modifier.testTag("goal_domain_${domain.name}"),
                        enabled = !editor.saving,
                    )
                }
            }

            if (editor.isEdit) {
                Spacer(Modifier.height(12.dp))
                Text("状态", style = MaterialTheme.typography.labelMedium, color = MorrowTheme.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GoalStatus.entries.forEach { status ->
                        MorrowChoicePill(
                            text = status.label,
                            selected = editor.status == status,
                            onClick = { onStatusChange(status) },
                            modifier = Modifier.testTag("goal_editor_status_${status.name}"),
                            enabled = !editor.saving,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "截止日期",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                MorrowTertiaryButton(
                    text = editor.dueDate?.let { Timestamps.formatDateUtc(it) } ?: "未设置",
                    onClick = { datePickerOpen = true },
                    testTag = "goal_editor_due_date",
                )
                if (editor.dueDate != null) {
                    MorrowTertiaryButton(
                        text = "清除",
                        onClick = { onDueDateChange(null) },
                    )
                }
            }

            if (editor.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = editor.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("goal_editor_error"),
                )
            }

            }
            HorizontalDivider(color = MorrowTheme.colors.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorrowTertiaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    enabled = !editor.saving,
                )
                Spacer(Modifier.size(8.dp))
                Box(modifier = Modifier.width(140.dp)) {
                    MorrowPrimaryButton(
                        text = if (editor.saving) "保存中…" else "保存",
                        onClick = onSave,
                        enabled = !editor.saving,
                        loading = editor.saving,
                        testTag = "goal_editor_save",
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (datePickerOpen) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = editor.dueDate,
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDueDateChange(dateState.selectedDateMillis)
                        datePickerOpen = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun GoalOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    singleLine: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(14.dp),
        minLines = minLines,
        singleLine = singleLine,
        enabled = enabled,
        colors = morrowTextFieldColors(),
    )
}

@Composable
fun StatusPickerDialog(
    goal: GoalEntity,
    onSelect: (GoalStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MorrowTheme.colors.card,
        shape = RoundedCornerShape(20.dp),
        title = { Text("修改状态", style = MorrowType.CardTitle) },
        text = {
            Column(modifier = Modifier.dialogSemantics()) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MorrowTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                GoalStatus.entries.forEach { status ->
                    // 整行可点：只让 RadioButton 可点会导致"点文字没反应"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("status_option_${status.name}")
                            .defaultMinSize(minHeight = 48.dp)
                            .selectable(
                                selected = goal.status == status,
                                role = Role.RadioButton,
                                onClick = { onSelect(status) },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = goal.status == status,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MorrowTheme.colors.forest,
                                unselectedColor = MorrowTheme.colors.textTertiary,
                            ),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(status.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinSheet(
    state: CheckinDraftState,
    onNoteChange: (String) -> Unit,
    onMetricChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MorrowTheme.colors.card,
        scrimColor = MorrowTheme.colors.textPrimary.copy(alpha = 0.22f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .dialogSemantics(),
        ) {
            Text("记录一次进展", style = MorrowType.SheetTitle, color = MorrowTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.goalTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MorrowTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))

            GoalOutlinedField(
                value = state.note,
                onValueChange = onNoteChange,
                label = "这次做了什么（必填）",
                modifier = Modifier.testTag("goal_checkin_note"),
                minLines = 3,
                enabled = !state.saving,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalOutlinedField(
                    value = state.metric,
                    onValueChange = onMetricChange,
                    label = "数值（可选）",
                    modifier = Modifier.weight(1f).testTag("goal_checkin_metric"),
                    singleLine = true,
                    enabled = !state.saving,
                )
                GoalOutlinedField(
                    value = state.unit,
                    onValueChange = onUnitChange,
                    label = "单位（可选）",
                    modifier = Modifier.weight(1f).testTag("goal_checkin_unit"),
                    singleLine = true,
                    enabled = !state.saving,
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("goal_checkin_error"),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorrowTertiaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    enabled = !state.saving,
                )
                Spacer(Modifier.size(8.dp))
                Box(modifier = Modifier.width(140.dp)) {
                    MorrowPrimaryButton(
                        text = if (state.saving) "保存中…" else "记录",
                        onClick = onSave,
                        enabled = !state.saving,
                        loading = state.saving,
                        testTag = "goal_checkin_save",
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinHistorySheet(
    goal: GoalEntity,
    items: List<GoalCheckinEntity>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 只使用用户手填的数值；不同单位分组，非法/非有限值由 GoalTrends 过滤。
    val series = remember(items) {
        GoalTrends.seriesFrom(
            items.map {
                GoalTrends.MetricRecord(
                    checkinId = it.id,
                    recordedAt = it.recordedAt,
                    value = it.metricValue,
                    unit = it.metricUnit,
                )
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MorrowTheme.colors.card,
        scrimColor = MorrowTheme.colors.textPrimary.copy(alpha = 0.22f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 历史里有真实趋势图 + 记录列表，必须给一个明确高度再内部滚动；
                // 否则 ModalBottomSheet 会按「可滚动内容」量成一个很矮的半屏。
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .dialogSemantics(),
        ) {
            Text("进展历史", style = MorrowType.SheetTitle, color = MorrowTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = goal.title,
                style = MaterialTheme.typography.bodySmall,
                color = MorrowTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Text(
                    text = "还没有进展记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MorrowTheme.colors.textSecondary,
                    modifier = Modifier.testTag("goal_history_empty"),
                )
            } else {
                // ---------------- 真实数值趋势 ----------------
                Text(
                    text = "数值趋势",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "数据来源：你在「记录进展」里手动填写的数值（source = manual）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MorrowTheme.colors.textSecondary,
                    modifier = Modifier.testTag("goal_trend_source"),
                )

                if (series.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "还没有数值记录，无法绘制趋势。记录进展时「数值」是选填项，" +
                            "下次可以填一个数字（例如 30、3.5）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MorrowTheme.colors.textSecondary,
                        modifier = Modifier.testTag("goal_history_trend_empty"),
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    series.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        Text(
                            text = "单位：${item.unitLabel} · ${item.points.size} 个数据点",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        MetricTrendChart(
                            series = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("goal_trend_chart_$index"),
                        )
                        Spacer(Modifier.height(6.dp))
                        if (item.points.size == 1) {
                            Text(
                                text = "只有 1 条数值记录，无法判断趋势；这里只显示该数据点，不连成曲线。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MorrowTheme.colors.textSecondary,
                                modifier = Modifier.testTag("goal_trend_single_note"),
                            )
                        } else {
                            Text(
                                text = "横轴是真实记录时间，纵轴是指标值；共 ${item.points.size} 条记录。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MorrowTheme.colors.textSecondary,
                                modifier = Modifier.testTag("goal_trend_multi_note"),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        TrendPointList(series = item)
                    }
                    if (series.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "这个目标存在不同单位的记录，已按单位分开绘制；不做单位换算，" +
                                "也不合并成一个人为评分。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MorrowTheme.colors.textSecondary,
                            modifier = Modifier.testTag("goal_trend_unit_split"),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "进展记录（按时间倒序）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MorrowTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))

                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        text = Timestamps.formatDateTime(item.recordedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MorrowTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.metricValue != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "数值：" + MetricFormat.withUnit(
                                item.metricValue,
                                item.metricUnit,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun DeleteGoalDialog(
    goal: GoalEntity,
    impact: DeleteImpact?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MorrowTheme.colors.card,
        shape = RoundedCornerShape(20.dp),
        title = { Text("删除这个目标？", style = MorrowType.CardTitle) },
        text = {
            Column(modifier = Modifier.dialogSemantics()) {
                Text("「${goal.title}」", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                if (impact == null) {
                    Text(
                        text = "正在计算影响范围…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MorrowTheme.colors.textSecondary,
                    )
                } else {
                    Text(
                        text = buildImpactText(impact),
                        style = MaterialTheme.typography.bodySmall,
                        color = MorrowTheme.colors.textSecondary,
                        modifier = Modifier.testTag("goal_delete_impact"),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "此操作不可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = impact != null,
                modifier = Modifier.testTag("goal_delete_confirm"),
            ) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("goal_delete_cancel")) {
                Text("取消")
            }
        },
    )
}

/** 明确列出会连带删除什么，避免"子目标悄悄消失"。 */
fun buildImpactText(impact: DeleteImpact): String {
    if (impact.goalCount <= 1 && impact.checkinCount == 0) {
        return "这个目标下面没有子目标，也没有进展记录。"
    }
    val parts = buildList {
        if (impact.stageCount > 0) add("${impact.stageCount} 个阶段目标")
        if (impact.actionCount > 0) add("${impact.actionCount} 个短期行动")
        if (impact.checkinCount > 0) add("${impact.checkinCount} 条进展记录")
    }
    return "删除后，还会一并删除：${parts.joinToString("、")}。"
}

/** 目标相关的全部弹层，挂在根组件里，跨页面可用。 */
@Composable
fun GoalOverlays(
    state: GoalsUiState,
    onAction: (GoalAction) -> Unit,
) {
    if (state.editor.open) {
        GoalEditorSheet(
            editor = state.editor,
            onTitleChange = { onAction(GoalAction.TitleChange(it)) },
            onDescriptionChange = { onAction(GoalAction.DescriptionChange(it)) },
            onDomainChange = { onAction(GoalAction.DomainChange(it)) },
            onStatusChange = { onAction(GoalAction.EditorStatusChange(it)) },
            onDueDateChange = { onAction(GoalAction.DueDateChange(it)) },
            onSave = { onAction(GoalAction.SaveEditor) },
            onDismiss = { onAction(GoalAction.CloseEditor) },
        )
    }

    state.statusPicker.target?.let { goal ->
        StatusPickerDialog(
            goal = goal,
            onSelect = { onAction(GoalAction.SetStatus(goal, it)) },
            onDismiss = { onAction(GoalAction.DismissStatusPicker) },
        )
    }

    if (state.checkin.open) {
        CheckinSheet(
            state = state.checkin,
            onNoteChange = { onAction(GoalAction.NoteChange(it)) },
            onMetricChange = { onAction(GoalAction.MetricChange(it)) },
            onUnitChange = { onAction(GoalAction.UnitChange(it)) },
            onSave = { onAction(GoalAction.SaveCheckin) },
            onDismiss = { onAction(GoalAction.CloseCheckin) },
        )
    }

    state.historyOpenFor?.let { goal ->
        CheckinHistorySheet(
            goal = goal,
            items = state.history,
            onDismiss = { onAction(GoalAction.CloseHistory) },
        )
    }

    state.delete.target?.let { goal ->
        DeleteGoalDialog(
            goal = goal,
            impact = state.delete.impact,
            onConfirm = { onAction(GoalAction.ConfirmDelete) },
            onDismiss = { onAction(GoalAction.DismissDelete) },
        )
    }
}
