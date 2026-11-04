package com.mengyalife.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.ui.components.GroupDivider
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.KeyValueRow
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SettingsGroup
import com.mengyalife.app.ui.components.SettingsRow
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.advisor.AiSettingsAction
import com.mengyalife.app.ui.advisor.AiSettingsUiState
import com.mengyalife.app.ui.settings.AiPrivacySection
import com.mengyalife.app.ui.settings.AiSettingsSection
import com.mengyalife.app.ui.settings.ExportUiState
import com.mengyalife.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * 设置页（二级页面）。
 *
 * 约定：
 * - 不占用底部导航位置；进入后底栏不出现（由上层切换整棵子树实现）。
 * - 所有未实现的能力统一标注「后续接入」，点击只给说明性提示，
 *   **不会**产生「保存成功」之类的虚假反馈。
 * - 主题**已经持久化**：写入 DataStore，重启后保持；「跟随系统」实时响应系统深浅色。
 * - 「导出我的数据」是真实实现：数据库事务快照 → JSON → 系统文件选择器 → 回读校验。
 * - 导入恢复**尚未实现**，页面里明确写清，不暗示已经可以还原。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    versionName: String,
    exportState: ExportUiState,
    onExportRequested: () -> Unit,
    onExportConfirmed: () -> String?,
    onExportDestination: (Uri) -> Unit,
    onExportCancelled: () -> Unit,
    onExportDestinationCancelled: () -> Unit,
    onExportMessageConsumed: () -> Unit,
    aiState: AiSettingsUiState,
    onAiAction: (AiSettingsAction) -> Unit,
    onOpenMemories: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // SAF：让用户自己选保存位置，应用不申请任何存储权限。
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) onExportDestinationCancelled() else onExportDestination(uri)
    }

    // 导出结果（成功条数 / 失败原因 / 取消）统一走 Snackbar，不伪造成功。
    LaunchedEffect(exportState.message) {
        val message = exportState.message ?: return@LaunchedEffect
        // 导出结果（成功条数/失败原因）信息量大，用 Long 让用户有时间看清。
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        onExportMessageConsumed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(
                    text = "配置与偏好",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "已实现的设置会立即生效并保存在本机；标注「后续接入」的能力尚未实现。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (BuildConfig.QA_BUILD) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier.testTag("settings_qa_banner"),
                    ) {
                        InlineNote(
                            "QA 测试构建：包名 ${BuildConfig.APPLICATION_ID}，数据库 " +
                                "${BuildConfig.DATABASE_NAME}，与正式版完全隔离。",
                        )
                    }
                }
            }

            // ---------------- A. AI 服务 ----------------
            item {
                AiSettingsSection(state = aiState, onAction = onAiAction)
            }

            item {
                AiPrivacySection(state = aiState, onAction = onAiAction)
            }

            item {
                SettingsGroup(title = "成长记忆") {
                    SettingsRow(
                        title = "查看成长记忆",
                        subtitle = "AI 从多篇日记线索整理的长期变化；可纠正或删除",
                        testTag = "settings_growth_memories",
                        onClick = onOpenMemories,
                    )
                }
            }

            // ---------------- B. 数据与隐私 ----------------
            item {
                SettingsGroup(title = "数据与隐私") {
                    SettingsRow(
                        title = "本地数据管理",
                        subtitle = "日记与目标保存在本机数据库；AI 请求只发送你确认的内容",
                        badge = "本机",
                        dimmed = false,
                        testTag = "settings_local_data",
                        onClick = { notify("日记与目标保存在本机数据库；本版本没有上传通道") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "导出我的数据",
                        subtitle = when {
                            exportState.preparing -> "正在读取数据库快照…"
                            exportState.exporting -> "正在写入并校验文件…"
                            else -> "把日记、目标与进展记录导出为 JSON；保存位置由你选择"
                        },
                        badge = "JSON",
                        dimmed = false,
                        testTag = "settings_export_data",
                        onClick = {
                            if (!exportState.preparing && !exportState.exporting) onExportRequested()
                        },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "导入数据",
                        subtitle = "本轮只支持导出；导入恢复尚未实现，后续单独验收",
                        badge = "后续接入",
                        dimmed = true,
                        testTag = "settings_import_data",
                        onClick = { notify("后续接入：当前只能导出，还不能把文件导回应用") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "数据采集与授权",
                        subtitle = "不采集任何数据；INTERNET 仅用于你主动发起的 AI 请求",
                        dimmed = true,
                        testTag = "settings_data_policy",
                        onClick = { notify("不采集任何数据；联网只发生在你主动发送 AI 请求时") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "删除全部数据",
                        subtitle = "该操作尚未实现：点击不会删除你的日记或目标",
                        dimmed = true,
                        testTag = "settings_delete_data",
                        onClick = { notify("功能尚未实现：未删除任何日记或目标") },
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    InlineNote(
                        "导出文件可能包含敏感个人信息（日记正文、目标与进展），请妥善保管。" +
                            "应用不会自动上传，也不会自动分享；导出不包含任何 API Key。",
                    )
                }
            }

            // ---------------- C. 外观与体验 ----------------
            item {
                SettingsGroup(title = "外观与体验") {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) GroupDivider()
                        ThemeOptionRow(
                            mode = mode,
                            selected = themeMode == mode,
                            onSelect = { onThemeModeChange(mode) },
                        )
                    }
                    GroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        InlineNote(
                            "主题切换立即生效，并保存在本机（DataStore）：重启后保持上次选择。" +
                                "选择「跟随系统」时会随系统深浅色变化。",
                        )
                    }
                    GroupDivider()
                    SettingsRow(
                        title = "提醒与通知",
                        subtitle = "每日回顾提醒、周报摘要",
                        badge = "后续接入",
                        dimmed = true,
                        testTag = "settings_notification",
                        onClick = { notify("后续接入：本版本不申请通知权限，也不会发送提醒") },
                    )
                }
            }

            // ---------------- D. 关于 ----------------
            item {
                SettingsGroup(title = "关于") {
                    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                        KeyValueRow(label = "应用名称", value = "萌芽人生")
                        KeyValueRow(label = "英文标识", value = "Mengya Life")
                        KeyValueRow(label = "当前版本", value = versionName)
                        KeyValueRow(label = "数据存储", value = "本机（无云端同步）")
                        if (BuildConfig.QA_BUILD) {
                            KeyValueRow(label = "构建类型", value = "QA 测试包（数据隔离）")
                        }
                    }
                    GroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(
                            text = "产品定位",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "关注长期的人生方向，而不是短期的消费决策。" +
                                "把目标、趋势与日记放在一起，帮助你判断「这段时间有没有真的在变好」，" +
                                "并在需要时由一个顾问把方向拆成下一步能做的动作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(text = "本地优先")
                            Spacer(Modifier.size(8.dp))
                            StatusPill(text = "自带密钥")
                            Spacer(Modifier.size(8.dp))
                            StatusPill(text = "无后端")
                        }
                        Spacer(Modifier.height(10.dp))
                        InlineNote(DemoData.DATA_SCOPE_NOTE)
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (exportState.showWarning) {
        val counts = exportState.bundle?.counts
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("export_warning"),
            onDismissRequest = onExportCancelled,
            title = { Text("导出我的数据？") },
            text = {
                Column {
                    Text(
                        text = if (counts == null) {
                            "将导出一份 JSON 文件。"
                        } else {
                            "将导出：日记 ${counts.journalEntries} 条 · 目标 ${counts.goals} 个 · " +
                                "进展记录 ${counts.goalCheckins} 条。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "文件包含日记正文等敏感个人信息，可能被其他应用读取，" +
                            "请保存到安全的位置并妥善保管。应用不会上传或自动分享。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("export_sensitive_warning"),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "导出不包含 API Key；导入恢复尚未实现，这个文件目前不能导回应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = onExportConfirmed()
                        if (name != null) saveLauncher.launch(name)
                    },
                    modifier = Modifier.testTag("export_confirm"),
                ) {
                    Text("选择保存位置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onExportCancelled,
                    modifier = Modifier.testTag("export_cancel"),
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(mode.testTag)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = mode.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
