package com.mengyalife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.data.advisor.StoredGoalProposal
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.AdvisorSource
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.domain.advisor.GoalProposalNode
import com.mengyalife.app.domain.advisor.safeEndpointLabel
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.ui.advisor.AdvisorAction
import com.mengyalife.app.ui.advisor.AdvisorUiState
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.util.Timestamps

/**
 * 顾问页（Gate 3B-1）：
 * - 未配置 → 引导配置；
 * - 已配置但未授权 → 首次/重新授权页（不读取个人数据）；
 * - 已授权 → 对话；每轮自动检索授权类别，来源写在回复下方；
 * - 可在对话内生成「目标草案」，草案不写入正式目标。
 */
@Composable
fun AdvisorScreen(
    listState: LazyListState,
    state: AdvisorUiState,
    onAction: (AdvisorAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceDialogFor by remember { mutableStateOf<AdvisorMessage?>(null) }
    var proposalDialogFor by remember { mutableStateOf<StoredGoalProposal?>(null) }

    LaunchedEffect(state.messages.size, state.sending) {
        if (state.messages.isNotEmpty() || state.sending) {
            runCatching { listState.animateScrollToItem((state.messages.size + 3).coerceAtLeast(0)) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "长期成长顾问",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (state.configured) {
                        "模型：${state.config.model} · ${safeEndpointLabel(state.config.endpoint)}"
                    } else {
                        "尚未配置模型服务"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(text = if (state.configured) "已配置" else "未配置")
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("advisor_open_settings")) {
                Text("模型设置")
            }
        }

        when {
            !state.configured -> NotConfiguredPanel(onOpenSettings)
            !state.consentValid -> ConsentPanel(state = state, onAction = onAction)
            else -> {
                ConsentSummaryRow(state = state, onOpenSettings = onOpenSettings)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            InlineNote("还没有对话。系统会按授权自动检索相关目标、进展或日记，并在回复下方标出来源。")
                        }
                    } else {
                        items(state.messages.size) { index ->
                            val message = state.messages[index]
                            val lastWithSources = message.sources.isNotEmpty() &&
                                state.messages.drop(index + 1).none { it.sources.isNotEmpty() }
                            val lastFailed = message.status != AdvisorMessageStatus.SENT &&
                                state.messages.drop(index + 1).none {
                                    it.status == AdvisorMessageStatus.FAILED ||
                                        it.status == AdvisorMessageStatus.CANCELLED
                                }
                            MessageRow(
                                message = message,
                                lastFailed = lastFailed,
                                lastWithSources = lastWithSources,
                                proposal = state.proposals[message.id],
                                onRetry = { onAction(AdvisorAction.Retry(message.id)) },
                                onOpenSources = { sourceDialogFor = message },
                                onOpenProposal = { proposalDialogFor = it },
                            )
                        }
                    }

                    if (state.sending) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("advisor_waiting"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "正在等待模型回复…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { onAction(AdvisorAction.CancelRequest) },
                                    modifier = Modifier.testTag("advisor_cancel"),
                                ) { Text("取消") }
                            }
                        }
                    }
                    state.errorMessage?.let { error ->
                        item {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("advisor_error"),
                            )
                        }
                    }
                    state.infoMessage?.let { info ->
                        item {
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("advisor_info"),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { onAction(AdvisorAction.RequestProposal) },
                            enabled = !state.sending && state.consentValid,
                            modifier = Modifier.testTag("advisor_make_proposal"),
                        ) { Text("整理为目标草案") }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "草案不会保存为正式目标",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!state.sending && state.input.isBlank()) {
                        Text(
                            text = "问题示例",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DemoData.advisorSuggestions.forEachIndexed { index, suggestion ->
                                SuggestionChip(
                                    onClick = { onAction(AdvisorAction.InputChange(suggestion)) },
                                    label = { Text(suggestion) },
                                    modifier = Modifier.testTag("advisor_suggestion_$index"),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = { onAction(AdvisorAction.InputChange(it)) },
                            modifier = Modifier.weight(1f).testTag("advisor_input"),
                            placeholder = { Text("问点什么…") },
                            maxLines = 4,
                            shape = MaterialTheme.shapes.medium,
                            enabled = !state.sending,
                        )
                        Spacer(Modifier.size(8.dp))
                        if (state.sending) {
                            Button(
                                onClick = { onAction(AdvisorAction.CancelRequest) },
                                modifier = Modifier.testTag("advisor_cancel_button"),
                            ) { Text("取消") }
                        } else {
                            IconButton(
                                onClick = { onAction(AdvisorAction.Send) },
                                enabled = state.canSend,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = if (state.canSend) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .testTag("advisor_send"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (state.canSend) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    sourceDialogFor?.let { message ->
        SourceDialog(message = message, onDismiss = { sourceDialogFor = null })
    }
    proposalDialogFor?.let { proposal ->
        GoalProposalDialog(proposal = proposal, onDismiss = { proposalDialogFor = null })
    }
}

@Composable
private fun NotConfiguredPanel(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp)
            .testTag("advisor_not_configured"),
    ) {
        Text("先配置你自己的模型", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "需要 HTTPS 服务地址、模型名称和 API Key。密钥由 Android Keystore 加密保存在本机；" +
                "请求直接发往你填写的服务，不经过萌芽人生服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.testTag("advisor_configure_button")) {
            Text("去设置")
        }
    }
}

@Composable
private fun ConsentPanel(
    state: AdvisorUiState,
    onAction: (AdvisorAction) -> Unit,
) {
    val recipient = AiConsentRules.normalizeRecipient(state.config.endpoint)
    val needsReconsent = state.consent.granted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .testTag("advisor_consent_panel"),
    ) {
        Text(
            text = if (needsReconsent) "需要重新确认数据接收方" else "首次启用 AI 顾问",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (needsReconsent) {
                "服务地址、授权文案或数据类别发生了变化。请确认新的接收方后继续。"
            } else {
                "在开始对话前，请阅读并确认以下数据使用方式。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        AiConsentRules.consentIntroLines(recipient).forEach { line ->
            Text("• $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("数据接收服务：$recipient", style = MaterialTheme.typography.labelMedium)
        Text(
            "本次将允许：${AiConsentRules.categoriesSummary(AiConsentRules.allCategories)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onAction(AdvisorAction.GrantConsent) },
                modifier = Modifier.weight(1f).testTag("advisor_consent_agree"),
            ) { Text("同意并启用") }
            Button(
                onClick = { onAction(AdvisorAction.DeclineConsent) },
                modifier = Modifier.weight(1f).testTag("advisor_consent_decline"),
            ) { Text("暂不使用") }
        }
        Spacer(Modifier.height(8.dp))
        state.infoMessage?.let { info ->
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("advisor_consent_info"),
            )
            Spacer(Modifier.height(6.dp))
        }
        InlineNote("拒绝不会影响日记、目标和聊天记录的本地保存。")
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ConsentSummaryRow(
    state: AdvisorUiState,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已授权：${AiConsentRules.categoriesSummary(state.consent.categories)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("advisor_consent_summary"),
            )
            Text(
                text = "系统按需检索，回复下方会标注实际来源。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("advisor_consent_manage"),
        ) { Text("管理", color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun MessageRow(
    message: AdvisorMessage,
    lastFailed: Boolean,
    lastWithSources: Boolean,
    proposal: StoredGoalProposal?,
    onRetry: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenProposal: (StoredGoalProposal) -> Unit,
) {
    val isUser = message.role == AdvisorRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (message.status == AdvisorMessageStatus.SENT) {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag(if (isUser) "advisor_user_message" else "advisor_assistant_message"),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(14.dp)
                    .testTag(if (lastFailed) "advisor_retry_last" else "advisor_failed_message"),
            ) {
                Text(
                    text = if (message.status == AdvisorMessageStatus.CANCELLED) {
                        "已取消，没有生成回复"
                    } else {
                        "请求失败，没有生成回复"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.errorMessage ?: "未知错误",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(
                        if (lastFailed) "advisor_retry_button_last" else "advisor_retry_button",
                    ),
                ) { Text("重试") }
            }
        }

        if (message.sources.isNotEmpty()) {
            val goals = message.sources.count { it.type == AdvisorSourceType.GOAL }
            val journals = message.sources.count { it.type == AdvisorSourceType.JOURNAL }
            val progress = message.sources.count { it.type == AdvisorSourceType.PROGRESS }
            val summary = buildList {
                if (goals > 0) add("$goals 个目标")
                if (journals > 0) add("$journals 篇日记")
                if (progress > 0) add("$progress 条进展")
            }.joinToString("、")
            TextButton(
                onClick = onOpenSources,
                modifier = Modifier.testTag(
                    if (lastWithSources) "advisor_sources_last" else "advisor_sources",
                ),
            ) {
                Text("参考了 $summary", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (message.proposalError != null) {
            Text(
                text = message.proposalError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("advisor_proposal_error"),
            )
        }
        if (proposal != null) {
            TextButton(
                onClick = { onOpenProposal(proposal) },
                modifier = Modifier.testTag("advisor_proposal_open"),
            ) { Text("查看目标草案（尚未保存）") }
        }
        Text(
            text = Timestamps.formatDateTime(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun SourceDialog(message: AdvisorMessage, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("advisor_sources_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("本轮参考的数据") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "只展示来源引用，不包含完整正文副本；这些引用不会再次自动发送给其它服务。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                message.sources.groupBy { it.type }.forEach { (type, sources) ->
                    Text(type.label, style = MaterialTheme.typography.labelMedium)
                    sources.forEach { source ->
                        Text(
                            "• #${source.recordId} ${source.label}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("advisor_source_${type.name}_${source.recordId}"),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("advisor_sources_close")) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun GoalProposalDialog(proposal: StoredGoalProposal, onDismiss: () -> Unit) {
    val draft = proposal.draft
    val longTerm = draft.nodes.filter { it.level == GoalLevel.LONG_TERM }

    AlertDialog(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("goal_proposal_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("目标草案（尚未保存）") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(draft.summary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (draft.assumptions.isNotEmpty()) {
                    Text("假设", style = MaterialTheme.typography.labelMedium)
                    draft.assumptions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (draft.risks.isNotEmpty()) {
                    Text("风险", style = MaterialTheme.typography.labelMedium)
                    draft.risks.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(8.dp))
                val rendered = mutableSetOf<String>()
                longTerm.forEach { root ->
                    if (rendered.add(root.tempId)) {
                        ProposalNodeBlock(root, 0)
                        draft.nodes.filter { it.parentTempId == root.tempId }
                            .sortedBy { it.level.ordinal }
                            .forEach { stage ->
                                if (rendered.add(stage.tempId)) {
                                    ProposalNodeBlock(stage, 1)
                                    draft.nodes.filter { it.parentTempId == stage.tempId }
                                        .forEach { action ->
                                            if (rendered.add(action.tempId)) {
                                                ProposalNodeBlock(action, 2)
                                            }
                                        }
                                }
                            }
                    }
                }
                val attachedStages = draft.nodes.filter {
                    it.level == GoalLevel.STAGE && it.parentGoalId != null
                }
                if (attachedStages.isNotEmpty()) {
                    Text("补充到已有长期目标", style = MaterialTheme.typography.labelMedium)
                    attachedStages.forEach { stage ->
                        if (rendered.add(stage.tempId)) {
                            Text(
                                "• [阶段] ${stage.title} → 已有目标 #${stage.parentGoalId}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("goal_proposal_node_${stage.tempId}"),
                            )
                            draft.nodes.filter { it.parentTempId == stage.tempId }
                                .forEach { action ->
                                    if (rendered.add(action.tempId)) {
                                        ProposalNodeBlock(action, 1)
                                    }
                                }
                        }
                    }
                }
                draft.nodes.filter {
                    it.level == GoalLevel.ACTION && it.parentGoalId != null
                }.forEach { action ->
                    if (rendered.add(action.tempId)) {
                        Text(
                            "• [行动] ${action.title} → 已有目标 #${action.parentGoalId}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("goal_proposal_node_${action.tempId}"),
                        )
                    }
                }
                val orphans = draft.nodes.filter { it.tempId !in rendered }
                if (orphans.isNotEmpty()) {
                    Text("未挂接节点", style = MaterialTheme.typography.labelMedium)
                    orphans.forEach { ProposalNodeBlock(it, 0) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "草案仅保存在本机，不会修改正式目标；确认写入留给 Gate 3B-2。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("goal_proposal_close")) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ProposalNodeBlock(node: GoalProposalNode, depth: Int) {
    Text(
        text = ("　".repeat(depth)) + "[${node.level.label}] ${node.title}",
        style = when (node.level) {
            GoalLevel.LONG_TERM -> MaterialTheme.typography.titleSmall
            GoalLevel.STAGE -> MaterialTheme.typography.bodyMedium
            GoalLevel.ACTION -> MaterialTheme.typography.bodySmall
        },
        modifier = Modifier.testTag("goal_proposal_node_${node.tempId}"),
    )
    node.description?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = ("　".repeat(depth + 1)) + it.take(160),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    node.basis?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = ("　".repeat(depth + 1)) + "依据：" + it.take(120),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
