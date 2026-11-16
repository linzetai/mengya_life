package com.mengyalife.app.ui.candidates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.ui.presentation.*
import com.mengyalife.app.util.Timestamps

private fun candidateGoalMap(snapshot: CandidateSnapshot) = buildMap {
    fun visit(nodes: List<GoalNode>) { nodes.forEach { put(it.goal.id, it.goal); visit(it.children) } }
    visit(snapshot.context.goals.roots)
}

@Composable
fun CandidatePlanDialog(
    plan: SmallPlanEntity,
    snapshot: CandidateSnapshot,
    progressCount: Int?,
    onDismiss: () -> Unit,
    onOpenGoal: (Long) -> Unit,
) {
    val goals = candidateGoalMap(snapshot)
    val goal = goals[plan.goalId]
    val p = LocalProductColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        modifier = Modifier.testTag("candidate_plan_detail"),
        title = { Text(plan.title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (plan.status) { "ACTIVE" -> "进行中"; "COMPLETED" -> "已完成"; "PAUSED" -> "已暂缓"; "CANCELLED" -> "已取消"; else -> "状态待核对" }, color = p.secondary)
                Text("周期：${plan.timeScope ?: "未安排时间"}")
                Text(if (plan.goalId == null) "独立计划" else goal?.let { "关联目标：${it.title}" } ?: "原关联目标已不可用")
                goal?.parentId?.let { goals[it] }?.let { Text("上级目标：${it.title}", color = p.secondary) }
                Text(when (progressCount) { null -> "执行记录数正在核对"; 0 -> "暂无执行记录；未记录不代表没有执行。"; else -> "已记录 $progressCount 次执行。" }, color = p.secondary)
                goal?.let { current -> TextButton(onClick = { onDismiss(); onOpenGoal(current.id) }, modifier = Modifier.fillMaxWidth()) { Text("打开「${current.title}」") } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } },
    )
}

@Composable
fun CandidateSubjectsDialog(subjects: List<InformationSubject>, onDismiss: () -> Unit, onOpenGoal: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalProductColors.current.surface,
        title = { Text("涉及的目标", style = MaterialTheme.typography.titleLarge) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            subjects.forEach { subject ->
                val goal = subject.linkedGoal?.id ?: subject.stableId
                TextButton(enabled = goal != null && subject.currentName != null, onClick = { goal?.let { onDismiss(); onOpenGoal(it) } }) {
                    Text(subject.currentName ?: "原目标已不可用")
                }
                subject.parentGoal?.currentName?.let { Text("上级目标：$it", style = MaterialTheme.typography.bodySmall) }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } },
    )
}

@Composable
fun CandidateEvidenceDialog(info: InformationPresentation, snapshot: CandidateSnapshot, onDismiss: () -> Unit, onOpenGoal: (Long) -> Unit) {
    val goals = candidateGoalMap(snapshot)
    val p = LocalProductColors.current
    val mayShow = info.state !in setOf(InformationState.AUTHORIZATION_CHANGED, InformationState.RESTORE_CHANGED, InformationState.MISSING_CONTEXT)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        modifier = Modifier.testTag("candidate_evidence"),
        title = { Text(info.subject.displayName, style = MaterialTheme.typography.titleLarge) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!mayShow) Text("当前依据需要重新核对，未沿用旧的授权或恢复状态。", color = p.warning)
            else {
                info.observation.distinctBy { it.factId to it.text }.forEach { fact ->
                    fact.period?.let { Text("${Timestamps.formatDate(it.startAt)} — ${Timestamps.formatDate(it.endAt)}", style = MaterialTheme.typography.bodySmall, color = p.secondary) }
                    Text(fact.originalText ?: fact.text, style = MaterialTheme.typography.bodyMedium)
                }
                Text("原始记录", style = MaterialTheme.typography.titleSmall)
                info.evidence.sourceRefs.distinctBy { it.type to it.recordId }.forEach { source ->
                    val goalId = when (source.type.uppercase()) {
                        "GOAL" -> source.recordId.toLongOrNull()
                        "CHECKIN", "PROGRESS", "GOAL_CHECKIN" -> snapshot.context.checkins.firstOrNull { it.id.toString() == source.recordId }?.goalId
                        else -> null
                    }
                    val goal = goals[goalId]
                    val label = when (source.type.uppercase()) { "GOAL" -> "目标"; "CHECKIN", "PROGRESS", "GOAL_CHECKIN" -> "进展记录"; "JOURNAL", "DIARY" -> "日记"; else -> "关联记录" }
                    if (goal != null) TextButton(onClick = { onDismiss(); onOpenGoal(goal.id) }) { Text("$label · ${goal.title}") }
                    else Text("$label${if (goalId != null) "已不可用" else "待核对"}", color = p.secondary)
                }
                snapshot.report?.takeIf { it.id == info.evidence.reportReference?.reportId }?.let {
                    Text("观察生成于 ${Timestamps.formatDateTime(it.generatedAt)}", style = MaterialTheme.typography.bodySmall, color = p.secondary)
                }
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } },
    )
}
