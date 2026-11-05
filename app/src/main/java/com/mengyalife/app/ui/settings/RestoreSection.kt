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

/** G4A-2A：恢复只读预览。没有执行按钮，不会写 DB/DataStore。 */
@Composable
fun RestoreSection() {
    val vm: RestoreViewModel = viewModel(factory = RestoreViewModel.Factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onFileChosen(uri.toString())
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        vm.consumeError()
    }

    SettingsGroup(title = "恢复备份（只读预览）") {
        SettingsRow(
            title = "选择 v2 备份预览",
            subtitle = "仅校验并展示将被覆盖的范围；本阶段不会写入数据",
            badge = "只读",
            testTag = "settings_restore_v2",
            onClick = { picker.launch(arrayOf("*/*")) },
        )
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            InlineNote("只读取你选择的文件；不申请广泛存储权限；不会恢复 API Key/授权/通知权限。")
            InlineNote("最终执行按钮在 G4A-2B 开放；本阶段没有写入路径。")
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
            title = { Text("恢复预览（只读）") },
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
                        Text("可确定修复（仅计划，不执行）", style = MaterialTheme.typography.labelLarge)
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
                    Text("尚未执行恢复；没有写入任何数据。", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.testTag("restore_execute_disabled"),
                ) { Text("执行恢复（G4A-2B 开放）") }
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
