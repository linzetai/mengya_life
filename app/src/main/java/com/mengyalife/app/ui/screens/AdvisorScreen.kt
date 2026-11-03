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
import com.mengyalife.app.domain.advisor.AdvisorMessage
import com.mengyalife.app.domain.advisor.AdvisorMessageStatus
import com.mengyalife.app.domain.advisor.AdvisorRole
import com.mengyalife.app.domain.advisor.AdvisorRules
import com.mengyalife.app.domain.advisor.safeEndpointLabel
import com.mengyalife.app.ui.advisor.AdvisorAction
import com.mengyalife.app.ui.advisor.AdvisorUiState
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.util.Timestamps

/**
 * 顾问：真实 AI 对话 + 本地持久化 + 可授权的目标上下文。
 *
 * 页面只发 Action；请求、持久化、重试都在 AdvisorViewModel 里。
 * 失败消息显示错误与重试，不会显示成 AI 回复。
 */
@Composable
fun AdvisorScreen(
    listState: LazyListState,
    state: AdvisorUiState,
    onAction: (AdvisorAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var goalPickerOpenLocal by remember { mutableStateOf(false) }
    val pickerOpen = state.goalPickerOpen || goalPickerOpenLocal

    LaunchedEffect(state.messages.size, state.sending) {
        val target = (state.messages.size + 3).coerceAtLeast(0)
        if (state.messages.isNotEmpty() || state.sending) {
            runCatching { listState.animateScrollToItem(target) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // 顶部：配置状态 + 模型设置入口
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
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("advisor_open_settings"),
            ) {
                Text("模型设置")
            }
        }

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
            if (!state.configured) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp)
                            .testTag("advisor_not_configured"),
                    ) {
                        Text(
                            text = "先配置你自己的模型",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "需要 HTTPS 服务地址、模型名称和 API Key。密钥由 Android Keystore " +
                                "加密保存在本机；请求直接发往你填写的服务，不经过萌芽人生服务器。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("advisor_configure_button"),
                        ) {
                            Text("去设置")
                        }
                    }
                }
            } else {
                item { ContextBar(state = state, onAction = onAction, onPick = { goalPickerOpenLocal = true }) }
            }

            if (state.messages.isEmpty()) {
                item {
                    InlineNote(
                        if (state.configured) {
                            "还没有对话。提问后，消息与模型回复都会保存在本机，重启后仍可查看。"
                        } else {
                            "配置完成后，这里会显示你与模型的真实对话。"
                        },
                    )
                }
            } else {
                items(state.messages.size) { index ->
                    val message = state.messages[index]
                    val lastFailed = message.status != AdvisorMessageStatus.SENT &&
                        state.messages.drop(index + 1).none {
                            it.status == AdvisorMessageStatus.FAILED ||
                                it.status == AdvisorMessageStatus.CANCELLED
                        }
                    MessageRow(
                        message = message,
                        lastFailed = lastFailed,
                        onRetry = { onAction(AdvisorAction.Retry(message.id)) },
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
                        ) {
                            Text("取消")
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                item {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("advisor_error"),
                    )
                }
            }
            if (state.infoMessage != null) {
                item {
                    Text(
                        text = state.infoMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("advisor_info"),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = { onAction(AdvisorAction.InputChange(it)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("advisor_input"),
                    placeholder = { Text(if (state.configured) "问点什么…" else "先到设置页配置模型") },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.medium,
                    enabled = !state.sending,
                )
                Spacer(Modifier.size(8.dp))
                if (state.sending) {
                    Button(
                        onClick = { onAction(AdvisorAction.CancelRequest) },
                        modifier = Modifier.testTag("advisor_cancel_button"),
                    ) {
                        Text("取消")
                    }
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

    if (pickerOpen) {
        GoalPickerDialog(
            state = state,
            onSelect = {
                goalPickerOpenLocal = false
                onAction(AdvisorAction.SelectGoal(it))
            },
            onDismiss = {
                goalPickerOpenLocal = false
                onAction(AdvisorAction.DismissGoalPicker)
            },
        )
    }

    state.consent?.let { consent ->
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("advisor_consent_dialog"),
            onDismissRequest = { onAction(AdvisorAction.CancelConsent) },
            title = { Text("本次将发送哪些个人数据？") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "确认后才会把下面的目标摘要随本次问题一起发送；取消则本次不发送。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    AdvisorRules.consentLines(consent.context).forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "发送对象：${safeEndpointLabel(state.config.endpoint)} · 模型 ${state.config.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(AdvisorAction.ConfirmConsent) },
                    modifier = Modifier.testTag("advisor_consent_confirm"),
                ) {
                    Text("确认并发送")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(AdvisorAction.CancelConsent) },
                    modifier = Modifier.testTag("advisor_consent_cancel"),
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ContextBar(
    state: AdvisorUiState,
    onAction: (AdvisorAction) -> Unit,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advisor_context_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.contextGoal == null) "目标上下文：未附带" else
                    "目标上下文：${state.contextGoal.title}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (state.contextGoal == null) {
                    "默认只发送你的问题与近期对话，不发送日记或目标。"
                } else {
                    "发送前会展示本次将发送的目标摘要并等待确认。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                if (state.contextGoal == null) onPick() else onAction(AdvisorAction.ClearContext)
            },
            modifier = Modifier.testTag("advisor_context_action"),
        ) {
            Text(if (state.contextGoal == null) "选择" else "移除")
        }
    }
}

@Composable
private fun GoalPickerDialog(
    state: AdvisorUiState,
    onSelect: (com.mengyalife.app.domain.advisor.AdvisorGoalChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("advisor_goal_picker"),
        onDismissRequest = onDismiss,
        title = { Text("选择附带的目标") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "只发送所选目标的名称、描述、阶段/行动标题与最近进展摘要；" +
                        "不发送日记正文和其它目标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (state.goalChoices.isEmpty()) {
                    Text(
                        text = "还没有长期目标。先到目标页创建一个，再回来附加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("advisor_goal_picker_empty"),
                    )
                } else {
                    state.goalChoices.forEach { goal ->
                        TextButton(
                            onClick = { onSelect(goal) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("advisor_goal_${goal.id}"),
                        ) {
                            Text(goal.title, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("advisor_goal_picker_cancel")) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MessageRow(
    message: AdvisorMessage,
    lastFailed: Boolean,
    onRetry: () -> Unit,
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
                        color = if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
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
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
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
                    modifier = Modifier.testTag(if (lastFailed) "advisor_retry_button_last" else "advisor_retry_button"),
                ) {
                    Text("重试")
                }
            }
        }
        Text(
            text = Timestamps.formatDateTime(message.createdAt) +
                if (message.contextGoalId != null && isUser) " · 附带目标上下文" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
        )
    }
}
