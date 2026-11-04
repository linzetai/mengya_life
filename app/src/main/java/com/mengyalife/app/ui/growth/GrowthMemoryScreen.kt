package com.mengyalife.app.ui.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.growth.MemoryDetail
import com.mengyalife.app.domain.growth.GrowthMemory
import com.mengyalife.app.domain.growth.GrowthMemoryStatus
import com.mengyalife.app.util.Timestamps

@Composable
fun GrowthMemoryScreen(
    state: GrowthMemoryUiState,
    onAction: (GrowthMemoryAction) -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag("growth_memory_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        val selected = state.selected
        if (selected != null) {
            MemoryDetailView(selected, state.error, onAction, onBack)
        } else {
            MemoryListView(state, onAction, onBack)
        }
    }
}

@Composable
private fun MemoryListView(
    state: GrowthMemoryUiState,
    onAction: (GrowthMemoryAction) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("成长记忆", style = MaterialTheme.typography.titleLarge)
                Text(
                    "由日记线索整理；每条都能追溯来源，可纠正或删除。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onBack, modifier = Modifier.testTag("growth_memory_back")) {
                Text("返回")
            }
        }
        if (state.error != null) {
            Text(
                state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 18.dp).testTag("growth_memory_error"),
            )
        }
        if (state.memories.isEmpty()) {
            Text(
                "还没有形成成长记忆。至少需要几条来自日记的线索；提取完成后会自动整理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(18.dp).testTag("growth_memory_empty"),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("growth_memory_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.memories, key = { it.id }) { memory ->
                MemoryCard(memory) { onAction(GrowthMemoryAction.Open(memory.id)) }
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: GrowthMemory, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("growth_memory_${memory.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(memory.theme, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                statusLabel(memory),
                style = MaterialTheme.typography.labelSmall,
                color = if (memory.status == GrowthMemoryStatus.STALE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(memory.content, style = MaterialTheme.typography.bodySmall, maxLines = 4)
        memory.recentChange?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "来源：${memory.sourceDiaryIds.size} 篇日记 · ${memory.signalIds.size} 条线索 · 版本 ${memory.version}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MemoryDetailView(
    detail: MemoryDetail,
    error: String?,
    onAction: (GrowthMemoryAction) -> Unit,
    onBack: () -> Unit,
) {
    val memory = detail.memory
    var correcting by remember { mutableStateOf(false) }
    var correction by remember { mutableStateOf(memory.content) }
    var deleting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(memory.theme, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { onAction(GrowthMemoryAction.CloseDetail) },
                modifier = Modifier.testTag("growth_memory_detail_back"),
            ) { Text("返回") }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Text(
                statusLabel(memory) + " · 版本 ${memory.version}" +
                    if (memory.isInference) " · 含推测" else " · 事实",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(memory.content, style = MaterialTheme.typography.bodyLarge)
            memory.recentChange?.let {
                Spacer(Modifier.height(8.dp))
                Text("最近变化：$it", style = MaterialTheme.typography.bodySmall)
            }
            if (memory.insufficient) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "信息不足：当前线索覆盖的日记较少，顾问会表达不确定性。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "创建 ${Timestamps.formatDateTime(memory.createdAt)} · 更新 ${Timestamps.formatDateTime(memory.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Text("来源线索", style = MaterialTheme.typography.titleSmall)
            detail.signals.forEach { signal ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .testTag("growth_memory_signal_${signal.id}"),
                ) {
                    Text(
                        "${signal.category} · " + when {
                            signal.origin == "USER" -> "用户纠正"
                            signal.isInference -> "推测"
                            else -> "事实"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(signal.content, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "原文：${signal.evidence.take(160)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("来源日记", style = MaterialTheme.typography.titleSmall)
            detail.journals.forEach { journal ->
                Text(
                    "• " + (journal.title?.takeIf { it.isNotBlank() } ?: "无标题") +
                        " · " + Timestamps.formatDateTime(journal.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("growth_memory_diary_${journal.id}"),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (memory.status == GrowthMemoryStatus.DELETED) {
                Text("该记忆已删除", style = MaterialTheme.typography.bodySmall)
            } else {
                TextButton(
                    onClick = { correction = memory.content; correcting = true },
                    modifier = Modifier.testTag("growth_memory_correct"),
                ) { Text("纠正") }
                TextButton(
                    onClick = { deleting = true },
                    modifier = Modifier.testTag("growth_memory_delete"),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (correcting) {
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("growth_memory_correct_dialog"),
            onDismissRequest = { correcting = false },
            title = { Text("纠正成长记忆") },
            text = {
                Column {
                    Text("纠正后的内容优先于 AI 推测；来源与证据仍保留。")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correction,
                        onValueChange = { correction = it },
                        modifier = Modifier.fillMaxWidth().testTag("growth_memory_correction_input"),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(GrowthMemoryAction.Correct(memory.id, correction))
                        correcting = false
                    },
                    modifier = Modifier.testTag("growth_memory_correction_save"),
                ) { Text("保存纠正") }
            },
            dismissButton = { TextButton(onClick = { correcting = false }) { Text("取消") } },
        )
    }
    if (deleting) {
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("growth_memory_delete_dialog"),
            onDismissRequest = { deleting = false },
            title = { Text("删除这条成长记忆？") },
            text = { Text("删除后顾问不再使用；重新分析旧日记不会自动复活同一版本。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(GrowthMemoryAction.Delete(memory.id))
                        deleting = false
                    },
                    modifier = Modifier.testTag("growth_memory_delete_confirm"),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
    }
}

private fun statusLabel(memory: GrowthMemory): String = when {
    memory.userCorrected || memory.status == GrowthMemoryStatus.CORRECTED -> "用户纠正"
    memory.status == GrowthMemoryStatus.STALE -> "已过时"
    memory.status == GrowthMemoryStatus.DELETED -> "已删除"
    memory.insufficient -> "信息不足"
    else -> "有效"
}
