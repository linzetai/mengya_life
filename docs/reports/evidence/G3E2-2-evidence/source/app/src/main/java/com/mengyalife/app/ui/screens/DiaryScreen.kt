package com.mengyalife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.domain.JournalRules
import com.mengyalife.app.domain.growth.DiaryAnalysisStatus
import com.mengyalife.app.domain.growth.GrowthSignal
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.journal.DiaryAction
import com.mengyalife.app.ui.journal.DiaryUiState
import com.mengyalife.app.ui.journal.JournalEditor
import com.mengyalife.app.ui.journal.JournalSheet
import com.mengyalife.app.util.Timestamps

/**
 * 日记页：**真实数据**（Room）展示，不再是演示数据。
 *
 * 本文件只做「渲染状态 + 发动作」，所有读写逻辑在 JournalViewModel /
 * JournalRepository 里。
 */
@Composable
fun DiaryScreen(
    listState: LazyListState,
    state: DiaryUiState,
    onAction: (DiaryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("journal_list"),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "把今天留下来",
                subtitle = "记录真实发生的细节，保存在这台设备上",
                // 日记是真实用户数据，绝不能挂"演示数据"标签
                showDemoTag = false,
            )
        }

        item {
            SectionCard(
                title = "今天",
                subtitle = "写下此刻最想留下的一句话",
                trailing = {
                    Button(
                        onClick = { onAction(DiaryAction.NewEntry) },
                        modifier = Modifier.testTag("diary_new"),
                    ) {
                        Text("写一条")
                    }
                },
            ) {
                InlineNote("日记保存在本机数据库，关闭应用后仍然存在。")
            }
        }

        item {
            SectionCard(
                title = "AI 提取",
                subtitle = "接入模型后，这里会展示从日记中提取的情绪、事项与目标线索",
                trailing = { StatusPill(text = "未启用") },
            ) {
                InlineNote("本轮不会分析你的日记，也不会把任何内容发送出去。")
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "全部记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (state.entries.isNotEmpty()) {
                    StatusPill(text = "${state.entries.size} 条")
                }
            }
        }

        when {
            state.readError != null -> item {
                SectionCard(title = "无法读取本地日记") {
                    InlineNote(state.readError)
                }
            }

            state.loading -> item {
                SectionCard(title = "正在读取…") {
                    InlineNote("正在从本机数据库加载你的记录。")
                }
            }

            state.entries.isEmpty() -> item {
                SectionCard(
                    title = "还没有记录",
                    subtitle = "写下的第一条会出现在这里",
                ) {
                    InlineNote("点右上角「写一条」开始记录。这里不会预先放任何演示内容。")
                }
            }

            else -> items(items = state.entries, key = { it.id }) { entry ->
                JournalEntryCard(
                    entry = entry,
                    onClick = { onAction(DiaryAction.Open(entry)) },
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    when (val sheet = state.sheet) {
        JournalSheet.Closed -> Unit

        JournalSheet.Creating, is JournalSheet.Editing -> JournalEditorSheet(
            isCreating = sheet == JournalSheet.Creating,
            editor = state.editor,
            onBodyChange = { onAction(DiaryAction.BodyChange(it)) },
            onTitleChange = { onAction(DiaryAction.TitleChange(it)) },
            onSave = { onAction(DiaryAction.Save) },
            onDismiss = { onAction(DiaryAction.CloseSheet) },
        )

        is JournalSheet.Viewing -> JournalDetailSheet(
            entry = sheet.entry,
            signals = state.signals,
            onEdit = { onAction(DiaryAction.Edit(sheet.entry)) },
            onDelete = { onAction(DiaryAction.RequestDelete(sheet.entry)) },
            onRetryAnalysis = { onAction(DiaryAction.RetryAnalysis(sheet.entry.id)) },
            onDeleteSignal = { onAction(DiaryAction.DeleteSignal(it)) },
            onCorrectSignal = { id, text -> onAction(DiaryAction.CorrectSignal(id, text)) },
            onDismiss = { onAction(DiaryAction.CloseSheet) },
        )
    }

    val pending = state.pendingDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { onAction(DiaryAction.DismissDelete) },
            title = { Text("删除这条日记？") },
            text = {
                Column {
                    Text("删除后无法恢复。将会删除：\n\n" + JournalRules.excerpt(pending.body, 40))
                    if (state.deleteError != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.deleteError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("journal_delete_error"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(DiaryAction.ConfirmDelete) },
                    modifier = Modifier.testTag("journal_delete_confirm"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(DiaryAction.DismissDelete) },
                    modifier = Modifier.testTag("journal_delete_cancel"),
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntryEntity,
    onClick: () -> Unit,
) {
    val edited = entry.updatedAt > entry.createdAt
    SectionCard(
        title = entry.title?.takeIf { it.isNotBlank() } ?: "无标题",
        subtitle = buildString {
            append(Timestamps.formatDateTime(entry.createdAt))
            if (edited) append(" · 已编辑")
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("journal_entry_${entry.id}"),
        trailing = { StatusPill(text = "查看") },
    ) {
        Text(
            text = JournalRules.excerpt(entry.body, 80),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalEditorSheet(
    isCreating: Boolean,
    editor: JournalEditor,
    onBodyChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                // ModalBottomSheet 是独立窗口，根节点上的 testTagsAsResourceId 不会传进来
                .semantics { testTagsAsResourceId = true },
        ) {
            Text(
                text = if (isCreating) "写一条日记" else "编辑日记",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = editor.title,
                onValueChange = onTitleChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_title_input"),
                label = { Text("标题（可选）") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                enabled = !editor.saving,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = editor.body,
                onValueChange = onBodyChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_body_input"),
                label = { Text("正文（必填）") },
                isError = editor.bodyError != null,
                supportingText = {
                    if (editor.bodyError != null) {
                        Text(
                            text = editor.bodyError,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("journal_body_error"),
                        )
                    }
                },
                minLines = 5,
                shape = MaterialTheme.shapes.medium,
                enabled = !editor.saving,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !editor.saving,
                ) {
                    Text("取消")
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onSave,
                    enabled = !editor.saving,
                    modifier = Modifier.testTag("journal_save"),
                ) {
                    Text(if (editor.saving) "保存中…" else "保存")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalDetailSheet(
    entry: JournalEntryEntity,
    signals: List<GrowthSignal>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetryAnalysis: () -> Unit,
    onDeleteSignal: (String) -> Unit,
    onCorrectSignal: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { testTagsAsResourceId = true },
        ) {
            Text(
                text = entry.title?.takeIf { it.isNotBlank() } ?: "无标题",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "创建于 " + Timestamps.formatDateTime(entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.updatedAt > entry.createdAt) {
                Text(
                    text = "更新于 " + Timestamps.formatDateTime(entry.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            AiDiscoverySection(
                entry = entry,
                signals = signals,
                onRetry = onRetryAnalysis,
                onDeleteSignal = onDeleteSignal,
                onCorrectSignal = onCorrectSignal,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("journal_edit"),
                ) {
                    Text("编辑")
                }
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("journal_delete"),
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AiDiscoverySection(
    entry: JournalEntryEntity,
    signals: List<GrowthSignal>,
    onRetry: () -> Unit,
    onDeleteSignal: (String) -> Unit,
    onCorrectSignal: (String, String) -> Unit,
) {
    var correcting by remember { mutableStateOf<GrowthSignal?>(null) }
    var deleting by remember { mutableStateOf<GrowthSignal?>(null) }
    var correctionText by remember { mutableStateOf("") }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("AI 发现", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(8.dp))
        Text(
            text = DiaryAnalysisStatus.label(entry.analysisStatus),
            style = MaterialTheme.typography.labelSmall,
            color = when (entry.analysisStatus) {
                DiaryAnalysisStatus.FAILED -> MaterialTheme.colorScheme.error
                DiaryAnalysisStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.testTag("journal_analysis_status"),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "仅在你配置 AI 并授权「日记」类别时发送；结果只作为线索，不会修改目标或进展。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (entry.analysisStatus) {
        DiaryAnalysisStatus.PENDING -> {
            Spacer(Modifier.height(6.dp))
            Text("待分析：保存后会在允许时自动分析。", style = MaterialTheme.typography.bodySmall)
        }
        DiaryAnalysisStatus.ANALYZING -> {
            Spacer(Modifier.height(6.dp))
            Text("正在分析这篇日记…", style = MaterialTheme.typography.bodySmall)
        }
        DiaryAnalysisStatus.FAILED -> {
            Spacer(Modifier.height(6.dp))
            Text(
                "分析失败：" + (entry.analysisError ?: "未知错误"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("journal_analysis_error"),
            )
            TextButton(onClick = onRetry, modifier = Modifier.testTag("journal_analysis_retry")) {
                Text("重试分析")
            }
        }
    }
    if (entry.analysisStatus == DiaryAnalysisStatus.COMPLETED && signals.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("这篇日记没有提取到可保留的成长线索。", style = MaterialTheme.typography.bodySmall)
    }
    signals.forEach { signal ->
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(10.dp)
                .testTag("growth_signal_${signal.id}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(signal.category.label, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = when {
                        signal.origin == com.mengyalife.app.domain.growth.GrowthSignalOrigin.USER -> "用户纠正"
                        signal.isInference -> "推测"
                        else -> "事实"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (signal.isInference) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
                signal.confidence?.let {
                    Spacer(Modifier.size(8.dp))
                    Text("可信度 ${(it * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(signal.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "原文：" + signal.evidence.take(180),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(
                    onClick = {
                        correcting = signal
                        correctionText = signal.content
                    },
                    modifier = Modifier.testTag("growth_signal_correct_${signal.id}"),
                ) { Text("纠正") }
                TextButton(
                    onClick = { deleting = signal },
                    modifier = Modifier.testTag("growth_signal_delete_${signal.id}"),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    correcting?.let { signal ->
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("growth_signal_correction_dialog"),
            onDismissRequest = { correcting = null },
            title = { Text("纠正这条线索") },
            text = {
                Column {
                    Text("纠正后会优先于旧 AI 推测，不会修改日记或目标。")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correctionText,
                        onValueChange = { correctionText = it },
                        modifier = Modifier.fillMaxWidth().testTag("growth_signal_correction_input"),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCorrectSignal(signal.id, correctionText)
                        correcting = null
                    },
                    modifier = Modifier.testTag("growth_signal_correction_save"),
                ) { Text("保存纠正") }
            },
            dismissButton = {
                TextButton(onClick = { correcting = null }) { Text("取消") }
            },
        )
    }
    deleting?.let { signal ->
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("growth_signal_delete_dialog"),
            onDismissRequest = { deleting = null },
            title = { Text("删除这条线索？") },
            text = { Text("删除后不会进入未来的 AI 上下文；日记本身不变。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSignal(signal.id)
                        deleting = null
                    },
                    modifier = Modifier.testTag("growth_signal_delete_confirm"),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}
