package com.mengyalife.app.ui.advisor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.advisor.RecordProposalFormat
import com.mengyalife.app.data.advisor.StoredRecordProposal
import com.mengyalife.app.domain.advisor.GoalChangeKind
import com.mengyalife.app.domain.advisor.RecordAmountKind
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.advisor.RecordProposalRules
import com.mengyalife.app.ui.components.MorrowChoicePill
import com.mengyalife.app.ui.components.MorrowSecondaryButton
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.components.MorrowTextField
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType

/**
 * 对话里的记录候选卡片。
 *
 * 只负责展示/编辑候选，不直接写数据库；确认与撤销通过 AdvisorAction 交给 ViewModel，
 * 再由 ProgressRecordApplyService 走领域接口写入。
 */
@Composable
fun RecordProposalCard(
    proposal: StoredRecordProposal,
    actionState: RecordActionUiState?,
    onConfirm: (RecordProposalDraft) -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onUpdateDraft: (RecordProposalDraft) -> Unit,
    onOpenDiary: (String) -> Unit,
    onOpenGoal: (String) -> Unit,
) {
    val colors = MorrowTheme.colors
    var editing by remember(proposal.id) { mutableStateOf(false) }
    var expanded by remember(proposal.id) { mutableStateOf(false) }
    val draft = proposal.draft
    val pending = draft.pendingFields

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("record_proposal_${proposal.id}"),
        shape = RoundedCornerShape(14.dp),
        color = colors.nested,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            val compact = !proposal.isDraft && !expanded
            if (compact) {
                CompactOutcomeRow(
                    proposal = proposal,
                    onExpand = { expanded = true },
                )
            } else {
            if (draft.isDiary) {
                DiaryCardContent(
                    proposal = proposal,
                    draft = draft,
                    actionState = actionState,
                    onEdit = { editing = true },
                    onConfirm = { onConfirm(draft) },
                    onCancel = onCancel,
                    onOpenDiary = { onOpenDiary(proposal.id) },
                )
                if (editing) {
                    DiaryEditorDialog(
                        draft = draft,
                        onDismiss = { editing = false },
                        onSave = { updated ->
                            editing = false
                            if (proposal.isApplied) {
                                onConfirm(updated)
                            } else {
                                // 草稿编辑只更新草稿，是否保存到日记仍由「保存到日记」决定。
                                onUpdateDraft(updated)
                            }
                        },
                    )
                }
            } else if (draft.isPlanProgress) {
                PlanProgressCardContent(
                    proposal = proposal,
                    draft = draft,
                    actionState = actionState,
                    onConfirm = { onConfirm(draft) },
                    onCancel = onCancel,
                )
            } else if (draft.isPlan) {
                PlanCardContent(
                    proposal = proposal,
                    draft = draft,
                    actionState = actionState,
                    onConfirm = { onConfirm(draft) },
                    onEdit = { editing = true },
                    onCancel = onCancel,
                )
                if (editing) {
                    PlanEditorDialog(
                        draft = draft,
                        onDismiss = { editing = false },
                        onSave = { updated -> editing = false; onUpdateDraft(updated) },
                    )
                }
            } else if (draft.isGoal) {
                GoalCardContent(
                    proposal = proposal,
                    draft = draft,
                    actionState = actionState,
                    onEdit = { editing = true },
                    onConfirm = { onConfirm(draft) },
                    onCancel = onCancel,
                    onOpenGoal = { onOpenGoal(proposal.id) },
                    onToggleBreakdown = { index ->
                        val change = draft.goalChange
                        if (change != null) {
                            val updated = change.breakdown.mapIndexed { i, item ->
                                if (i == index) item.copy(selected = !item.selected) else item
                            }
                            onUpdateDraft(draft.copy(goalChange = change.copy(breakdown = updated)))
                        }
                    },
                )
                if (editing) {
                    GoalDraftEditorDialog(
                        draft = draft,
                        onDismiss = { editing = false },
                        onSave = { updated ->
                            editing = false
                            onUpdateDraft(updated)
                        },
                    )
                }
            } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "进展",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(text = RecordProposalFormat.statusLabel(proposal.status))
            }
            Spacer(Modifier.height(6.dp))

            // 关联目标：优先一行说清楚；歧义时直接给候选。
            Text(
                text = "目标：" + (draft.goalTitle?.takeIf { it.isNotBlank() }
                    ?: if ("goal" in pending) "待确认" else "未关联"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (proposal.isDraft && "goal" in pending && draft.goalCandidates.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                draft.goalCandidates.forEach { candidate ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MorrowSecondaryButton(
                            text = "记到「${candidate.title}」",
                            onClick = {
                                onUpdateDraft(
                                    draft.copy(
                                        goalId = candidate.goalId,
                                        goalTitle = candidate.title,
                                        pendingFields = pending - "goal",
                                    ),
                                )
                            },
                            testTag = "record_pick_goal_${proposal.id}_${candidate.goalId}",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            // 本次进展：正文只出现一次。
            Text(
                text = draft.note,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordProposalRules.amountLine(draft)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.forest,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                draft.occurredAt?.let {
                    Text(
                        text = com.mengyalife.app.util.Timestamps.formatDate(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }

            if (proposal.isDraft && pending.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "待补充：" + pending.joinToString("、") { RecordProposalFormat.pendingLabel(it) },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }

            // 来源原话：默认折叠，避免和“本次进展”重复；修改后仍可追溯。
            val source = draft.originalText.takeIf { it.isNotBlank() && it != draft.note }
            if (source != null) {
                var showSource by remember(proposal.id) { mutableStateOf(false) }
                TextButton(
                    onClick = { showSource = !showSource },
                    modifier = Modifier.testTag("record_source_toggle_${proposal.id}"),
                ) {
                    Text(
                        text = if (showSource) "收起来源原话" else "来源原话",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
                if (showSource) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }

            actionState?.message?.takeIf { actionState.proposalId == proposal.id }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.forest)
            }
            actionState?.error?.takeIf { actionState.proposalId == proposal.id }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
            }

            Spacer(Modifier.height(4.dp))
            when {
                proposal.isDraft -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        MorrowSecondaryButton(
                            text = if (actionState?.loading == true && actionState.proposalId == proposal.id) {
                                "保存中…"
                            } else {
                                "确认记录"
                            },
                            onClick = { onConfirm(draft) },
                            enabled = pending.isEmpty() && actionState?.loading != true,
                            testTag = "record_confirm_${proposal.id}",
                        )
                    }
                    MorrowTertiaryButton(
                        text = "修改",
                        onClick = { editing = true },
                        enabled = actionState?.loading != true,
                        testTag = "record_edit_${proposal.id}",
                    )
                    MorrowTertiaryButton(
                        text = "取消",
                        onClick = onCancel,
                        enabled = actionState?.loading != true,
                        contentColor = colors.danger,
                        testTag = "record_cancel_${proposal.id}",
                    )
                }
                proposal.isApplied -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "已写入目标进展",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    MorrowTertiaryButton(
                        text = "修改",
                        onClick = { editing = true },
                        enabled = actionState?.loading != true,
                        testTag = "record_edit_applied_${proposal.id}",
                    )
                    MorrowTertiaryButton(
                        text = "撤销",
                        onClick = onUndo,
                        enabled = actionState?.loading != true,
                        contentColor = colors.danger,
                        testTag = "record_undo_${proposal.id}",
                    )
                }
                else -> Unit
            }
            }
            }
        }
    }

    if (editing && !draft.isDiary) {
        RecordEditorDialog(
            draft = draft,
            isApplied = proposal.isApplied,
            onDismiss = { editing = false },
            onSave = { updated ->
                editing = false
                onConfirm(updated)
            },
        )
    }
}


@Composable
private fun PlanProgressCardContent(
    proposal: StoredRecordProposal,
    draft: RecordProposalDraft,
    actionState: RecordActionUiState?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val progress = draft.planProgress ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "计划执行",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        StatusPill(text = RecordProposalFormat.statusLabel(proposal.status))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = progress.planTitle ?: "对应计划",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textTertiary,
    )
    Spacer(Modifier.height(3.dp))
    Text(
        text = progress.note,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    actionState?.message?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.forest)
    }
    actionState?.error?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            MorrowSecondaryButton(
                text = "计入这次执行",
                onClick = onConfirm,
                enabled = actionState?.loading != true,
                testTag = "plan_progress_confirm_${proposal.id}",
            )
        }
        MorrowTertiaryButton(
            text = "放弃",
            onClick = onCancel,
            enabled = actionState?.loading != true,
            contentColor = colors.danger,
            testTag = "plan_progress_cancel_${proposal.id}",
        )
    }
}

@Composable
private fun PlanCardContent(
    proposal: StoredRecordProposal,
    draft: RecordProposalDraft,
    actionState: RecordActionUiState?,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val change = draft.planChange ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (change.change == com.mengyalife.app.domain.advisor.PlanChangeKind.STATUS) "更新小计划" else "小计划",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        StatusPill(text = RecordProposalFormat.statusLabel(proposal.status))
    }
    Spacer(Modifier.height(6.dp))
    when (change.change) {
        com.mengyalife.app.domain.advisor.PlanChangeKind.CREATE -> {
            Text(
                text = change.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = (change.timeScope ?: "时间未定") +
                    (change.goalId?.let { " · 关联目标" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
        else -> {
            Text(
                text = (change.title ?: "这条计划") + " → " + planStatusLabel(change.newStatus),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            change.note?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            }
        }
    }
    actionState?.message?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.forest)
    }
    actionState?.error?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            MorrowSecondaryButton(
                text = if (change.change == com.mengyalife.app.domain.advisor.PlanChangeKind.STATUS) "确认更新" else "记下这个计划",
                onClick = onConfirm,
                enabled = actionState?.loading != true,
                testTag = "plan_confirm_${proposal.id}",
            )
        }
        if (change.change == com.mengyalife.app.domain.advisor.PlanChangeKind.CREATE) {
            MorrowTertiaryButton(
                text = "修改",
                onClick = onEdit,
                enabled = actionState?.loading != true,
                testTag = "plan_edit_${proposal.id}",
            )
        }
        MorrowTertiaryButton(
            text = "放弃",
            onClick = onCancel,
            enabled = actionState?.loading != true,
            contentColor = colors.danger,
            testTag = "plan_cancel_${proposal.id}",
        )
    }
}

private fun planStatusLabel(raw: String?): String = when (raw) {
    "COMPLETED" -> "已完成"
    "PAUSED" -> "已暂停"
    "CANCELLED" -> "已取消"
    else -> "进行中"
}

@Composable
private fun PlanEditorDialog(
    draft: RecordProposalDraft,
    onDismiss: () -> Unit,
    onSave: (RecordProposalDraft) -> Unit,
) {
    val colors = MorrowTheme.colors
    val change = draft.planChange ?: return
    var title by remember(draft) { mutableStateOf(change.title.orEmpty()) }
    var scope by remember(draft) { mutableStateOf(change.timeScope.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = RoundedCornerShape(20.dp),
        title = { Text("修改小计划", style = MorrowType.CardTitle) },
        text = {
            Column {
                MorrowTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "要做什么",
                    singleLine = false,
                    modifier = Modifier.testTag("plan_edit_title"),
                )
                Spacer(Modifier.height(8.dp))
                MorrowTextField(
                    value = scope,
                    onValueChange = { scope = it },
                    placeholder = "时间范围（这周/明天/周末）",
                    singleLine = true,
                    modifier = Modifier.testTag("plan_edit_scope"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) return@TextButton
                    onSave(draft.copy(planChange = change.copy(
                        title = title.trim(),
                        timeScope = scope.trim().takeIf { it.isNotEmpty() },
                    )))
                },
                modifier = Modifier.testTag("plan_edit_save"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun goalLevelLabel(raw: String?): String = when (raw) {
    "LONG_TERM" -> "长期目标"
    "STAGE" -> "阶段目标"
    "ACTION" -> "近期行动"
    else -> "长期目标"
}

private fun goalDomainLabel(raw: String?): String = when (raw) {
    "CAREER" -> "职业"
    "HEALTH" -> "健康"
    "WEALTH" -> "财富"
    "LIFE" -> "生活"
    "LEARNING" -> "学习"
    else -> "生活"
}

@Composable
private fun GoalCardContent(
    proposal: StoredRecordProposal,
    draft: RecordProposalDraft,
    actionState: RecordActionUiState?,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onOpenGoal: () -> Unit,
    onToggleBreakdown: (Int) -> Unit,
) {
    val colors = MorrowTheme.colors
    val change = draft.goalChange ?: return
    val before = change.before
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (change.change) {
                GoalChangeKind.UPDATE -> "调整目标"
                GoalChangeKind.BREAKDOWN -> "目标拆解"
                else -> "新建目标"
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        StatusPill(text = RecordProposalFormat.statusLabel(proposal.status))
    }
    Spacer(Modifier.height(6.dp))

    if (change.change == GoalChangeKind.UPDATE && before != null) {
        Text(
            text = "原：" + before.title + " · " + goalLevelLabel(before.levelName) +
                " · " + goalDomainLabel(before.domainName) +
                (before.dueAt?.let { " · 截止 " + com.mengyalife.app.util.Timestamps.formatDateUtc(it) } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.testTag("goal_before_${proposal.id}"),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "新：" + (change.title ?: before.title) +
                (change.levelName?.let { " · " + goalLevelLabel(it) } ?: "") +
                (change.domainName?.let { " · " + goalDomainLabel(it) } ?: "") +
                (change.dueAt?.let { " · 截止 " + com.mengyalife.app.util.Timestamps.formatDateUtc(it) } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.testTag("goal_after_${proposal.id}"),
        )
    } else {
        Text(
            text = change.title.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = goalLevelLabel(change.levelName) + " · " + goalDomainLabel(change.domainName) +
                (change.dueAt?.let { " · 截止 " + com.mengyalife.app.util.Timestamps.formatDateUtc(it) } ?: " · 期限未定"),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
    change.description?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 3)
    }
    if (change.uncertain.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "还不确定：" + change.uncertain.joinToString("、"),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }

    if (change.breakdown.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (change.change == GoalChangeKind.BREAKDOWN) "选择要创建的阶段" else "可选的拆解（勾选后一起创建）",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(2.dp))
        change.breakdown.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = proposal.isDraft) { onToggleBreakdown(index) }
                    .padding(vertical = 5.dp)
                    .testTag("goal_breakdown_${proposal.id}_$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (item.selected) "✓" else "○",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.selected) colors.forest else colors.textTertiary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = item.title + "（" + goalLevelLabel(item.levelName) + "）",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    actionState?.message?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.forest)
    }
    actionState?.error?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
    }
    Spacer(Modifier.height(6.dp))
    when {
        proposal.isDraft -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MorrowSecondaryButton(
                    text = when (change.change) {
                        GoalChangeKind.UPDATE -> "保存变更"
                        GoalChangeKind.BREAKDOWN -> "创建所选阶段"
                        else -> "创建目标"
                    },
                    onClick = onConfirm,
                    enabled = draft.isComplete && actionState?.loading != true,
                    testTag = "goal_confirm_${proposal.id}",
                )
            }
            MorrowTertiaryButton(
                text = "修改",
                onClick = onEdit,
                enabled = actionState?.loading != true,
                testTag = "goal_edit_${proposal.id}",
            )
            MorrowTertiaryButton(
                text = "放弃",
                onClick = onCancel,
                enabled = actionState?.loading != true,
                contentColor = colors.danger,
                testTag = "goal_cancel_${proposal.id}",
            )
        }
        proposal.isApplied -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MorrowSecondaryButton(
                    text = "查看目标",
                    onClick = onOpenGoal,
                    testTag = "goal_open_${proposal.id}",
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun GoalDraftEditorDialog(
    draft: RecordProposalDraft,
    onDismiss: () -> Unit,
    onSave: (RecordProposalDraft) -> Unit,
) {
    val colors = MorrowTheme.colors
    val change = draft.goalChange ?: return
    var title by remember(draft) { mutableStateOf(change.title ?: change.before?.title.orEmpty()) }
    var description by remember(draft) { mutableStateOf(change.description.orEmpty()) }
    var dueText by remember(draft) {
        mutableStateOf(
            change.dueAt?.let { com.mengyalife.app.util.Timestamps.formatDateUtc(it) }.orEmpty(),
        )
    }
    var error by remember(draft) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = RoundedCornerShape(20.dp),
        title = { Text("修改目标草稿", style = MorrowType.CardTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MorrowTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    placeholder = "目标标题",
                    singleLine = true,
                    modifier = Modifier.testTag("goal_edit_title"),
                )
                Spacer(Modifier.height(8.dp))
                MorrowTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "描述（可选）",
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.testTag("goal_edit_desc"),
                )
                Spacer(Modifier.height(8.dp))
                MorrowTextField(
                    value = dueText,
                    onValueChange = { dueText = it; error = null },
                    placeholder = "截止日期 yyyy-MM-dd（可选）",
                    singleLine = true,
                    modifier = Modifier.testTag("goal_edit_due"),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        error = "目标标题不能为空"
                        return@TextButton
                    }
                    val due = if (dueText.isBlank()) {
                        null
                    } else {
                        runCatching {
                            java.time.LocalDate.parse(dueText.trim())
                                .atStartOfDay(java.time.ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli()
                        }.getOrElse {
                            error = "日期格式应为 yyyy-MM-dd"
                            return@TextButton
                        }
                    }
                    onSave(
                        draft.copy(
                            goalChange = change.copy(
                                title = title.trim(),
                                description = description.trim().takeIf { it.isNotEmpty() },
                                dueAt = due,
                            ),
                        ),
                    )
                },
                modifier = Modifier.testTag("goal_edit_save"),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DiaryCardContent(
    proposal: StoredRecordProposal,
    draft: RecordProposalDraft,
    actionState: RecordActionUiState?,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onOpenDiary: () -> Unit,
) {
    val colors = MorrowTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "日记",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        StatusPill(text = RecordProposalFormat.statusLabel(proposal.status))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "整理自最近 ${draft.sourceMessageIds.size.coerceAtLeast(1)} 条对话",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textTertiary,
    )
    Spacer(Modifier.height(6.dp))
    draft.diaryTitle?.takeIf { it.isNotBlank() }?.let { title ->
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(
        text = draft.diaryBody.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
        maxLines = 8,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("diary_body_${proposal.id}"),
    )
    draft.occurredAt?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            text = com.mengyalife.app.util.Timestamps.formatDate(it),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
    actionState?.message?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.forest)
    }
    actionState?.error?.takeIf { actionState.proposalId == proposal.id }?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
    }
    Spacer(Modifier.height(6.dp))
    when {
        proposal.isDraft -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MorrowSecondaryButton(
                    text = if (actionState?.loading == true && actionState.proposalId == proposal.id) {
                        "保存中…"
                    } else {
                        "保存到日记"
                    },
                    onClick = onConfirm,
                    enabled = actionState?.loading != true,
                    testTag = "diary_save_${proposal.id}",
                )
            }
            MorrowTertiaryButton(
                text = "修改",
                onClick = onEdit,
                enabled = actionState?.loading != true,
                testTag = "diary_edit_${proposal.id}",
            )
            MorrowTertiaryButton(
                text = "取消",
                onClick = onCancel,
                enabled = actionState?.loading != true,
                contentColor = colors.danger,
                testTag = "diary_cancel_${proposal.id}",
            )
        }
        proposal.isApplied -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MorrowSecondaryButton(
                    text = "打开日记",
                    onClick = onOpenDiary,
                    testTag = "diary_open_${proposal.id}",
                )
            }
            MorrowTertiaryButton(
                text = "修改",
                onClick = onEdit,
                enabled = actionState?.loading != true,
                testTag = "diary_edit_applied_${proposal.id}",
            )
        }
        else -> Unit
    }
}

@Composable
private fun DiaryEditorDialog(
    draft: RecordProposalDraft,
    onDismiss: () -> Unit,
    onSave: (RecordProposalDraft) -> Unit,
) {
    val colors = MorrowTheme.colors
    var title by remember(draft) { mutableStateOf(draft.diaryTitle.orEmpty()) }
    var body by remember(draft) { mutableStateOf(draft.diaryBody.orEmpty()) }
    var error by remember(draft) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = RoundedCornerShape(20.dp),
        title = { Text("修改日记草稿", style = MorrowType.CardTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MorrowTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "标题（可选）",
                    singleLine = true,
                    modifier = Modifier.testTag("diary_edit_title"),
                )
                Spacer(Modifier.height(8.dp))
                MorrowTextField(
                    value = body,
                    onValueChange = { body = it; error = null },
                    placeholder = "日记正文",
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.testTag("diary_edit_body"),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (body.isBlank()) {
                        error = "日记正文不能为空"
                    } else {
                        onSave(draft.copy(diaryTitle = title.trim().takeIf { it.isNotEmpty() }, diaryBody = body.trim()))
                    }
                },
                modifier = Modifier.testTag("diary_edit_save"),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CompactOutcomeRow(
    proposal: StoredRecordProposal,
    onExpand: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val draft = proposal.draft
    val text = when {
        proposal.isCancelled -> when {
            draft.isDiary -> "已放弃这条日记草稿"
            draft.isGoal -> "已放弃这次目标建议"
            else -> "已放弃这条记录建议"
        }
        proposal.isUndone -> "已撤销这条进展记录"
        draft.isDiary -> "✓ 已保存到日记" + (draft.diaryTitle?.let { "：$it" } ?: "")
        draft.isPlan -> "✓ 已记下小计划" + (draft.planChange?.title?.let { "：$it" } ?: "")
        draft.isPlanProgress -> "✓ 已记录一次执行" + (draft.planProgress?.planTitle?.let { "：$it" } ?: "")
        draft.isGoal -> {
            val change = draft.goalChange
            val verb = if (change?.change == com.mengyalife.app.domain.advisor.GoalChangeKind.UPDATE) {
                "✓ 已更新目标"
            } else {
                "✓ 已创建目标"
            }
            verb + (change?.title?.let { "：$it" } ?: "")
        }
        else -> "✓ 已记录进展" + (draft.note.take(24).takeIf { it.isNotBlank() }?.let { "：$it" } ?: "")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compact_proposal_${proposal.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (proposal.isCancelled) colors.textTertiary else colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onExpand,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                text = "查看",
                style = MaterialTheme.typography.labelSmall,
                color = colors.forest,
            )
        }
    }
}

@Composable
private fun RecordEditorDialog(
    draft: RecordProposalDraft,
    isApplied: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecordProposalDraft) -> Unit,
) {
    val colors = MorrowTheme.colors
    var note by remember(draft) { mutableStateOf(draft.note) }
    var valueText by remember(draft) {
        mutableStateOf(
            draft.metricValue?.let { value ->
                if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            }.orEmpty(),
        )
    }
    var unit by remember(draft) { mutableStateOf(draft.metricUnit.orEmpty()) }
    var amountKind by remember(draft) { mutableStateOf(draft.amountKind) }
    var error by remember(draft) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (isApplied) "修改已记录的进展" else "修改这条记录",
                style = MorrowType.CardTitle,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                draft.goalTitle?.takeIf { it.isNotBlank() }?.let {
                    Text("目标：$it", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                }
                Text("记录说明", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Spacer(Modifier.height(4.dp))
                MorrowTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "例如：今天又存了 2000 元",
                    minLines = 2,
                    maxLines = 4,
                    testTag = "record_edit_note",
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "记录类型",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorrowChoicePill(
                        text = "增量",
                        selected = amountKind == RecordAmountKind.DELTA,
                        onClick = { amountKind = RecordAmountKind.DELTA },
                        modifier = Modifier.testTag("record_kind_delta"),
                    )
                    MorrowChoicePill(
                        text = "累计快照",
                        selected = amountKind == RecordAmountKind.SNAPSHOT,
                        onClick = { amountKind = RecordAmountKind.SNAPSHOT },
                        modifier = Modifier.testTag("record_kind_snapshot"),
                    )
                    MorrowChoicePill(
                        text = "无数值",
                        selected = amountKind == RecordAmountKind.NONE,
                        onClick = { amountKind = RecordAmountKind.NONE },
                        modifier = Modifier.testTag("record_kind_none"),
                    )
                }
                if (amountKind != RecordAmountKind.NONE) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "数值",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(4.dp))
                            MorrowTextField(
                                value = valueText,
                                onValueChange = { valueText = it },
                                placeholder = "2000",
                                singleLine = true,
                                maxLines = 1,
                                testTag = "record_edit_value",
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "单位",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(4.dp))
                            MorrowTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                placeholder = "元 / 小时",
                                singleLine = true,
                                maxLines = 1,
                                testTag = "record_edit_unit",
                            )
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
                if (isApplied) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "已记录的进展不能改目标；需要换目标请先撤销。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = when (amountKind) {
                        RecordAmountKind.NONE -> null
                        else -> valueText.trim().toDoubleOrNull()
                    }
                    if (note.isBlank()) {
                        error = "记录说明不能为空"
                        return@TextButton
                    }
                    if (amountKind != RecordAmountKind.NONE && (value == null || !value.isFinite())) {
                        error = "请填写有效的数值（只支持有限数字）"
                        return@TextButton
                    }
                    val normalized = value?.takeIf { amountKind != RecordAmountKind.NONE }
                    onSave(
                        draft.copy(
                            note = note.trim(),
                            amountKind = amountKind,
                            metricValue = normalized,
                            metricUnit = unit.trim().takeIf { it.isNotEmpty() },
                            pendingFields = draft.pendingFields - "metricValue",
                        ),
                    )
                },
                modifier = Modifier.testTag("record_edit_save"),
            ) { Text("保存", color = colors.forest) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("record_edit_cancel")) {
                Text("取消")
            }
        },
    )
}
