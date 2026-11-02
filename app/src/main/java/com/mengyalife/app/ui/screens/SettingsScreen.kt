package com.mengyalife.app.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.ui.components.GroupDivider
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.KeyValueRow
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SettingsGroup
import com.mengyalife.app.ui.components.SettingsRow
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * 设置页（二级页面）。
 *
 * 约定：
 * - 不占用底部导航位置；进入后底栏不出现（由上层切换整棵子树实现）。
 * - 所有未实现的能力统一标注「后续接入」，点击只给说明性提示，
 *   **不会**产生「保存成功」之类的虚假反馈。
 * - 主题切换立即生效；本轮不引入 DataStore，所以不持久化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
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
                    text = "本轮仅完成页面结构与说明，标注「后续接入」的能力尚未实现。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- A. AI 服务 ----------------
            item {
                SettingsGroup(title = "AI 服务") {
                    SettingsRow(
                        title = "模型配置",
                        subtitle = "服务提供方、模型与生成参数",
                        badge = "后续接入",
                        dimmed = true,
                        testTag = "settings_model_config",
                        onClick = { notify("后续接入：本版本不接入任何模型服务") },
                    )
                    GroupDivider()
                    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                        KeyValueRow(label = "当前配置状态", value = "未配置（演示环境）")
                    }
                    GroupDivider()
                    SettingsRow(
                        title = "API 服务地址",
                        subtitle = "自建网关或服务商 endpoint",
                        badge = "后续接入",
                        dimmed = true,
                        testTag = "settings_api_endpoint",
                        onClick = { notify("后续接入：当前不会发起任何网络请求") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "模型",
                        subtitle = "默认模型与备用模型",
                        badge = "后续接入",
                        dimmed = true,
                        testTag = "settings_model_name",
                        onClick = { notify("后续接入：当前未选择任何模型") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "密钥",
                        subtitle = "仅保存在本机，不随日志或备份导出",
                        badge = "后续接入",
                        dimmed = true,
                        testTag = "settings_api_key",
                        onClick = { notify("后续接入：本版本不接收、不保存、不发送任何 API Key") },
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    InlineNote("本版本不申请网络权限，也不会读取、存储或上传任何密钥。")
                }
            }

            // ---------------- B. 数据与隐私 ----------------
            item {
                SettingsGroup(title = "数据与隐私") {
                    SettingsRow(
                        title = "本地数据管理",
                        subtitle = "当前页面内容均为内置演示数据，未写入数据库",
                        badge = "演示",
                        dimmed = true,
                        testTag = "settings_local_data",
                        onClick = { notify("本版本没有本地数据库，页面内容为演示数据") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "数据导入 / 导出",
                        subtitle = "后续会支持本地文件的导入与导出",
                        badge = "后续接入",
                        dimmed = true,
                        testTag = "settings_import_export",
                        onClick = { notify("后续接入：当前没有可导入或导出的数据") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "数据采集与授权",
                        subtitle = "本版本不采集任何数据，也不申请网络权限",
                        dimmed = true,
                        testTag = "settings_data_policy",
                        onClick = { notify("本版本不采集任何数据，无网络权限") },
                    )
                    GroupDivider()
                    SettingsRow(
                        title = "删除全部数据",
                        subtitle = "该操作尚未实现，点击不会删除任何内容",
                        dimmed = true,
                        testTag = "settings_delete_data",
                        onClick = { notify("功能尚未实现：未删除任何数据") },
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
                        InlineNote("主题切换立即生效。本轮不引入 DataStore，设置仅在本次运行有效，重启后恢复「跟随系统」。")
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
