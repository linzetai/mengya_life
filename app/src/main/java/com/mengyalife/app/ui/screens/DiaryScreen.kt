package com.mengyalife.app.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.domain.JournalRules
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
            onEdit = { onAction(DiaryAction.Edit(sheet.entry)) },
            onDelete = { onAction(DiaryAction.RequestDelete(sheet.entry)) },
            onDismiss = { onAction(DiaryAction.CloseSheet) },
        )
    }

    val pending = state.pendingDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { onAction(DiaryAction.DismissDelete) },
            title = { Text("删除这条日记？") },
            text = {
                Text("删除后无法恢复。将会删除：\n\n" + JournalRules.excerpt(pending.body, 40))
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
