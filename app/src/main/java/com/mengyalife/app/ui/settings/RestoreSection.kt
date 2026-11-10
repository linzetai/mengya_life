package com.mengyalife.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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

/** 恢复备份：选择文件 → 口令校验 → 预览覆盖范围 → 确认执行 → 结果。 */
@Composable
fun RestoreSection() {
    val vm: RestoreViewModel = viewModel(factory = RestoreViewModel.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as com.mengyalife.app.MengyaApplication
    val completion by app.container.restoreCoordinator.completionMessage.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var riskAccepted by remember(state.preview?.backupId) { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onFileChosen(uri.toString())
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        vm.consumeError()
    }

    completion?.let { message ->
        androidx.compose.material3.Card(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).testTag("restore_success_banner"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("恢复完成", style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodySmall)
                Text(
                    "备份文件不包含 API Key。恢复后本机凭据按服务地址处理：" +
                        "地址相同则保留；地址不同会删除旧 Key 并撤销授权；每周 AI 回顾会关闭，需要时可重新开启。",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { app.container.restoreCoordinator.clearCompletionMessage() }) { Text("关闭提示") }
            }
        }
    }

    SettingsGroup(title = "恢复备份") {
        SettingsRow(
            title = "选择 v2 备份文件",
            subtitle = "先校验口令并预览会被覆盖的记录，确认后才写入",
            badge = "校验 → 预览 → 确认",
            testTag = "settings_restore_v2",
            onClick = { picker.launch(arrayOf("*/*")) },
        )
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            InlineNote("只读取你选中的文件；应用不申请广泛存储权限。")
            InlineNote(
                "恢复会替换本机全部日记、目标与进展。API Key 不在备份里：" +
                    "同一服务地址保留本机 Key，不同地址会被删除并撤销授权。",
            )
        }
    }

    if (state.showPassword) {
        AlertDialog(
            modifier = Modifier.testTag("restore_password_dialog"),
            onDismissRequest = { vm.cancelPassword(); password = ""; passwordError = null },
            title = { Text("输入备份口令") },
            text = {
                Column {
                    Text(state.fileName ?: "已选择文件", style = MaterialTheme.typography.bodyMedium)
                    Text("${state.fileSize} 字节", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passwordError = null },
                        label = { Text("备份口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.testTag("restore_password_input"),
                    )
                    passwordError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("restore_password_confirm"),
                    onClick = {
                        val issue = vm.confirmPassword(password)
                        if (issue != null) passwordError = issue
                        else { password = ""; passwordError = null }
                    },
                ) { Text("校验并预览") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("restore_password_cancel"),
                    onClick = { vm.cancelPassword(); password = ""; passwordError = null },
                ) { Text("取消") }
            },
        )
    }

    state.preview?.let { preview ->
        AlertDialog(
            modifier = Modifier.testTag("restore_preview"),
            onDismissRequest = { vm.dismissPreview() },
            title = { Text("恢复预览") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("备份时间：${preview.backupTimeLabel}", style = MaterialTheme.typography.bodySmall)
                    Text("来源：${preview.sourcePackage} ${preview.sourceVersionName}${if (preview.sourceQa) "（QA）" else "（正式）"}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("格式：v${preview.backupFormatVersion} / 加密 / KDF ${preview.kdfIterations}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("数据库：${preview.dbSchemaVersion} → 当前 ${preview.currentDbSchemaVersion}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("文件：${preview.fileSizeBytes} 字节", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("记录数（备份 / 本机）", style = MaterialTheme.typography.labelLarge)
                    preview.counts.forEach { c ->
                        Text(
                            "${c.label}：${c.backupCount} / ${c.currentCount}" +
                                if (c.willRestore) "（将覆盖）" else "（仅归档，不恢复）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preview.repairs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("兼容修复（执行时按此计划应用）", style = MaterialTheme.typography.labelLarge)
                        preview.repairs.forEach { r -> Text("${r.action}：${r.count}", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (preview.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("兼容性/历史引用 warning", style = MaterialTheme.typography.labelLarge)
                        preview.warnings.forEach { w -> Text(w.message, style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("不会恢复", style = MaterialTheme.typography.labelLarge)
                    preview.notRestored.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    Text("恢复会替换本机全部用户数据。建议先关闭预览，用上方“完整加密备份”导出当前数据。",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = riskAccepted,
                            onCheckedChange = { riskAccepted = it },
                            modifier = Modifier.testTag("restore_risk_checkbox"),
                        )
                        Text("我理解确认后会用备份替换本机数据",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.executing) {
                        Text(
                            "正在恢复数据并完成安全重置…",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("restore_executing"),
                        )
                    } else {
                        Text(
                            "确认恢复后才会写入；现在还没有改动任何数据。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = riskAccepted && !state.executing,
                    onClick = { vm.executeConfirmed() },
                    modifier = Modifier.testTag("restore_execute"),
                ) { Text("确认恢复（替换本机数据）") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("restore_preview_close"),
                    onClick = { vm.dismissPreview() },
                ) { Text("关闭") }
            },
        )
    }

}
