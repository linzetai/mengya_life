package com.mengyalife.app.ui.advisor

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.advisor.StoredGoalProposal
import com.mengyalife.app.domain.advisor.GoalProposalDraft
import com.mengyalife.app.domain.advisor.GoalProposalNode
import com.mengyalife.app.domain.goals.GoalLevel
import java.time.LocalDate

/**
 * Gate 3B-2 草案审核：全屏对话框 + LazyColumn，而不是一个超长 AlertDialog。
 *
 * DRAFT: 可编辑/勾选/确认创建；APPLIED/UNDONE: 只读结果，可撤销。
 */
@Composable
fun GoalProposalReviewDialog(
    proposal: StoredGoalProposal,
    applyState: ProposalApplyUiState?,
    onDismiss: () -> Unit,
    onUpdate: (GoalProposalDraft, Set<String>) -> Unit,
    onApply: (GoalProposalDraft, Set<String>) -> Unit,
    onUndo: () -> Unit,
) {
    var draft by remember(proposal.id) { mutableStateOf(proposal.draft) }
    var selected by remember(proposal.id) {
        mutableStateOf(
            proposal.selectedTempIds.ifEmpty { proposal.draft.nodes.map { it.tempId }.toSet() },
        )
    }
    var editingNode by remember { mutableStateOf<GoalProposalNode?>(null) }
    var undoConfirm by remember { mutableStateOf(false) }
    val editable = proposal.isDraft
    val flattened = remember(draft) { flatten(draft) }

    fun persist(newDraft: GoalProposalDraft, newSelected: Set<String>) {
        draft = newDraft
        selected = newSelected
        onUpdate(newDraft, newSelected)
    }

    fun toggle(node: GoalProposalNode, checked: Boolean) {
        val newSelected = selected.toMutableSet()
        if (checked) {
            var cursor: GoalProposalNode? = node
            var guard = 0
            while (cursor != null && guard < 20) {
                newSelected.add(cursor.tempId)
                cursor = cursor.parentTempId?.let { id -> draft.nodes.firstOrNull { it.tempId == id } }
                guard++
            }
        } else {
            val remove = mutableSetOf(node.tempId)
            var changed = true
            while (changed) {
                changed = false
                draft.nodes.forEach {
                    if (it.parentTempId in remove && remove.add(it.tempId)) changed = true
                }
            }
            newSelected.removeAll(remove)
        }
        persist(draft, newSelected)
    }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag("goal_proposal_dialog"),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                proposal.isApplied -> "目标草案（已创建）"
                                proposal.isUndone -> "目标草案（已撤销）"
                                else -> "目标草案（尚未保存）"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "审核 → 勾选 → 确认创建；草案不会自动修改正式目标",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("goal_proposal_close")) {
                        Text("关闭")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 18.dp, end = 18.dp, bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(draft.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (draft.assumptions.isNotEmpty()) {
                        item {
                            Text("假设", style = MaterialTheme.typography.labelMedium)
                            draft.assumptions.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (draft.risks.isNotEmpty()) {
                        item {
                            Text("风险", style = MaterialTheme.typography.labelMedium)
                            draft.risks.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (applyState?.message != null || applyState?.error != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (applyState.error != null) {
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        },
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(12.dp)
                                    .testTag("goal_proposal_apply_result"),
                            ) {
                                Text(
                                    text = applyState.error ?: applyState.message.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (applyState.error != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                )
                                if (applyState.createdTitles.isNotEmpty()) {
                                    Text(
                                        text = "已创建：" + applyState.createdTitles.joinToString("、"),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text("目标层级", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "新建节点与「补充到已有目标」会分开标注；勾选子节点会自动勾选父节点。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(flattened, key = { it.first.tempId }) { (node, depth) ->
                        val parentSelected = node.parentTempId == null || node.parentTempId in selected
                        ProposalNodeRow(
                            node = node,
                            depth = depth,
                            checked = node.tempId in selected,
                            editable = editable,
                            parentSelected = parentSelected,
                            onCheckedChange = { toggle(node, it) },
                            onEdit = { editingNode = node },
                        )
                    }
                    item {
                        Text(
                            "确认创建会按父→子顺序在同一个 Room 事务中写入；任一步失败整体回滚。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (proposal.isApplied) {
                        Button(
                            onClick = { undoConfirm = true },
                            modifier = Modifier.weight(1f).testTag("goal_proposal_undo"),
                        ) { Text("撤销本次创建") }
                    } else if (proposal.isDraft) {
                        TextButton(onClick = onDismiss, modifier = Modifier.testTag("goal_proposal_cancel")) {
                            Text("取消审核")
                        }
                        Button(
                            onClick = { onApply(draft, selected) },
                            enabled = selected.isNotEmpty() && applyState?.loading != true,
                            modifier = Modifier.weight(1f).testTag("goal_proposal_apply"),
                        ) {
                            Text(if (applyState?.loading == true) "创建中…" else "确认创建 ${selected.size} 个目标")
                        }
                    } else {
                        Text(
                            "本次创建已撤销；如需重新创建，请重新生成草案。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

    editingNode?.let { node ->
        NodeEditDialog(
            node = node,
            onDismiss = { editingNode = null },
            onSave = { updated ->
                val newDraft = draft.copy(
                    nodes = draft.nodes.map { if (it.tempId == updated.tempId) updated else it },
                )
                persist(newDraft, selected)
                editingNode = null
            },
        )
    }

    if (undoConfirm) {
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("goal_proposal_undo_dialog"),
            onDismissRequest = { undoConfirm = false },
            title = { Text("撤销本次创建？") },
            text = {
                Text(
                    "只会删除本次草案创建、且未被修改/产生进展/挂接其他数据的目标；" +
                        "原本存在的目标不会删除。若有风险，系统会阻止并说明原因。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        undoConfirm = false
                        onUndo()
                    },
                    modifier = Modifier.testTag("goal_proposal_undo_confirm"),
                ) { Text("撤销", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { undoConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ProposalNodeRow(
    node: GoalProposalNode,
    depth: Int,
    checked: Boolean,
    editable: Boolean,
    parentSelected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("goal_proposal_node_${node.tempId}"),
        verticalAlignment = Alignment.Top,
    ) {
        if (editable) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = parentSelected,
                modifier = Modifier.testTag("goal_proposal_select_${node.tempId}"),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[${node.level.label}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(node.title, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (node.parentGoalId != null) {
                    "补充到已有目标 #${node.parentGoalId} · 将新建"
                } else {
                    "新建 · ${node.domain.label}" +
                        (node.startDate?.let { " · $it" } ?: "") +
                        (node.dueDate?.let { " → $it" } ?: "")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            node.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            node.basis?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "依据：${it.take(160)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editable && !parentSelected) {
                Text(
                    "需先勾选父节点",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (editable) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.testTag("goal_proposal_edit_${node.tempId}"),
            ) { Text("编辑") }
        }
    }
}

@Composable
private fun NodeEditDialog(
    node: GoalProposalNode,
    onDismiss: () -> Unit,
    onSave: (GoalProposalNode) -> Unit,
) {
    var title by remember { mutableStateOf(node.title) }
    var description by remember { mutableStateOf(node.description.orEmpty()) }
    var startDate by remember { mutableStateOf(node.startDate.orEmpty()) }
    var dueDate by remember { mutableStateOf(node.dueDate.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("goal_proposal_edit_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("编辑草案节点") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_proposal_edit_title"),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth().testTag("goal_proposal_edit_description"),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("开始 yyyy-MM-dd") },
                        modifier = Modifier.weight(1f).testTag("goal_proposal_edit_start"),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("截止 yyyy-MM-dd") },
                        modifier = Modifier.weight(1f).testTag("goal_proposal_edit_due"),
                        singleLine = true,
                    )
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanTitle = title.trim()
                    val err = when {
                        cleanTitle.isEmpty() -> "标题不能为空"
                        cleanTitle.length > 60 -> "标题不能超过 60 字"
                        description.trim().length > 1000 -> "描述不能超过 1000 字"
                        startDate.isNotBlank() && runCatching { LocalDate.parse(startDate.trim()) }.isFailure ->
                            "开始日期必须是 yyyy-MM-dd"
                        dueDate.isNotBlank() && runCatching { LocalDate.parse(dueDate.trim()) }.isFailure ->
                            "截止日期必须是 yyyy-MM-dd"
                        startDate.isNotBlank() && dueDate.isNotBlank() &&
                            dueDate.trim() < startDate.trim() -> "截止日期不能早于开始日期"
                        else -> null
                    }
                    if (err != null) {
                        error = err
                    } else {
                        onSave(
                            node.copy(
                                title = cleanTitle,
                                description = description.trim().takeIf { it.isNotEmpty() },
                                startDate = startDate.trim().takeIf { it.isNotEmpty() },
                                dueDate = dueDate.trim().takeIf { it.isNotEmpty() },
                            ),
                        )
                    }
                },
                modifier = Modifier.testTag("goal_proposal_edit_save"),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("goal_proposal_edit_cancel")) {
                Text("取消")
            }
        },
    )
}

private fun flatten(draft: GoalProposalDraft): List<Pair<GoalProposalNode, Int>> {
    val result = mutableListOf<Pair<GoalProposalNode, Int>>()
    val visited = mutableSetOf<String>()
    fun add(node: GoalProposalNode, depth: Int) {
        if (!visited.add(node.tempId)) return
        result.add(node to depth)
        draft.nodes.filter { it.parentTempId == node.tempId }
            .sortedBy { it.level.ordinal }
            .forEach { add(it, depth + 1) }
    }
    draft.nodes.filter { it.level == GoalLevel.LONG_TERM }.forEach { add(it, 0) }
    draft.nodes.filter { it.parentGoalId != null }
        .sortedBy { it.level.ordinal }
        .forEach { add(it, 1) }
    draft.nodes.filter { it.tempId !in visited }.forEach { add(it, 0) }
    return result
}
