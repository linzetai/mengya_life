package com.mengyalife.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.mengyalife.app.domain.advisor.AiConsentRules
import com.mengyalife.app.domain.advisor.ConsentCategory
import com.mengyalife.app.ui.advisor.AiSettingsAction
import com.mengyalife.app.ui.advisor.AiSettingsUiState
import com.mengyalife.app.ui.components.GroupDivider
import com.mengyalife.app.ui.components.SettingsGroup
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.util.Timestamps

/**
 * 设置 → AI 与隐私：持续授权状态、四类数据开关、撤回入口。
 * 关闭只影响未来请求，不删除本地数据。
 */
@Composable
fun AiPrivacySection(
    state: AiSettingsUiState,
    onAction: (AiSettingsAction) -> Unit,
) {
    var withdrawConfirm by remember { mutableStateOf(false) }
    val consent = state.consent
    val status = when {
        !consent.granted -> "未授权"
        !state.consentValid -> "需重新授权"
        else -> "已授权"
    }
    val statusTag = when {
        !consent.granted -> "ai_consent_status_none"
        !state.consentValid -> "ai_consent_status_stale"
        else -> "ai_consent_status_granted"
    }

    SettingsGroup(title = "AI 与隐私") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .semantics { testTagsAsResourceId = true },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "个人数据授权",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(text = status, modifier = Modifier.testTag(statusTag))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "数据接收服务：" + (
                    consent.recipient.takeIf { it.isNotBlank() }
                        ?: AiConsentRules.normalizeRecipient(state.endpoint).takeIf { it.isNotBlank() }
                        ?: "尚未配置"
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("ai_consent_recipient"),
            )
            consent.grantedAt?.takeIf { it > 0 }?.let {
                Text(
                    text = "授权时间：" + Timestamps.formatDateTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (consent.granted && !state.consentValid) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "服务地址或授权版本已变化，需要重新确认新的数据接收方。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ai_consent_needs_reconsent"),
                )
            }
            if (!consent.granted) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "请到顾问页完成首次授权；未授权时不会读取或发送个人数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ai_consent_not_granted_hint"),
                )
            }

            Spacer(Modifier.height(8.dp))
            ConsentCategory.entries.forEach { category ->
                GroupDivider()
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            category.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = consent.enabled(category),
                        onCheckedChange = {
                            onAction(AiSettingsAction.ToggleCategory(category, it))
                        },
                        enabled = consent.granted && state.consentValid,
                        modifier = Modifier.testTag("ai_category_${category.name}"),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            state.message?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("ai_privacy_status"),
                )
            }
            state.error?.let { error ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ai_privacy_error"),
                )
            }

            if (consent.granted) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { withdrawConfirm = true },
                    modifier = Modifier.testTag("ai_withdraw"),
                ) {
                    Text("撤回全部授权", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "关闭某个类别只影响未来请求，不会删除本机已有的日记、目标与聊天记录；已经发送给第三方模型服务的数据无法从对方撤回。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "授权长期有效，只在首次启用时确认；更换服务地址或新增数据类别时需要重新确认。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (withdrawConfirm) {
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("ai_withdraw_dialog"),
            onDismissRequest = { withdrawConfirm = false },
            title = { Text("撤回全部 AI 数据授权？") },
            text = {
                Text(
                    "撤回后，顾问不会再自动读取或发送目标、进展、日记与历史对话；" +
                        "已有本地数据与聊天记录不会删除。若要继续使用 AI，需要重新授权。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        withdrawConfirm = false
                        onAction(AiSettingsAction.WithdrawConsent)
                    },
                    modifier = Modifier.testTag("ai_withdraw_confirm"),
                ) { Text("撤回", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { withdrawConfirm = false },
                    modifier = Modifier.testTag("ai_withdraw_cancel"),
                ) { Text("取消") }
            },
        )
    }
}
