package com.mengyalife.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.advisor.StoredGoalProposal
import com.mengyalife.app.data.advisor.StoredRecordProposal
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorSourceType
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.RecordProposalDraft
import com.mengyalife.app.domain.advisor.safeEndpointLabel
import com.mengyalife.app.ui.advisor.AdvisorAction
import com.mengyalife.app.ui.advisor.AdvisorUiState
import com.mengyalife.app.ui.advisor.GoalProposalReviewDialog
import com.mengyalife.app.ui.advisor.RecordActionUiState
import com.mengyalife.app.ui.advisor.RecordProposalCard
import com.mengyalife.app.ui.components.GentleEmptyCard
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.components.MorrowIconColors
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowSecondaryButton
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.components.MorrowTextField
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.ui.theme.isDarkTheme
import com.mengyalife.app.util.Timestamps
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private val STARTER_PROMPTS = listOf(
    "我还不知道未来想做什么",
    "最近的变化值得关注吗",
    "帮我想清楚一个决定",
)

/**
 * 顾问页：
 * - 未配置 → 清晰配置入口，绝不伪造回答；
 * - 已配置但未授权 → 首次/重新授权页（不读取个人数据）；
 * - 已授权 → 真实 BYOK 对话；来源、失败重试、取消与草案确认全部保留。
 */
@Composable
fun AdvisorScreen(
    listState: LazyListState,
    state: AdvisorUiState,
    onAction: (AdvisorAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    var sourceDialogFor by remember { mutableStateOf<AdvisorMessage?>(null) }
    var proposalDialogFor by remember { mutableStateOf<StoredGoalProposal?>(null) }
    var statusExpanded by rememberSaveable { mutableStateOf(false) }

    val hasMessages = state.messages.isNotEmpty()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val emptyStateScroll = rememberScrollState()
    var followLatest by remember(listState) {
        mutableStateOf(listState.layoutInfo.totalItemsCount == 0 || !listState.canScrollForward)
    }
    val followLatestState = rememberUpdatedState(followLatest)
    var previousMessageCount by remember(listState) {
        mutableIntStateOf(if (listState.layoutInfo.totalItemsCount == 0) 0 else state.messages.size)
    }

    // 只有用户真正拖动列表时才更新“是否跟随最新”：
    // 自动滚动/键盘改高度/新消息插入都不应把用户强行拉回底部，也不应误判成用户离开底部。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> Unit
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    followLatest = !listState.canScrollForward
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(
        state.messages.size,
        state.sending,
        state.errorMessage,
        state.infoMessage,
        state.records.size,
        imeVisible,
    ) {
        val messagesAdded = state.messages.size > previousMessageCount
        previousMessageCount = state.messages.size
        if (hasMessages && followLatestState.value) {
            if (imeVisible) delay(350)
            val trailingItems =
                (if (state.sending) 1 else 0) +
                    (if (state.errorMessage != null) 1 else 0) +
                    (if (state.infoMessage != null) 1 else 0)
            val lastIndex = (state.messages.size + trailingItems - 1).coerceAtLeast(0)
            if (messagesAdded || state.sending || imeVisible || state.errorMessage != null || state.infoMessage != null ||
                state.records.isNotEmpty()
            ) {
                listState.scrollToItem(lastIndex)
                // 记录卡片在消息之后插入，高度可能超过一屏；等一帧布局后再滚到真正的末尾。
                delay(120)
                repeat(3) {
                    if (!listState.canScrollForward) return@repeat
                    listState.scrollBy(listState.layoutInfo.viewportSize.height.toFloat())
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .testTag("advisor_screen"),
        ) {
            MorrowTopBar(
                label = "顾问",
                onSettingsClick = onOpenSettings,
                horizontalPadding = 22.dp,
            )
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                CompactStatusChip(
                    state = state,
                    onClick = { statusExpanded = !statusExpanded },
                )
                if (statusExpanded) {
                    Spacer(Modifier.height(6.dp))
                    AdvisorStatusCard(
                        state = state,
                        expanded = true,
                        compact = hasMessages,
                        keyboardOpen = imeVisible,
                        onToggle = { statusExpanded = false },
                        onOpenSettings = onOpenSettings,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            when {
                !state.configured -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    NotConfiguredPanel(onOpenSettings)
                }

                !state.consentValid -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ConsentPanel(state = state, onAction = onAction)
                }

                else -> {
                    val hasAssistantReply = state.messages.any {
                        it.role == AdvisorRole.ASSISTANT && it.status == AdvisorMessageStatus.SENT
                    }
                    if (state.messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = if (imeVisible) Alignment.TopCenter else Alignment.Center,
                        ) {
                            EmptyConversation(
                                onPick = { onAction(AdvisorAction.InputChange(it)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp)
                                    .then(
                                        if (imeVisible) {
                                            Modifier.verticalScroll(emptyStateScroll)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 22.dp,
                                end = 22.dp,
                                top = 4.dp,
                                bottom = 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.messages.size) { index ->
                                val message = state.messages[index]
                                val lastWithSources = message.sources.isNotEmpty() &&
                                    state.messages.drop(index + 1).none { it.sources.isNotEmpty() }
                                val lastFailed = message.status != AdvisorMessageStatus.SENT &&
                                    state.messages.drop(index + 1).none {
                                        it.status == AdvisorMessageStatus.FAILED ||
                                            it.status == AdvisorMessageStatus.CANCELLED
                                    }
                                val proposal = state.proposals[message.id]
                                val lastProposal = proposal != null &&
                                    state.messages.drop(index + 1).none { state.proposals[it.id] != null }
                                MessageRow(
                                    message = message,
                                    lastFailed = lastFailed,
                                    lastWithSources = lastWithSources,
                                    lastProposal = lastProposal,
                                    proposal = proposal,
                                    record = state.records[message.id],
                                    recordAction = state.recordAction,
                                    onRetry = { onAction(AdvisorAction.Retry(message.id)) },
                                    onOpenSources = { sourceDialogFor = message },
                                    onOpenProposal = { proposalDialogFor = it },
                                    onConfirmRecord = { stored, draft ->
                                        onAction(AdvisorAction.ConfirmRecord(stored.id, draft))
                                    },
                                    onCancelRecord = { onAction(AdvisorAction.CancelRecord(it)) },
                                    onUndoRecord = { onAction(AdvisorAction.UndoRecord(it)) },
                                    onUpdateRecordDraft = { id, draft ->
                                        onAction(AdvisorAction.UpdateRecordDraft(id, draft))
                                    },
                                    onOpenDiary = { onAction(AdvisorAction.OpenDiary(it)) },
                                    onOpenGoal = { onAction(AdvisorAction.OpenGoal(it)) },
                                )
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
                                            text = "正在等你选定的模型回复…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            onClick = { onAction(AdvisorAction.CancelRequest) },
                                            modifier = Modifier.testTag("advisor_cancel"),
                                        ) { Text("取消", color = colors.forest) }
                                    }
                                }
                            }
                            state.errorMessage?.let { error ->
                                item {
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.danger,
                                        modifier = Modifier.testTag("advisor_error"),
                                    )
                                }
                            }
                            state.infoMessage?.let { info ->
                                item {
                                    Text(
                                        text = info,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                        modifier = Modifier.testTag("advisor_info"),
                                    )
                                }
                            }
                            if (state.planSuggestion) {
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("advisor_plan_suggestion"),
                                        shape = RoundedCornerShape(14.dp),
                                        color = colors.nested,
                                        border = BorderStroke(1.dp, colors.hairline),
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "要把刚才讨论的计划整理成目标吗？",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary,
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(
                                                    onClick = { onAction(AdvisorAction.AcceptPlanSuggestion) },
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Text("生成草案", color = colors.forest)
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                TextButton(
                                                    onClick = { onAction(AdvisorAction.DismissPlanSuggestion) },
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Text("不用了", color = colors.textTertiary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Composer(
                        state = state,
                        onAction = onAction,
                        canPropose = hasAssistantReply,
                        keyboardOpen = imeVisible,
                    )
                }
            }
        }

        sourceDialogFor?.let { message ->
            SourceDialog(message = message, onDismiss = { sourceDialogFor = null })
        }
        proposalDialogFor?.let { opened ->
            val proposal = state.proposals.values.firstOrNull { it.id == opened.id } ?: opened
            GoalProposalReviewDialog(
                proposal = proposal,
                applyState = state.applyState?.takeIf { it.proposalId == proposal.id },
                onDismiss = { proposalDialogFor = null },
                onUpdate = { draft, selected ->
                    onAction(AdvisorAction.UpdateProposalDraft(proposal.id, draft, selected))
                },
                onApply = { draft, selected ->
                    onAction(AdvisorAction.ApplyProposal(proposal.id, draft, selected))
                },
                onUndo = { onAction(AdvisorAction.UndoProposal(proposal.id)) },
            )
        }
    }
}

@Composable
private fun CompactStatusChip(
    state: AdvisorUiState,
    onClick: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val text = when {
        !state.configured -> "未配置模型"
        !state.consentValid -> "已配置 · 等待授权"
        else -> "已配置 · ${state.config.model}"
    }
    val dotColor = when {
        !state.configured -> colors.danger
        !state.consentValid -> colors.textTertiary
        else -> colors.forest
    }
    Surface(
        modifier = Modifier
            .widthIn(max = 250.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag("advisor_status_chip"),
        shape = RoundedCornerShape(50),
        color = colors.nested,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, androidx.compose.foundation.shape.CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "⌄",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun AdvisorStatusCard(
    state: AdvisorUiState,
    expanded: Boolean,
    compact: Boolean,
    keyboardOpen: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val title = when {
        !state.configured -> "还没有配置模型"
        !state.consentValid -> "模型已配置 · 等待授权"
        else -> "已配置 · ${state.config.model}"
    }
    val subtitle = when {
        !state.configured -> "配置你自己的服务后，才可能开始对话"
        !state.consentValid -> "先确认允许分享的记录，再开始"
        else -> "可读取：${AiConsentRules.categoriesSummary(state.consent.categories)}；" +
            "发送消息时才会向该地址发起真实请求，这里只表示本机配置齐全"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(15.dp),
        color = colors.nested,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact && keyboardOpen) 4.dp else if (compact) 8.dp else 14.dp,
            ),
        ) {
            if (compact && keyboardOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.configured && state.consentValid) "$title · 已授权" else title,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (state.configured && state.consentValid) {
                                    Modifier.testTag("advisor_consent_summary")
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MorrowIcon(
                            kind = if (expanded) MorrowIconKind.CHEVRON_UP else MorrowIconKind.CHEVRON_DOWN,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp),
                            contentDescription = if (expanded) "收起状态详情" else "展开状态详情",
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 28.dp else 34.dp)
                            .clip(CircleShape)
                            .background(colors.card),
                        contentAlignment = Alignment.Center,
                    ) {
                        MorrowIcon(
                            kind = MorrowIconKind.SPARKLE,
                            tint = MorrowIconColors.Active,
                            modifier = Modifier.size(if (compact) 15.dp else 18.dp),
                        )
                    }
                    Spacer(Modifier.width(if (compact) 8.dp else 11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (state.configured && state.consentValid) {
                                Modifier.testTag("advisor_consent_summary")
                            } else {
                                Modifier
                            },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MorrowIcon(
                            kind = if (expanded) MorrowIconKind.CHEVRON_UP else MorrowIconKind.CHEVRON_DOWN,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp),
                            contentDescription = if (expanded) "收起状态详情" else "展开状态详情",
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.hairline),
                )
                Spacer(Modifier.height(8.dp))
                StatusDetailRow(
                    label = "服务",
                    value = if (state.configured) safeEndpointLabel(state.config.endpoint) else "尚未配置",
                )
                StatusDetailRow(
                    label = "模型",
                    value = state.config.model.ifBlank { "尚未选择" },
                )
                StatusDetailRow(
                    label = "授权",
                    value = if (state.consentValid) {
                        AiConsentRules.categoriesSummary(state.consent.categories)
                    } else {
                        "尚未授权"
                    },
                )
            }

            if (!compact || expanded) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorrowTertiaryButton(
                        text = "模型设置",
                        onClick = onOpenSettings,
                        showArrow = true,
                        testTag = "advisor_open_settings",
                    )
                    if (state.configured) {
                        Spacer(Modifier.width(2.dp))
                        Text("·", color = colors.textTertiary, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(2.dp))
                        MorrowTertiaryButton(
                            text = "授权管理",
                            onClick = onOpenSettings,
                            showArrow = true,
                            testTag = "advisor_consent_manage",
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusDetailRow(label: String, value: String) {
    val colors = MorrowTheme.colors
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.width(42.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class AdvisorPrompt(val icon: MorrowIconKind, val title: String)

private val ADVISOR_PROMPTS = listOf(
    AdvisorPrompt(MorrowIconKind.COMPASS, "我还不知道未来想做什么"),
    AdvisorPrompt(MorrowIconKind.TREND, "最近的变化值得关注吗"),
    AdvisorPrompt(MorrowIconKind.BRANCH, "帮我想清楚一个决定"),
)

@Composable
private fun EmptyConversation(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "如果你愿意，可以从这里开始。",
            style = MorrowType.CardTitle,
            color = colors.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "不需要先想清楚。先从一句真实的话开始。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        ADVISOR_PROMPTS.forEachIndexed { index, prompt ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPick(prompt.title) }
                    .testTag("advisor_suggestion_$index"),
                shape = RoundedCornerShape(14.dp),
                color = colors.card,
                border = BorderStroke(1.dp, colors.hairline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.nested),
                        contentAlignment = Alignment.Center,
                    ) {
                        MorrowIcon(
                            kind = prompt.icon,
                            tint = MorrowIconColors.Active,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    MorrowIcon(
                        kind = MorrowIconKind.ARROW_RIGHT,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "系统只会按你授权的类别检索目标、进展和日记，并在回复下方标注来源。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun NotConfiguredPanel(onOpenSettings: () -> Unit) {
    val colors = MorrowTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .padding(20.dp)
            .testTag("advisor_not_configured"),
    ) {
        Text("先配置你自己的模型", style = MorrowType.CardTitle, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "需要 HTTPS 服务地址、模型名称和 API Key。密钥由 Android Keystore 加密保存在本机；" +
                "请求直接发往你填写的服务，不经过萌芽人生服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        MorrowPrimaryButton(
            text = "去设置",
            onClick = onOpenSettings,
            testTag = "advisor_configure_button",
        )
        Spacer(Modifier.height(12.dp))
        InlineNote("没有配置时，这里不会显示任何假装来自模型的回答。")
    }
}

@Composable
private fun ConsentPanel(
    state: AdvisorUiState,
    onAction: (AdvisorAction) -> Unit,
) {
    val colors = MorrowTheme.colors
    val recipient = AiConsentRules.normalizeRecipient(state.config.endpoint)
    val needsReconsent = state.consent.granted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .testTag("advisor_consent_panel"),
    ) {
        Text(
            text = if (needsReconsent) "需要重新确认数据接收方" else "首次启用 AI 顾问",
            style = MorrowType.CardTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (needsReconsent) {
                "服务地址、授权文案或数据类别发生了变化。请确认新的接收方后继续。"
            } else {
                "在开始对话前，请阅读并确认以下数据使用方式。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        AiConsentRules.consentIntroLines(recipient).forEach { line ->
            Text(
                "• $line",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("数据接收服务：$recipient", style = MaterialTheme.typography.labelMedium)
        Text(
            "本次将允许：${AiConsentRules.categoriesSummary(AiConsentRules.allCategories)}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                MorrowPrimaryButton(
                    text = "同意并启用",
                    onClick = { onAction(AdvisorAction.GrantConsent) },
                    testTag = "advisor_consent_agree",
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                MorrowSecondaryButton(
                    text = "暂不使用",
                    onClick = { onAction(AdvisorAction.DeclineConsent) },
                    testTag = "advisor_consent_decline",
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        state.infoMessage?.let { info ->
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = colors.forest,
                modifier = Modifier.testTag("advisor_consent_info"),
            )
            Spacer(Modifier.height(6.dp))
        }
        InlineNote("拒绝不会影响日记、目标和聊天记录的本地保存。")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Composer(
    state: AdvisorUiState,
    onAction: (AdvisorAction) -> Unit,
    canPropose: Boolean,
    keyboardOpen: Boolean,
) {
    val colors = MorrowTheme.colors
    val dark = isDarkTheme()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                MorrowTextField(
                    value = state.input,
                    onValueChange = { onAction(AdvisorAction.InputChange(it)) },
                    placeholder = "问点什么…",
                    maxLines = 4,
                    enabled = !state.sending,
                    testTag = "advisor_input",
                )
            }
            Spacer(Modifier.size(10.dp))
            if (state.sending) {
                Box(modifier = Modifier.width(76.dp)) {
                    MorrowSecondaryButton(
                        text = "取消",
                        onClick = { onAction(AdvisorAction.CancelRequest) },
                        testTag = "advisor_cancel_button",
                    )
                }
            } else {
                val canSend = state.canSend
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                if (dark) colors.insightCard else colors.forest
                            } else {
                                colors.nested
                            },
                        )
                        .clickable(enabled = canSend) { onAction(AdvisorAction.Send) }
                        .testTag("advisor_send"),
                    contentAlignment = Alignment.Center,
                ) {
                    MorrowIcon(
                        kind = MorrowIconKind.SEND,
                        tint = if (canSend) colors.insightText else colors.textDisabled,
                        modifier = Modifier.size(20.dp),
                        contentDescription = "发送",
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: AdvisorMessage,
    lastFailed: Boolean,
    lastWithSources: Boolean,
    lastProposal: Boolean,
    proposal: StoredGoalProposal?,
    record: StoredRecordProposal?,
    recordAction: RecordActionUiState?,
    onRetry: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenProposal: (StoredGoalProposal) -> Unit,
    onConfirmRecord: (StoredRecordProposal, RecordProposalDraft) -> Unit,
    onCancelRecord: (String) -> Unit,
    onUndoRecord: (String) -> Unit,
    onUpdateRecordDraft: (String, RecordProposalDraft) -> Unit,
    onOpenDiary: (String) -> Unit,
    onOpenGoal: (String) -> Unit,
) {
    val colors = MorrowTheme.colors
    val dark = isDarkTheme()
    val isUser = message.role == AdvisorRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (message.status == AdvisorMessageStatus.SENT) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag(if (isUser) "advisor_user_message" else "advisor_assistant_message"),
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 5.dp,
                    bottomEnd = if (isUser) 5.dp else 18.dp,
                ),
                color = if (isUser) {
                    if (dark) colors.insightCard else colors.forest
                } else {
                    colors.card
                },
                border = if (isUser) null else BorderStroke(1.dp, colors.hairline),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) colors.insightText else colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.nested)
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
                    color = colors.danger,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.errorMessage ?: "未知错误",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                MorrowTertiaryButton(
                    text = "重试",
                    onClick = onRetry,
                    showArrow = true,
                    testTag = if (lastFailed) "advisor_retry_button_last" else "advisor_retry_button",
                )
            }
        }

        if (message.sources.isNotEmpty()) {
            val goals = message.sources.count { it.type == AdvisorSourceType.GOAL }
            val journals = message.sources.count { it.type == AdvisorSourceType.JOURNAL }
            val progress = message.sources.count { it.type == AdvisorSourceType.PROGRESS }
            val signals = message.sources.count { it.type == AdvisorSourceType.GROWTH }
            val memories = message.sources.count { it.type == AdvisorSourceType.MEMORY }
            val summary = buildList {
                if (goals > 0) add("$goals 个目标")
                if (journals > 0) add("$journals 篇日记")
                if (progress > 0) add("$progress 条进展")
                if (signals > 0) add("$signals 条成长线索")
                if (memories > 0) add("$memories 条成长记忆")
            }.joinToString("、")
            TextButton(
                onClick = onOpenSources,
                modifier = Modifier.testTag(
                    if (lastWithSources) "advisor_sources_last" else "advisor_sources",
                ),
            ) {
                Text("参考了 $summary", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
        if (message.proposalError != null) {
            Text(
                text = message.proposalError,
                style = MaterialTheme.typography.labelSmall,
                color = colors.danger,
                modifier = Modifier.testTag("advisor_proposal_error"),
            )
            if (message.status == AdvisorMessageStatus.SENT) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("advisor_retry_proposal"),
                ) { Text("重试草案", color = colors.forest) }
            }
        }
        if (proposal != null) {
            TextButton(
                onClick = { onOpenProposal(proposal) },
                modifier = Modifier.testTag(
                    if (lastProposal) "advisor_proposal_open_last" else "advisor_proposal_open",
                ),
            ) {
                Text(
                    when {
                        proposal.isApplied -> "查看创建结果"
                        proposal.isUndone -> "查看已撤销草案"
                        else -> "查看目标草案（尚未保存）"
                    },
                    color = colors.forest,
                )
            }
        }
        if (record != null) {
            Spacer(Modifier.height(6.dp))
            RecordProposalCard(
                proposal = record,
                actionState = recordAction,
                onConfirm = { draft -> onConfirmRecord(record, draft) },
                onCancel = { onCancelRecord(record.id) },
                onUndo = { onUndoRecord(record.id) },
                onUpdateDraft = { draft -> onUpdateRecordDraft(record.id, draft) },
                onOpenDiary = { proposalId -> onOpenDiary(proposalId) },
                onOpenGoal = { proposalId -> onOpenGoal(proposalId) },
            )
        }
        Text(
            text = Timestamps.formatDateTime(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun SourceDialog(message: AdvisorMessage, onDismiss: () -> Unit) {
    val colors = MorrowTheme.colors
    AlertDialog(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("advisor_sources_dialog"),
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text("本轮参考的数据", style = MorrowType.CardTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "只展示来源引用，不包含完整正文副本；这些引用不会再次自动发送给其它服务。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
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
                Text("关闭", color = colors.forest)
            }
        },
    )
}
