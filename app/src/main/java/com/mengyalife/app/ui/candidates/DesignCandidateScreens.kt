package com.mengyalife.app.ui.candidates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengyalife.app.data.local.goal.GoalCheckinEntity
import com.mengyalife.app.data.local.goal.GoalEntity
import com.mengyalife.app.data.local.plan.SmallPlanEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.ui.presentation.InformationGoalRelation
import com.mengyalife.app.ui.presentation.InformationNextAction
import com.mengyalife.app.ui.presentation.InformationPresentation
import com.mengyalife.app.ui.presentation.InformationState
import com.mengyalife.app.ui.presentation.InformationSubject
import com.mengyalife.app.ui.presentation.InformationSubjectType
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps
import com.mengyalife.app.ui.theme.isDarkTheme

/** Shared by Today, Trajectory and the candidate Advisor. QA identity never claims model quality. */
@Composable
fun CandidatePageHeader(title: String, onSettings: () -> Unit, subtitle: String? = null) {
    val colors = LocalProductColors.current
    val mode = LocalUiDesignCandidate.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var reviewMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            CandidateText(title, heading = true, modifier = Modifier.weight(1f).padding(top = 10.dp))
        Box {
            Text(
                when (mode) {
                    UiDesignCandidate.A_FOCUS -> "QA 离线·A ⌄"
                    UiDesignCandidate.B_COMPANION -> "QA 离线·B ⌄"
                    UiDesignCandidate.NONE -> "QA 设计候选"
                },
                modifier = Modifier.clickable { reviewMenu = true }.defaultMinSize(minHeight = 48.dp).padding(vertical = 10.dp),
                color = colors.secondary, style = MaterialTheme.typography.labelSmall,
            )
            androidx.compose.material3.DropdownMenu(expanded = reviewMenu, onDismissRequest = { reviewMenu = false }) {
                listOf("a" to "候选 A · 事项优先", "b" to "候选 B · 对话伴行").forEach { (key, label) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = {
                        reviewMenu = false
                        DesignPreview.handleIntent(context, android.content.Intent().putExtra("qa_ui_candidate", key))
                    })
                }
                listOf("light" to "浅色", "dark" to "深色").forEach { (key, label) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = {
                        reviewMenu = false
                        DesignPreview.handleIntent(context, android.content.Intent().putExtra("qa_ui_theme", key))
                    })
                }
            }
        }
            CandidateLink("设置", onSettings)
        }
        subtitle?.let { CandidateText(it, muted = true, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
fun CandidateTodayScreen(
    snapshot: CandidateSnapshot,
    listState: LazyListState,
    onAction: (InformationNextAction) -> Unit,
    onOpenGoals: () -> Unit,
    onWriteDiary: () -> Unit,
    onTalk: () -> Unit,
    relatedUserContext: String? = null,
    onReevaluate: () -> Unit,
    onOpenEvidence: (InformationPresentation) -> Unit,
    onSettings: () -> Unit,
    preparation: String? = null,
) {
    val companion = LocalUiDesignCandidate.current == UiDesignCandidate.B_COMPANION
    val colors = LocalProductColors.current
    val goals = currentGoals(snapshot)
    val plan = snapshot.plans.firstOrNull()
    val observation = snapshot.observations.firstOrNull {
        it.state == InformationState.VALID && it.suggestion != null
    } ?: snapshot.observations.firstOrNull { it.observation.any { fact -> fact.isCurrent } }
    val careRecord = snapshot.care
    val care = careRecord?.takeIf { it.state == InformationState.VALID }
    val careAttention = careRecord?.takeIf { it.state != InformationState.VALID }
    val conversation = care ?: observation
    val latest = snapshot.context.checkins.sortedWith(
        compareByDescending<GoalCheckinEntity> { it.recordedAt }.thenByDescending { it.id },
    ).firstOrNull()
    val related = plan?.let { selected ->
        snapshot.observations.firstOrNull { item ->
            val selectedIds = subjectGoalIds(selected.subject)
            selectedIds.isNotEmpty() && subjectGoalIds(item.subject) == selectedIds
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(colors.background).testTag("candidate_today_${LocalUiDesignCandidate.current.name.lowercase()}_${if (isDarkTheme()) "dark" else "light"}"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (companion) 22.dp else 18.dp),
    ) {
        item {
            CandidatePageHeader("今天", onSettings, Timestamps.formatDate(snapshot.context.now))
        }
        preparation?.let { message -> item { CandidateText(message, muted = true) } }
        if (!companion) {
            item {
                if (plan != null) {
                    CandidatePanel {
                        CandidateCaption("当前安排")
                        SubjectIdentity(plan.subject)
                        PlanPeriod(snapshot, plan)
                        if (plan.nextAction != InformationNextAction.None) {
                            CandidateLink("查看计划", { onAction(plan.nextAction) }, emphasized = true)
                        } else {
                            CandidateLink("查看相关方向", onOpenGoals)
                        }
                    }
                } else if (care != null) {
                    CandidatePanel {
                        CandidateCaption("想和你聊聊")
                        SubjectIdentity(care.subject)
                        care.observation.filter { it.isCurrent }.forEach { CandidateText(it.text) }
                        care.suggestion?.let {
                            CandidateCaption("AI 建议 · 推测")
                            CandidateText(it.text)
                        }
                        CandidateLink(actionLabel(care.nextAction), { onAction(care.nextAction) }, emphasized = true)
                        CandidateLink("完整记录与来源", { onOpenEvidence(care) })
                    }
                } else {
                    CandidatePanel {
                        CandidateText("今天，先留下你想记住的事。", heading = true)
                        CandidateText("不需要先列好计划。", muted = true)
                        CandidateLink("写一篇日记", onWriteDiary, emphasized = true)
                    }
                }
            }
            care?.let { activeCare ->
                item { CandidateCareNotice(activeCare, onAction) }
            }
            careAttention?.let { needsReview ->
                item { CandidateCareNotice(needsReview, onAction) }
            }
            if (related != null) {
                item {
                    ObservationNote(related, "同一目标 · 最近的观察", relatedUserContext, onAction, onReevaluate, onOpenEvidence, snapshot.context.zone)
                }
            } else if (observation != null) {
                item { ObservationNote(observation, "另外值得看看", relatedUserContext, onAction, onReevaluate, onOpenEvidence, snapshot.context.zone) }
            }
            latest?.takeIf { record -> related?.evidence?.sourceRefs?.none { source ->
                source.type.uppercase() in setOf("CHECKIN", "PROGRESS", "GOAL_CHECKIN") && source.recordId == record.id.toString()
            } != false }?.let { record ->
                item {
                    CandidateCaption("最近留下的进展")
                    RecordContent(record, goals, showSubject = true)
                    CandidateLink("查看这个目标", { onAction(InformationNextAction.OpenGoal(record.goalId)) })
                }
            }
        } else {
            if (plan != null) {
                item { CompactPlanSummary(snapshot, plan, onAction) }
            }
            if (!companion) care?.let { activeCare -> item { CandidateCareNotice(activeCare, onAction) } }
            careAttention?.let { needsReview -> item { CandidateCareNotice(needsReview, onAction) } }
            item {
                // B is a conversational document with a thin rule, not A's action card recolored.
                if (conversation != null) {
                    ObservationNote(conversation, "最近的观察", relatedUserContext, onAction, onReevaluate, onOpenEvidence, snapshot.context.zone)
                } else {
                    CompanionRule {
                        CandidateText("今天想从哪件事聊起？", heading = true)
                        if (latest != null) {
                            CandidateText("你最近为「${goals[latest.goalId]?.title ?: "目标不可用"}」留下了一条进展记录。")
                            RecordContent(latest, goals, showSubject = false)
                        } else {
                            CandidateText("可以聊一件在意的事，也可以只写下一句话。", muted = true)
                        }
                        CandidateLink("聊聊近况", onTalk, emphasized = true)
                    }
                }
            }
            val otherPlans = snapshot.plans.filter { it.subject.stableId != plan?.subject?.stableId }
            if (otherPlans.isNotEmpty()) {
                item { CandidateCaption("其他安排") }
                items(otherPlans, key = { "today_plan_${it.subject.stableId}" }) { item ->
                    val same = conversation?.let { subjectGoalIds(it.subject) }?.let { ids ->
                        ids.isNotEmpty() && ids == subjectGoalIds(item.subject)
                    } == true
                    Column(
                        Modifier.fillMaxWidth().padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (same) CandidateCaption("同一目标")
                        SubjectIdentity(item.subject)
                        PlanPeriod(snapshot, item)
                        if (item.nextAction != InformationNextAction.None) {
                            CandidateLink("查看计划", { onAction(item.nextAction) })
                        }
                    }
                }
            } else if (plan == null) {
                item {
                    CandidateText("还没有确认的小计划。", muted = true)
                    CandidateLink("看看我的方向", onOpenGoals)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                CandidateCaption("留给自己")
                CandidateLink("写日记", onWriteDiary)
                CandidateLink("看看我的方向", onOpenGoals)
                if (!companion) CandidateLink("和 MengYa 聊聊", onTalk)
            }
        }
    }
}

@Composable
fun CandidateTrajectoryScreen(
    snapshot: CandidateSnapshot,
    listState: LazyListState,
    onOpenGoal: (Long) -> Unit,
    onOpenRecord: (GoalCheckinEntity) -> Unit,
    onOpenGoals: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalProductColors.current
    val companion = LocalUiDesignCandidate.current == UiDesignCandidate.B_COMPANION
    val goals = currentGoals(snapshot)
    val records = snapshot.context.checkins.sortedWith(
        compareByDescending<GoalCheckinEntity> { it.recordedAt }.thenByDescending { it.id },
    )
    val groups = records.drop(1).groupBy { it.goalId }.entries.toList()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(colors.background).testTag("candidate_trajectory_${LocalUiDesignCandidate.current.name.lowercase()}_${if (isDarkTheme()) "dark" else "light"}"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (companion) 20.dp else 12.dp),
    ) {
        item {
            CandidatePageHeader("轨迹", onSettings, if (companion) "最近记录与此前进展" else "按记录时间回看")
        }
        if (records.isEmpty()) {
            item {
                if (companion) {
                    CompanionRule {
                        CandidateText("变化还没有写下来。", heading = true)
                        CandidateText("以后每一笔进展，都可以从这里回看。", muted = true)
                        CandidateLink("选一个想记录的方向", onOpenGoals, emphasized = true)
                    }
                } else {
                    CandidatePanel {
                        CandidateText("还没有进展记录", heading = true)
                        CandidateText("找到一个方向，记下这次发生的事。", muted = true)
                        CandidateLink("查看我的方向", onOpenGoals, emphasized = true)
                    }
                }
            }
        } else {
            // Both directions keep actual records chronological; B gives the newest one a compact lead-in.
            if (companion) {
                item {
                    CandidatePanel {
                        CandidateCaption("最近一笔")
                        RecordContent(records.first(), goals, showSubject = true)
                        CandidateLink("打开这条记录", { onOpenRecord(records.first()) })
                    }
                }
                if (records.size > 1) item { CandidateText("更早的记录", heading = true) }
            }
            items(if (companion) records.drop(1) else records, key = { "timeline_${it.id}" }) { record ->
                Column(
                    Modifier.fillMaxWidth().background(colors.border)
                        .padding(start = 2.dp).background(colors.background).padding(start = 14.dp),
                ) {
                    CandidateCaption(Timestamps.formatDateTime(record.recordedAt))
                    Surface(
                        onClick = { onOpenRecord(record) },
                        color = colors.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            .testTag("candidate_record_${record.id}"),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RecordContent(record, goals, showSubject = true, showDate = false)
                            CandidateCaption("查看这条记录 →")
                        }
                    }
                }
            }
        }
        // Confirmed plan state stays separate from recorded progress, without a second hero card.
        if (snapshot.context.plans.isNotEmpty()) {
            item { CandidateText("计划状态", heading = true) }
            items(snapshot.context.plans.sortedByDescending { it.updatedAt }, key = { "status_${it.id}" }) { plan ->
                PlanStateLine(plan, goals, onOpenGoal)
            }
            item {
                QuietExplanation("计划与记录如何区分", "计划状态是已确认的安排；进展页展示每条原记录。未记录不等于未执行，记录数量也不等于完成次数。")
            }
        }
        if (records.isNotEmpty()) {
            item {
                QuietExplanation("数值怎么看", "这里保留每次记录的数值、单位与时间。新增量和累计快照分开理解，不合计不同单位；单条记录不能判断趋势。")
            }
        }
    }
}

@Composable
private fun ObservationNote(
    item: InformationPresentation,
    label: String,
    relatedUserContext: String?,
    onAction: (InformationNextAction) -> Unit,
    onReevaluate: () -> Unit,
    onOpenEvidence: (InformationPresentation) -> Unit,
    zone: java.time.ZoneId,
) {
    CompanionRule {
        CandidateCaption(label)
        SubjectIdentity(item.subject)
        item.observation.filter { it.isCurrent }.forEach { observation ->
            val comparison = observation.comparison
            if (comparison != null) {
                ComparisonReadout(comparison, zone)
            } else {
                CandidateText(observation.text)
                if (observation.originalText == null || observation.originalText == observation.text) {
                    observation.period?.let { period ->
                        CandidateCaption("${Timestamps.formatDate(period.startAt)} — ${Timestamps.formatDate(period.endAt)}")
                    }
                }
            }
        }
        if (!relatedUserContext.isNullOrBlank() && item.state == InformationState.VALID) {
            CandidateCaption("你在相关对话中提到")
            CandidateText(relatedUserContext, muted = true)
        }
        if (item.state == InformationState.VALID) {
            item.suggestion?.let { suggestion ->
                CandidateCaption(if (suggestion.isInference) "AI 建议 · 推测" else "建议")
                CandidateText(suggestion.text)
            }
        } else {
            CandidateText("这条观察需要重新核对，暂不作为当前建议。", muted = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                item.subject.details.isNotEmpty() -> CandidateLink("涉及的目标", { onAction(InformationNextAction.OpenSubjects(item.subject.details)) }, emphasized = true)
                item.state == InformationState.VALID && item.nextAction != InformationNextAction.None ->
                    CandidateLink(actionLabel(item.nextAction), { onAction(item.nextAction) }, emphasized = true)
                item.state != InformationState.VALID -> CandidateLink("重新评估", {
                    if (item.nextAction is InformationNextAction.Reevaluate) onAction(item.nextAction) else onReevaluate()
                }, emphasized = true)
            }
            CandidateLink("原始记录", { onOpenEvidence(item) })
        }
    }
}

@Composable
private fun CompactPlanSummary(snapshot: CandidateSnapshot, plan: InformationPresentation, onAction: (InformationNextAction) -> Unit) {
    val p = LocalProductColors.current
    val entity = snapshot.context.plans.firstOrNull { it.id == plan.subject.stableId }
    Surface(
        color = p.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .testTag("candidate_primary_plan")
            .clickable(enabled = entity != null) { entity?.let { onAction(InformationNextAction.OpenPlan(it.id)) } },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CandidateCaption("已确认 · ${entity?.timeScope ?: "时间待安排"}")
                CandidateText(entity?.title ?: plan.subject.displayName)
                when (plan.subject.goalRelation) {
                    InformationGoalRelation.INDEPENDENT -> CandidateCaption("独立计划")
                    InformationGoalRelation.MISSING -> CandidateCaption("关联目标已不可用")
                    InformationGoalRelation.LINKED -> CandidateCaption("关联目标 · ${plan.subject.linkedGoal?.currentName ?: "目标不可用"}")
                    else -> Unit
                }
            }
            Text("›", color = p.accent, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CandidateCareNotice(info: InformationPresentation, onAction: (InformationNextAction) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CandidateCaption(if (info.state == InformationState.VALID) "想和你聊聊" else "这次关心需要重新核对")
        SubjectIdentity(info.subject)
        if (info.state == InformationState.VALID) {
            info.observation.filter { it.isCurrent }.forEach { CandidateText(it.text) }
            info.suggestion?.let { CandidateText(it.text) }
        } else {
            CandidateText("关联来源暂时无法验证；旧建议先不展示。", muted = true)
        }
        CandidateLink(
            if (info.nextAction is InformationNextAction.Reevaluate) "重新核对这件事" else "聊聊这件事",
            { onAction(info.nextAction) }, emphasized = info.state == InformationState.VALID,
        )
    }
}

@Composable
private fun ComparisonReadout(comparison: com.mengyalife.app.ui.presentation.InformationSnapshotComparison, zone: java.time.ZoneId) {
    val p = LocalProductColors.current
    val first = java.time.Instant.ofEpochMilli(comparison.first.recordedAt).atZone(zone)
    val last = java.time.Instant.ofEpochMilli(comparison.last.recordedAt).atZone(zone)
    val date = java.time.format.DateTimeFormatter.ofPattern(if (first.year == last.year) "M月d日" else "yyyy/M/d")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CandidateCaption("累计记录")
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CandidateCaption(date.format(first))
                Text(MetricFormat.withUnit(comparison.first.value, comparison.first.unit), color = p.text, style = MaterialTheme.typography.titleLarge)
            }
            Text("→", color = p.secondary, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CandidateCaption(date.format(last))
                Text(MetricFormat.withUnit(comparison.last.value, comparison.last.unit), color = p.text, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun SubjectIdentity(subject: InformationSubject) {
    CandidateText(subject.currentName ?: subject.displayName, heading = true)
    when (subject.goalRelation) {
        InformationGoalRelation.INDEPENDENT -> CandidateCaption("独立计划")
        InformationGoalRelation.MISSING -> CandidateCaption("关联目标不可用")
        InformationGoalRelation.LINKED -> CandidateText("关联目标 · ${subject.linkedGoal?.currentName ?: "目标不可用"}", muted = true)
        InformationGoalRelation.NOT_APPLICABLE -> Unit
    }
    subject.parentGoal?.let { CandidateCaption("上级目标 · ${it.currentName ?: "目标不可用"}") }
    // Never silently choose the first goal of a multi-object observation.
    if (subject.details.isNotEmpty()) {
        subject.details.forEach { detail ->
            CandidateText("· ${detail.currentName ?: detail.displayName}", muted = true)
            detail.linkedGoal?.let { CandidateCaption("关联目标 · ${it.currentName ?: "目标不可用"}") }
            detail.parentGoal?.let { CandidateCaption("上级目标 · ${it.currentName ?: "目标不可用"}") }
        }
    }
}

@Composable
private fun PlanPeriod(snapshot: CandidateSnapshot, presentation: InformationPresentation) {
    val plan = snapshot.context.plans.firstOrNull { it.id == presentation.subject.stableId }
    CandidateText(plan?.timeScope?.takeIf { it.isNotBlank() } ?: "时间未定", muted = true)
    if (presentation.state != InformationState.VALID) {
        CandidateCaption("计划信息需要核对")
    }
}

@Composable
private fun GoalIdentity(id: Long, goals: Map<Long, GoalEntity>) {
    val goal = goals[id]
    CandidateText(goal?.title ?: "目标不可用", heading = true)
    goal?.parentId?.let { CandidateCaption("上级目标 · ${goals[it]?.title ?: "目标不可用"}") }
}

@Composable
private fun RecordContent(
    record: GoalCheckinEntity,
    goals: Map<Long, GoalEntity>,
    showSubject: Boolean,
    showDate: Boolean = true,
) {
    if (showSubject) GoalIdentity(record.goalId, goals)
    if (showDate) CandidateCaption(Timestamps.formatDateTime(record.recordedAt))
    val value = record.metricValue?.takeIf { it.isFinite() }
    if (value != null && record.amountKind != ProgressKind.NONE) {
        CandidateText(
            when (record.amountKind) {
                ProgressKind.DELTA -> "本次新增 · ${MetricFormat.withUnit(value, record.metricUnit)}"
                ProgressKind.SNAPSHOT -> "累计快照 · ${MetricFormat.withUnit(value, record.metricUnit)}"
                else -> "记录数值 · ${MetricFormat.withUnit(value, record.metricUnit)}"
            },
            heading = true,
        )
    }
    if (record.note.isNotBlank()) CandidateText(record.note)
    else if (value == null) CandidateText("这条记录没有附文字或数值。", muted = true)
}

@Composable
private fun PlanStateLine(plan: SmallPlanEntity, goals: Map<Long, GoalEntity>, onOpenGoal: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CandidateText(plan.title)
        CandidateCaption(
            when (plan.status) {
                "ACTIVE" -> "进行中"
                "COMPLETED" -> "已完成"
                "PAUSED" -> "已暂停"
                "CANCELLED" -> "已取消"
                else -> "状态待确认"
            } + " · " + (plan.timeScope?.takeIf { it.isNotBlank() } ?: "时间未定"),
        )
        val id = plan.goalId
        when {
            id == null -> CandidateCaption("独立计划")
            goals[id] == null -> CandidateCaption("关联目标不可用")
            else -> {
                CandidateLink("关联目标 · ${goals[id]?.title}", { onOpenGoal(id) })
                goals[id]?.parentId?.let { CandidateCaption("上级目标 · ${goals[it]?.title ?: "目标不可用"}") }
            }
        }
    }
}

@Composable
private fun QuietExplanation(label: String, text: String) {
    var expanded by remember(label, text) { mutableStateOf(false) }
    Column {
        CandidateLink(if (expanded) "收起说明" else label, { expanded = !expanded })
        if (expanded) CandidateText(text, muted = true)
    }
}

@Composable
private fun CandidatePanel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalProductColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun CompanionRule(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalProductColors.current
    // A rule on a document, not another full filled recommendation card.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.accent))
        Column(
            Modifier.weight(1f).padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content,
        )
    }
}

@Composable
private fun CandidateText(text: String, heading: Boolean = false, muted: Boolean = false, modifier: Modifier = Modifier) {
    val colors = LocalProductColors.current
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.SansSerif,
        fontSize = if (heading) 20.sp else 16.sp,
        lineHeight = if (heading) 29.sp else 26.sp,
        fontWeight = if (heading) FontWeight.Medium else FontWeight.Normal,
        color = if (muted) colors.secondary else colors.text,
    )
}

@Composable
private fun CandidateCaption(text: String) {
    Text(text, fontFamily = FontFamily.SansSerif, style = MaterialTheme.typography.labelMedium, color = LocalProductColors.current.secondary)
}

@Composable
private fun CandidateLink(text: String, onClick: () -> Unit, emphasized: Boolean = false) {
    val colors = LocalProductColors.current
    TextButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            .then(if (emphasized) Modifier.background(colors.accentSurface, RoundedCornerShape(10.dp)) else Modifier),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text, color = colors.accent, fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.sp)
    }
}

private fun currentGoals(snapshot: CandidateSnapshot): Map<Long, GoalEntity> {
    fun flatten(nodes: List<GoalNode>): List<GoalEntity> = nodes.flatMap { listOf(it.goal) + flatten(it.children) }
    return flatten(snapshot.context.goals.roots).associateBy { it.id }
}

private fun subjectGoalIds(subject: InformationSubject): Set<Long> =
    if (subject.details.isNotEmpty()) subject.details.flatMap { subjectGoalIds(it) }.toSet()
    else if (subject.type == InformationSubjectType.GOAL) setOfNotNull(subject.stableId)
    else setOfNotNull(subject.linkedGoal?.id)

private fun actionLabel(action: InformationNextAction): String = when (action) {
    is InformationNextAction.OpenGoal -> "查看目标"
    is InformationNextAction.OpenPlan -> "查看计划"
    is InformationNextAction.OpenSubjects -> "查看涉及的对象"
    is InformationNextAction.DiscussInsight -> "讨论建议"
    is InformationNextAction.OpenCareConversation -> "回到这段对话"
    is InformationNextAction.Reevaluate -> "重新评估"
    InformationNextAction.None -> "查看详情"
}
