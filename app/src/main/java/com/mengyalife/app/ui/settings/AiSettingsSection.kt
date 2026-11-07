package com.mengyalife.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mengyalife.app.ui.advisor.AiSettingsAction
import com.mengyalife.app.ui.advisor.AiSettingsUiState
import com.mengyalife.app.ui.components.GroupDivider
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowSecondaryButton
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.components.morrowTextFieldColors
import com.mengyalife.app.ui.components.SettingsGroup
import com.mengyalife.app.ui.components.StatusPill

/**
 * 设置页「AI 服务」：BYOK 配置、测试连接、删除密钥。
 *
 * 密钥输入框始终遮挡；保存后不再回显；状态里只有 hasKey 布尔值。
 */
@Composable
fun AiSettingsSection(
    state: AiSettingsUiState,
    onAction: (AiSettingsAction) -> Unit,
) {
    var deleteConfirm by remember { mutableStateOf(false) }

    SettingsGroup(title = "AI 服务") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .semantics { testTagsAsResourceId = true },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "模型配置",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MorrowTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(text = if (state.configured) "已配置" else "未配置")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "V1 支持 OpenAI-compatible Chat Completions；请求直接发往你填写的地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MorrowTheme.colors.textSecondary,
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.endpoint,
                onValueChange = { onAction(AiSettingsAction.EndpointChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_endpoint_input"),
                label = { Text("API 服务地址（HTTPS）") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                enabled = !state.saving,
                shape = RoundedCornerShape(14.dp),
                colors = morrowTextFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.model,
                onValueChange = { onAction(AiSettingsAction.ModelChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_model_input"),
                label = { Text("模型名称") },
                placeholder = { Text("例如 deepseek-chat / gpt-4o-mini") },
                singleLine = true,
                enabled = !state.saving,
                shape = RoundedCornerShape(14.dp),
                colors = morrowTextFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.keyInput,
                onValueChange = { onAction(AiSettingsAction.KeyChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_key_input"),
                label = { Text("API Key") },
                placeholder = {
                    Text(if (state.hasKey) "已保存（留空表示不修改）" else "粘贴你的 API Key")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !state.saving,
                shape = RoundedCornerShape(14.dp),
                colors = morrowTextFieldColors(),
            )
            if (state.hasKey) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "已保存加密密钥；不会在界面、日志或导出文件中显示原文。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MorrowTheme.colors.forest,
                    modifier = Modifier.testTag("ai_key_saved"),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorrowPrimaryButton(
                    text = if (state.saving) "保存中…" else "保存配置",
                    onClick = { onAction(AiSettingsAction.Save) },
                    enabled = !state.saving && !state.testing,
                    loading = state.saving,
                    modifier = Modifier.weight(1f),
                    testTag = "ai_save",
                )
                MorrowSecondaryButton(
                    text = if (state.testing) "测试中…" else "测试连接",
                    onClick = { onAction(AiSettingsAction.TestConnection) },
                    enabled = !state.saving && !state.testing,
                    modifier = Modifier.weight(1f),
                    testTag = "ai_test",
                )
            }

            if (state.hasKey) {
                Spacer(Modifier.height(4.dp))
                MorrowTertiaryButton(
                    text = "删除密钥",
                    onClick = { deleteConfirm = true },
                    enabled = !state.saving,
                    showArrow = true,
                    contentColor = MorrowTheme.colors.danger,
                    testTag = "ai_delete_key",
                )
            }

            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MorrowTheme.colors.forest,
                    modifier = Modifier.testTag("ai_status"),
                )
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ai_error"),
                )
            }

            Spacer(Modifier.height(10.dp))
            InlineNote(
                "密钥由 Android Keystore 加密保存在本机；不上传、不写入 Room、不进入导出文件。" +
                    "应用新增的 INTERNET 权限只用于你主动发起的模型请求。",
            )
            Spacer(Modifier.height(6.dp))
            InlineNote("模型输出只作为建议，不会自动修改目标或日记；完整备份与恢复在下方独立入口，密钥不会进入备份。")

            if (state.allowFakeEngine) {
                Spacer(Modifier.height(10.dp))
                GroupDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "使用离线测试引擎（仅 QA）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MorrowTheme.colors.textPrimary,
                            modifier = Modifier.testTag("ai_fake_engine_label"),
                        )
                        Text(
                            text = "不发起网络请求，用于自动化验证对话保存与失败重试。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MorrowTheme.colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = state.useFakeEngine,
                        onCheckedChange = { onAction(AiSettingsAction.SetFakeEngine(it)) },
                        modifier = Modifier.testTag("ai_fake_engine"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MorrowTheme.colors.insightText,
                            checkedTrackColor = MorrowTheme.colors.forest,
                            uncheckedThumbColor = MorrowTheme.colors.textTertiary,
                            uncheckedTrackColor = MorrowTheme.colors.nested,
                        ),
                    )
                }
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("ai_delete_dialog"),
            onDismissRequest = { deleteConfirm = false },
            containerColor = MorrowTheme.colors.card,
            shape = RoundedCornerShape(20.dp),
            title = { Text("删除 API Key？", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "删除后旧密钥不能再被使用，需要重新输入才能继续对话。" +
                        "已保存的聊天记录不会删除。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirm = false
                        onAction(AiSettingsAction.DeleteKey)
                    },
                    modifier = Modifier.testTag("ai_delete_confirm"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteConfirm = false },
                    modifier = Modifier.testTag("ai_delete_cancel"),
                ) {
                    Text("取消")
                }
            },
        )
    }
}
