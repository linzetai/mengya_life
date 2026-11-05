package com.mengyalife.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.SettingsGroup
import com.mengyalife.app.ui.components.SettingsRow

/**
 * 完整加密备份 v2 入口。
 *
 * 与旧 v1“导出我的数据”并存且明确区分：v1 只含日记/目标/进展，不是完整备份。
 */
@Composable
fun BackupSection() {
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) viewModel.onDestinationCancelled() else viewModel.onDestinationChosen(uri.toString())
    }

    LaunchedEffect(state.suggestedFileName) {
        val name = state.suggestedFileName ?: return@LaunchedEffect
        saveLauncher.launch(name)
        viewModel.onSaveDialogLaunched()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeMessage()
    }

    var showPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SettingsGroup(title = "完整备份（v2）") {
        SettingsRow(
            title = "导出完整加密备份",
            subtitle = "完整导出 10 类数据，使用口令加密；不包含 API Key 与授权",
            badge = "v2",
            testTag = "settings_backup_v2",
            onClick = {
                if (!state.busy) {
                    password = ""
                    confirm = ""
                    error = null
                    showPassword = true
                    viewModel.requestBackup()
                }
            },
        )
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            InlineNote(
                "完整备份包含日记、目标、进展、对话、草案、成长线索/记忆、洞察、周回顾与历史 AI 归档；" +
                    "不包含 API Key、Keystore、授权同意或设备标识。",
            )
            Spacer(Modifier.height(4.dp))
            InlineNote("旧版“导出我的数据”只含日记/目标/进展，不是完整备份。")
        }
    }

    if (showPassword && state.showPassword) {
        AlertDialog(
            modifier = Modifier.testTag("backup_password_dialog"),
            onDismissRequest = {
                showPassword = false
                password = ""
                confirm = ""
                error = null
                viewModel.cancelPassword()
            },
            title = { Text("设置备份口令") },
            text = {
                Column {
                    Text(
                        "备份文件将用你的口令加密。口令不会保存在应用内，忘记后无法恢复，请自行妥善保管。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("备份口令（至少 ${BackupViewModel.MIN_PASSPHRASE_CHARS} 个字符）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.testTag("backup_password_input"),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        label = { Text("再次输入口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.testTag("backup_password_confirm_input"),
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "范围：10 类数据 + 主题/周回顾开关/AI endpoint 与 model（不含 Key、授权、QA 假引擎）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("backup_password_confirm"),
                    onClick = {
                        if (password != confirm) {
                            error = "两次输入的口令不一致"
                            return@TextButton
                        }
                        val issue = viewModel.startBackup(password)
                        if (issue != null) {
                            error = issue
                        } else {
                            showPassword = false
                            password = ""
                            confirm = ""
                            error = null
                        }
                    },
                ) { Text("开始备份") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("backup_password_cancel"),
                    onClick = {
                        showPassword = false
                        password = ""
                        confirm = ""
                        error = null
                        viewModel.cancelPassword()
                    },
                ) { Text("取消") }
            },
        )
    }

    if (state.preparing || state.writing) {
        InlineNote(if (state.preparing) "正在准备完整备份…" else "正在写入并校验备份…")
    }
    state.lastResult?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .testTag("backup_v2_status"),
        )
    }
}
