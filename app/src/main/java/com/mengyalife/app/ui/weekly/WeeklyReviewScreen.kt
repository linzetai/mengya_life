package com.mengyalife.app.ui.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.domain.weekly.WeeklyFact
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowQuietButton
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.util.Timestamps

@Composable
fun WeeklyReviewScreen(
    state: WeeklyReviewUiState,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    onGenerateAi: () -> Unit = {},
    onOpenGoal: (Long) -> Unit,
    onDiscuss: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MorrowTheme.colors.canvas,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("weekly_review_screen"),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    MorrowTopBar(
                        label = "一周回顾",
                        onSettingsClick = onOpenSettings,
                        onBack = onBack,
                        backTestTag = "weekly_review_back",
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "这一周，\n留下了什么。",
                        style = MorrowType.PageTitle,
                        color = MorrowTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本地回顾不发送；可选 AI 增强会按当前授权发送事实；「和顾问聊聊」进入现有顾问授权流程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MorrowTheme.colors.textSecondary,
                    )
                }
            }
            item {
                SectionCard(title = "本周回顾", subtitle = "轻量、可核验；数据不足会直接说明") {
                    val review = state.review
                    if (review == null) {
                        InlineNote("还没有生成回顾。打开下方按钮，用本机记录整理这一周。")
                    } else {
                        Text(
                            "周期 ${review.periodKey} · ${Timestamps.formatDate(review.periodStart)} – ${Timestamps.formatDate(review.periodEnd)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "生成于 ${Timestamps.formatDateTime(review.generatedAt)} · v${review.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MorrowPrimaryButton(
                        text = if (state.generating) "本地整理中…" else "生成本周回顾",
                        onClick = onGenerate,
                        enabled = !state.generating,
                        testTag = "weekly_review_generate",
                    )
                    state.error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val ai = state.aiReview
            if (ai != null) {
                item {
                    SectionCard(title = "AI 增强（可选）", subtitle = "只解释本轮规则事实；状态：${ai.status}") {
                        if (ai.status == "READY") {
                            Text(ai.summary, style = MaterialTheme.typography.titleSmall)
                            ai.highlights.take(3).forEach {
                                Text("• ${it.title}：${it.body}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Text("建议：${ai.suggestion}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "类别：${ai.categories} · 生成于 ${Timestamps.formatDateTime(ai.generatedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            Text(ai.error ?: "AI 增强未完成；本地回顾仍然有效。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                val ai = state.aiReview
                if (ai == null) {
                    MorrowQuietButton(
                        text = "尝试 AI 增强（每周最多一次）",
                        onClick = onGenerateAi,
                        enabled = !state.generating,
                        testTag = "weekly_generate_ai",
                    )
                } else if (ai.status == "PENDING" || ai.status == "SENT") {
                    Text("AI 已预留本周额度，正在处理；不会重复请求。", style = MaterialTheme.typography.labelSmall)
                } else if (ai.status == "FAILED" || ai.status == "STALE") {
                    Text("本周 AI 已尝试，额度已用尽；本地回顾仍有效。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
            val review = state.review
            if (review != null) {
                item { WeeklySection("本周变化", review.sections.changes, onOpenGoal) }
                item { WeeklySection("值得肯定", review.sections.wins, onOpenGoal) }
                item { WeeklySection("值得关注", review.sections.attention, onOpenGoal) }
                item { WeeklySection("建议下一步", review.sections.nextSteps, onOpenGoal) }
                if (review.sections.nextSteps.isNotEmpty()) {
                    item {
                        MorrowQuietButton(
                            text = "和顾问聊聊",
                            onClick = {
                                onDiscuss(
                                    "请围绕我的每周成长回顾继续讨论，并帮我把下一步变成可执行的小行动：\n" +
                                        review.sections.nextSteps.joinToString("\n") { "• ${it.text}" },
                                )
                            },
                            testTag = "weekly_review_discuss",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklySection(
    title: String,
    facts: List<WeeklyFact>,
    onOpenGoal: (Long) -> Unit,
) {
    SectionCard(title = title) {
        if (facts.isEmpty()) {
            InlineNote("这一项暂时没有可核验的内容。")
        } else {
            facts.forEach { fact ->
                Text(
                    "• ${fact.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
                fact.sources.filter { it.type == "GOAL" }.forEach { source ->
                    val id = source.recordId.toLongOrNull()
                    if (id != null) {
                        TextButton(
                            onClick = { onOpenGoal(id) },
                            modifier = Modifier.testTag("weekly_source_goal_${source.recordId}"),
                        ) { Text("查看目标：${source.label}") }
                    }
                }
            }
        }
    }
}
