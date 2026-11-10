package com.mengyalife.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.domain.JournalRules
import com.mengyalife.app.domain.growth.DiaryAnalysisStatus
import com.mengyalife.app.domain.growth.GrowthSignal
import com.mengyalife.app.ui.components.GentleEmptyCard
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.rememberToday
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowTextField
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.journal.DiaryAction
import com.mengyalife.app.ui.journal.DiaryUiState
import com.mengyalife.app.ui.journal.JournalEditor
import com.mengyalife.app.ui.journal.JournalSheet
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.util.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy年M月", Locale.getDefault())
private val WEEKDAY_SHORT = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 日记页：安静真实的书写空间。
 *
 * 数据来自 Room，不存在演示记录；写入、编辑、删除、AI 线索的确认/纠正
 * 继续走原有 JournalViewModel 与安全边界。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiaryScreen(
    listState: LazyListState,
    state: DiaryUiState,
    onAction: (DiaryAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    val today = rememberToday()
    val grouped = remember(state.entries) {
        state.entries
            .sortedByDescending { it.createdAt }
            .groupBy { entry ->
                Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    .let { YearMonth.of(it.year, it.monthValue) }
            }
            .toList()
            .sortedByDescending { it.first }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("journal_list"),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        stickyHeader {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorrowTheme.colors.canvas),
            ) {
                MorrowTopBar(label = "日记", onSettingsClick = onOpenSettings)
            }
        }
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "写下今天，\n留给未来的自己。",
                    style = MorrowType.PageTitle,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${today.year}年${today.monthValue}月${today.dayOfMonth}日 · " +
                        WEEKDAY_SHORT[today.dayOfWeek.value - 1] + " · 只保存在这台设备上",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }

        item {
            NewEntryCard(onClick = { onAction(DiaryAction.NewEntry) })
        }

        item {
            InlineNote("日记只保存在本机；只有在单篇详情里主动触发、且你已授权时，才会请求 AI 线索。")
        }

        when {
            state.readError != null -> item {
                GentleEmptyCard(
                    title = "暂时读不到本地日记",
                    body = state.readError,
                )
            }

            state.loading -> item {
                Text(
                    text = "正在读取你的记录…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            state.entries.isEmpty() -> item {
                GentleEmptyCard(
                    title = "还没有记录",
                    body = "写下的第一条会出现在这里。这里不会预先放任何演示内容，" +
                        "也不必写得完整——一句话就够了。",
                    actionLabel = "写第一篇",
                    onAction = { onAction(DiaryAction.NewEntry) },
                )
            }

            else -> {
                grouped.forEach { (month, entries) ->
                    item(key = "month_${month}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = MONTH_LABEL.format(month),
                                style = MorrowType.SectionTitle,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${entries.size} 篇",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    items(entries.size, key = { entries[it].id }) { index ->
                        JournalEntryCard(
                            entry = entries[index],
                            onClick = { onAction(DiaryAction.Open(entries[index])) },
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
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
            containerColor = colors.card,
            title = { Text("删除这条日记？", style = MorrowType.CardTitle) },
            text = {
                Column {
                    Text("删除后无法恢复。将会删除：\n\n" + JournalRules.excerpt(pending.body, 40))
                    if (state.deleteError != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.deleteError,
                            color = colors.danger,
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
                    Text("删除", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(DiaryAction.DismissDelete) },
                    modifier = Modifier.testTag("journal_delete_cancel"),
                ) {
                    Text("取消", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun NewEntryCard(onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 80.dp)
            .clip(RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .testTag("diary_new"),
        shape = RoundedCornerShape(17.dp),
        color = colors.nested,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.card),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(
                    kind = MorrowIconKind.PENCIL,
                    tint = colors.forest,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "写一篇日记",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "此刻最想留下的一句话",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            MorrowIcon(
                kind = MorrowIconKind.ARROW_RIGHT,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntryEntity,
    onClick: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val edited = entry.updatedAt > entry.createdAt
    val date = remember(entry.createdAt) {
        Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("journal_entry_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column(
                modifier = Modifier.width(44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${date.dayOfMonth}",
                    style = MorrowType.DateNumber,
                    color = colors.textPrimary,
                )
                Text(
                    text = WEEKDAY_SHORT[date.dayOfWeek.value - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(colors.hairline),
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title?.takeIf { it.isNotBlank() } ?: "无标题",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (edited) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "已编辑",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = JournalRules.excerpt(entry.body, 72),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    val colors = MorrowTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        scrimColor = colors.textPrimary.copy(alpha = 0.22f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(colors.hairline, CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 4.dp)
                .semantics { testTagsAsResourceId = true },
        ) {
            Text(
                text = if (isCreating) "写下今天" else "编辑这一篇",
                style = MorrowType.SheetTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "不必完整，也不必正确。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(18.dp))

            MorrowTextField(
                value = editor.title,
                onValueChange = onTitleChange,
                placeholder = "标题（可选）",
                singleLine = true,
                enabled = !editor.saving,
                testTag = "journal_title_input",
            )
            Spacer(Modifier.height(12.dp))

            MorrowTextField(
                value = editor.body,
                onValueChange = onBodyChange,
                placeholder = "今天发生了什么？你注意到了什么？",
                minLines = 7,
                maxLines = 16,
                enabled = !editor.saving,
                isError = editor.bodyError != null,
                supportingText = editor.bodyError,
                supportingTestTag = "journal_body_error",
                testTag = "journal_body_input",
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MorrowTertiaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    enabled = !editor.saving,
                )
                Spacer(Modifier.size(8.dp))
                Box(modifier = Modifier.width(126.dp)) {
                    MorrowPrimaryButton(
                        text = if (editor.saving) "保存中…" else "保存",
                        onClick = onSave,
                        enabled = !editor.saving,
                        testTag = "journal_save",
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
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
    val colors = MorrowTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        scrimColor = colors.textPrimary.copy(alpha = 0.22f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(colors.hairline, CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 4.dp)
                .semantics { testTagsAsResourceId = true },
        ) {
            Text(
                text = entry.title?.takeIf { it.isNotBlank() } ?: "无标题",
                style = MorrowType.SheetTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "创建于 " + Timestamps.formatDateTime(entry.createdAt) +
                    if (entry.updatedAt > entry.createdAt) {
                        " · 更新于 " + Timestamps.formatDateTime(entry.updatedAt)
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = colors.hairline)
            Spacer(Modifier.height(18.dp))
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(24.dp))
            AiDiscoverySection(
                entry = entry,
                signals = signals,
                onRetry = onRetryAnalysis,
                onDeleteSignal = onDeleteSignal,
                onCorrectSignal = onCorrectSignal,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("journal_edit"),
                ) {
                    Text("编辑", color = colors.forest)
                }
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("journal_delete"),
                ) {
                    Text("删除", color = colors.danger)
                }
            }
            Spacer(Modifier.height(24.dp))
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
    val colors = MorrowTheme.colors
    var correcting by remember { mutableStateOf<GrowthSignal?>(null) }
    var deleting by remember { mutableStateOf<GrowthSignal?>(null) }
    var correctionText by remember { mutableStateOf("") }

    HorizontalDivider(color = colors.hairline)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("从记录里发现线索", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        Spacer(Modifier.size(8.dp))
        StatusPill(text = DiaryAnalysisStatus.label(entry.analysisStatus))
    }
    Spacer(Modifier.height(5.dp))
    Text(
        "仅在你配置 AI 并授权「日记」类别时发送；结果只作为线索，不会修改目标或进展。",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
    )
    when (entry.analysisStatus) {
        DiaryAnalysisStatus.PENDING -> {
            Spacer(Modifier.height(8.dp))
            Text("待分析：保存后会在允许时分析。", style = MaterialTheme.typography.bodySmall)
        }
        DiaryAnalysisStatus.ANALYZING -> {
            Spacer(Modifier.height(8.dp))
            Text("正在分析这篇日记…", style = MaterialTheme.typography.bodySmall)
        }
        DiaryAnalysisStatus.FAILED -> {
            Spacer(Modifier.height(8.dp))
            Text(
                "分析失败：" + (entry.analysisError ?: "未知错误"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.testTag("journal_analysis_error"),
            )
            TextButton(onClick = onRetry, modifier = Modifier.testTag("journal_analysis_retry")) {
                Text("重试分析", color = colors.forest)
            }
        }
    }
    if (entry.analysisStatus == DiaryAnalysisStatus.COMPLETED && signals.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("这篇日记没有提取到可保留的成长线索。", style = MaterialTheme.typography.bodySmall)
    }
    signals.forEach { signal ->
        Spacer(Modifier.height(9.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.nested, RoundedCornerShape(12.dp))
                .padding(12.dp)
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
                    color = if (signal.isInference) colors.textSecondary else colors.forest,
                )
            }
            signal.confidence?.let {
                Text(
                    text = "可信度 ${(it * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(signal.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "原文：" + signal.evidence.take(180),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Row {
                TextButton(
                    onClick = {
                        correcting = signal
                        correctionText = signal.content
                    },
                    modifier = Modifier.testTag("growth_signal_correct_${signal.id}"),
                ) { Text("纠正", color = colors.forest) }
                TextButton(
                    onClick = { deleting = signal },
                    modifier = Modifier.testTag("growth_signal_delete_${signal.id}"),
                ) { Text("删除", color = colors.danger) }
            }
        }
    }

    correcting?.let { signal ->
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("growth_signal_correction_dialog"),
            onDismissRequest = { correcting = null },
            containerColor = colors.card,
            title = { Text("纠正这条线索", style = MorrowType.CardTitle) },
            text = {
                Column {
                    Text("纠正后会优先于旧 AI 推测，不会修改日记或目标。")
                    Spacer(Modifier.height(8.dp))
                    MorrowTextField(
                        value = correctionText,
                        onValueChange = { correctionText = it },
                        placeholder = "你更准确的表述",
                        minLines = 2,
                        testTag = "growth_signal_correction_input",
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
                ) { Text("保存纠正", color = colors.forest) }
            },
            dismissButton = {
                TextButton(onClick = { correcting = null }) { Text("取消", color = colors.textSecondary) }
            },
        )
    }
    deleting?.let { signal ->
        AlertDialog(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("growth_signal_delete_dialog"),
            onDismissRequest = { deleting = null },
            containerColor = colors.card,
            title = { Text("删除这条线索？", style = MorrowType.CardTitle) },
            text = { Text("删除后不会进入未来的 AI 上下文；日记本身不变。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSignal(signal.id)
                        deleting = null
                    },
                    modifier = Modifier.testTag("growth_signal_delete_confirm"),
                ) { Text("删除", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消", color = colors.textSecondary) }
            },
        )
    }
}
