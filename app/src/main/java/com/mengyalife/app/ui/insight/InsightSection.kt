package com.mengyalife.app.ui.insight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.mengyalife.app.domain.insight.InsightReference
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowPrimaryButton
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
    onDiscuss: (InsightReference) -> Unit,
) {
    var detail by remember { mutableStateOf<GrowthInsight?>(null) }
    val report = state.report
    val current = state.hasReport
    LaunchedEffect(report?.id) {
        if (report?.insights?.none { it.id == detail?.id } != false) detail = null
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
                if (report != null) "来源或观察窗口已变化。上次依据保留供回看，旧结论不再作为当前建议。"
                else "还没有生成洞察。可以先记录几条目标进展或日记；生成时会先做可核验的规则分析。",
            )
            report?.let { historical ->
                distinctInsightCards(historical.insights).take(3).forEachIndexed { index, insight ->
                    InsightCard(insight, index) { detail = insight }
                }
            }
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
        } else if (report != null && report.facts.any { it.id == "insufficient" }) {
            InlineNote("数据还不足，暂不展示行动建议为确定结论。请先继续记录真实进展。")
            report.facts.filter { it.id == "insufficient" }.forEach { FactLine(it) }
        } else if (report != null && report.insights.isEmpty()) {
            InlineNote("这次没有形成可靠的洞察；可以补充目标、进展或日记后再试。")
        } else if (report != null) {
            distinctInsightCards(report.insights).take(3).forEachIndexed { index, insight ->
                InsightCard(insight, index) { detail = insight }
            }
            Text(
                "打开结论查看解释、依据与来源。建议需要讨论后由你决定。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        MorrowPrimaryButton(
            text = if (state.loading) "正在重新评估…" else if (state.error != null || report != null) "重新评估" else "生成洞察",
            onClick = onGenerate,
            enabled = !state.loading,
            loading = state.loading,
            testTag = "insight_generate",
        )
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
            isCurrent = current,
            loading = state.loading,
            staleReason = state.error,
            onReevaluate = onGenerate,
            onDiscuss = { reference -> onDiscuss(reference); detail = null },
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
            "查看解释与依据",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FactLine(fact: InsightFact) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            if (fact.isInference) "AI 推测 · " else "规则事实 · ",
            style = MaterialTheme.typography.labelSmall,
            color = if (fact.isInference) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary,
        )
        Text(
            fact.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightDetailDialog(
    insight: GrowthInsight,
    report: InsightReport?,
    onDismiss: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenTrends: () -> Unit,
    onDiscuss: (InsightReference) -> Unit,
    onReevaluate: () -> Unit,
    isCurrent: Boolean,
    loading: Boolean = false,
    staleReason: String? = null,
) {
    var evidenceExpanded by remember(insight.id) { mutableStateOf(false) }
    val hasEvidence = insight.factIds.isNotEmpty() && insight.factIds.all { id -> report?.facts?.any { it.id == id } == true }
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
                if (!isCurrent || !hasEvidence) {
                    Spacer(Modifier.height(8.dp))
                    InlineNote(staleReason ?: if (!hasEvidence) "关联依据不完整，请重新评估后再讨论行动。" else "来源或观察窗口已变化；以下是上次的依据，不代表当前结论。")
                    MorrowPrimaryButton(
                        text = if (loading) "正在重新评估…" else "重新评估",
                        onClick = onReevaluate,
                        enabled = !loading,
                        loading = loading,
                        testTag = "insight_retry",
                    )
                }
                TextButton(
                    onClick = { evidenceExpanded = !evidenceExpanded },
                    modifier = Modifier.testTag("insight_evidence_toggle"),
                ) { Text(if (evidenceExpanded) "收起依据与来源" else "展开依据与来源") }
                if (evidenceExpanded && report != null) {
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
                    val relevant = report.facts.filter { it.id in insight.factIds }.distinctBy { it.text }
                    if (relevant.isEmpty()) {
                        InlineNote("这条结论没有可核验的关联依据，请重新评估；不以其他事实代替。")
                    } else {
                        val additional = additionalInsightFacts(insight.body, relevant)
                        val included = relevant.size - additional.size
                        if (included > 0) {
                            Text(
                                "上述解释已引用 $included 条记录事实，来源保留如下。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        additional.forEach { FactLine(it) }
                    }
                }
                if (evidenceExpanded && insight.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("来源", style = MaterialTheme.typography.labelMedium)
                    insight.sources.distinctBy { it.type to it.recordId }.forEach {
                        Text("• ${friendlyInsightSource(it)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val goalId = insight.sources.firstOrNull { it.type == "GOAL" }?.recordId?.toLongOrNull()
                            onDismiss()
                            if (isCurrent && goalId != null) onOpenGoal(goalId) else onOpenGoals()
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
                if (isCurrent && hasEvidence && insight.type == InsightType.SUGGESTION && report != null) {
                    MorrowPrimaryButton(
                        text = "和顾问讨论",
                        onClick = { onDiscuss(InsightReference(report.id, report.dataVersion, insight.id)) },
                        testTag = "insight_discuss",
                    )
                    Text(
                        "确认行动前会先讨论；这里不会直接修改目标或计划。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
