package com.mengyalife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.ui.components.DemoTag
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.PageHeader
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.StatusPill

/**
 * 日记：列表、新建入口、内容摘要，以及「未来 AI 提取信息」的展示区域。
 *
 * 新建的日记只存在于本次运行的内存中（不落库、不落文件）。
 */
@Composable
fun DiaryScreen(
    listState: LazyListState,
    draft: String,
    onDraftChange: (String) -> Unit,
    entries: List<DemoData.DiaryEntry>,
    editorOpen: Boolean,
    onToggleEditor: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(
                title = "把今天留下来",
                subtitle = "记录真实发生的细节，留给以后回看",
            )
        }

        item {
            SectionCard(
                title = "今天",
                subtitle = "写下此刻最想留下的一句话",
                trailing = {
                    if (!editorOpen) {
                        Button(
                            onClick = onToggleEditor,
                            modifier = Modifier.testTag("diary_new"),
                        ) {
                            Text("写一条")
                        }
                    }
                },
            ) {
                if (editorOpen) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diary_input"),
                        placeholder = { Text("今天发生了什么？") },
                        minLines = 3,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onSave,
                            enabled = draft.isNotBlank(),
                            modifier = Modifier.testTag("diary_save"),
                        ) {
                            Text("保存")
                        }
                        TextButton(onClick = onToggleEditor) {
                            Text("取消")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    InlineNote("仅保存在本次运行的内存中，重启后会消失。")
                } else {
                    InlineNote("点右上角「写一条」开始记录。本轮不做持久化。")
                }
            }
        }

        item {
            SectionCard(
                title = "AI 提取（预览）",
                subtitle = "未来会从日记中提取情绪、事项与目标线索",
                trailing = { StatusPill(text = "后续接入") },
            ) {
                DemoData.diaryAiExtractExample.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                InlineNote("以上为示例文案，当前版本不会调用任何模型。")
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
                DemoTag()
            }
        }

        items(entries.size) { index ->
            DiaryEntryCard(entry = entries[index])
        }

        if (entries.isEmpty()) {
            item {
                SectionCard(title = "还没有记录") {
                    InlineNote("写下的第一条会出现在这里。")
                }
            }
        }
    }
}

@Composable
private fun DiaryEntryCard(entry: DemoData.DiaryEntry) {
    SectionCard(
        title = entry.title,
        subtitle = entry.date,
        trailing = { if (entry.isNew) StatusPill(text = "本次新建") },
    ) {
        Text(
            text = entry.excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.tags.forEach { tag ->
                StatusPill(text = "#$tag")
            }
        }
    }
}
