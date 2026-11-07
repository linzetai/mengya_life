package com.mengyalife.app.ui.insight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightFact
import com.mengyalife.app.domain.insight.InsightReport
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.domain.insight.InsightType
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.util.Timestamps

@Composable
fun InsightSection(
    state: InsightUiState,
    onGenerate: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenGoal: (Long) -> Unit = {},
    onOpenTrends: () -> Unit,
    onDiscuss: (String) -> Unit,
    onDraft: (String) -> Unit,
) {
    var detail by remember { mutableStateOf<GrowthInsight?>(null) }
    val report = state.report
    val current = state.hasReport
    LaunchedEffect(state.currentVersion, state.hasReport) {
        if (!state.hasReport) detail = null
    }

    SectionCard(
        title = "最近值得关注",
        subtitle = "基于你的目标、进展与成长记忆的规则分析；AI 只做解释与建议",
        modifier = Modifier.testTag("insight_section"),
        trailing = {
            if (state.loading) StatusPill(text = "生成中")
            else if (current && report?.status == InsightReportStatus.READY) StatusPill(text = "已更新")
            else if (current && report?.status == InsightReportStatus.FAILED) StatusPill(text = "AI 未生成")
            else StatusPill(text = "待生成")
        },
    ) {
        if (!current) {
            InlineNote(
                if (report != null) "数据已变化，旧洞察不再展示。请重新生成。"
                else "还没有生成洞察。可以先记录几条目标进展或日记；生成时会先做可核验的规则分析。",
            )
        } else if (report?.status == InsightReportStatus.FAILED) {
            Text(
                "AI 洞察未生成：${report.error ?: "未知错误"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("insight_error"),
            )
            Spacer(Modifier.height(6.dp))
            Text("以下仍是你可核验的规则分析，不是 AI 洞察。", style = MaterialTheme.typography.labelSmall)
            report.facts.take(4).forEach { fact -> FactLine(fact) }
        } else if (report != null && report.insights.isEmpty()) {
            InlineNote("这次没有形成可靠的洞察；可以补充目标、进展或日记后再试。")
        } else if (report != null) {
            report.insights.take(3).forEachIndexed { index, insight ->
                InsightCard(insight, index) { detail = insight }
            }
            report.facts.firstOrNull()?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "判断依据（${report.facts.size} 条规则事实）：${it.text.take(90)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onGenerate,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().testTag("insight_generate"),
        ) {
            Text(if (state.loading) "正在分析…" else if (current) "生成 / 更新洞察" else "生成洞察")
        }
        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    detail?.let { insight ->
        InsightDetailDialog(
            insight = insight,
            report = report,
            onDismiss = { detail = null },
            onOpenGoals = onOpenGoals,
            onOpenGoal = onOpenGoal,
            onOpenTrends = onOpenTrends,
            onDiscuss = { onDiscuss(insight.title + "：" + insight.body); detail = null },
            onDraft = { onDraft(insight.title + "：" + insight.body); detail = null },
        )
    }
}

@Composable
private fun InsightCard(insight: GrowthInsight, index: Int, onClick: () -> Unit) {
    val color = when (insight.type) {
        InsightType.POSITIVE -> MaterialTheme.colorScheme.primary
        InsightType.RISK -> MaterialTheme.colorScheme.error
        InsightType.SUGGESTION -> MaterialTheme.colorScheme.tertiary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick)
            .testTag("insight_card_$index"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(insight.type.label, style = MaterialTheme.typography.labelMedium, color = color)
            Spacer(Modifier.weight(1f))
            Text(
                if (insight.isInference) "推测" else "事实",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(insight.title, style = MaterialTheme.typography.titleSmall)
        Text(
            insight.body.take(160),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FactLine(fact: InsightFact) {
    Text(
        "• ${fact.text}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
fun InsightDetailDialog(
    insight: GrowthInsight,
    report: InsightReport?,
    onDismiss: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenTrends: () -> Unit,
    onDiscuss: () -> Unit,
    onDraft: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag("insight_detail"),
        onDismissRequest = onDismiss,
        title = { Text(insight.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${insight.type.label} · ${if (insight.isInference) "推测" else "事实"}" +
                        (insight.confidence?.let { " · 置信度 ${(it * 100).toInt()}%" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(insight.body, style = MaterialTheme.typography.bodyMedium)
                if (report != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "数据区间：${Timestamps.formatDate(report.periodStart)} – ${Timestamps.formatDate(report.periodEnd)}" +
                            " · 生成于 ${Timestamps.formatDateTime(report.generatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "为什么这样判断：",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val relevant = report.facts.filter { it.id in insight.factIds }
                        .ifEmpty { report.facts }
                    relevant.forEach { FactLine(it) }
                }
                if (insight.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("来源", style = MaterialTheme.typography.labelMedium)
                    insight.sources.forEach {
                        Text("• ${it.type} · ${it.label} (#${it.recordId})", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val goalId = insight.sources.firstOrNull { it.type == "GOAL" }?.recordId?.toLongOrNull()
                            onDismiss()
                            if (goalId != null) onOpenGoal(goalId) else onOpenGoals()
                        },
                        modifier = Modifier.testTag("insight_open_goals"),
                    ) {
                        Text("目标")
                    }
                    TextButton(
                        onClick = { onDismiss(); onOpenTrends() },
                        modifier = Modifier.testTag("insight_open_trends"),
                    ) {
                        Text("趋势")
                    }
                }
                if (insight.type == InsightType.SUGGESTION) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDiscuss, modifier = Modifier.testTag("insight_discuss")) {
                            Text("和顾问讨论")
                        }
                        TextButton(onClick = onDraft, modifier = Modifier.testTag("insight_draft")) {
                            Text("整理为目标草案")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("insight_detail_close")) {
                Text("关闭")
            }
        },
    )
}
