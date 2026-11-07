package com.mengyalife.app.ui.weekly

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mengyalife.app.domain.weekly.WeeklyFact
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.theme.MorrowSpacing
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
    val colors = MorrowTheme.colors
    val review = state.review
    val ai = state.aiReview
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = colors.canvas,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("weekly_review_screen"),
            contentPadding = ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = "用本机记录整理这一周。可选 AI 增强只在授权后发送事实，" +
                            "不会改写你的目标或日记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MorrowSpacing.CardRadius)),
                    shape = RoundedCornerShape(MorrowSpacing.CardRadius),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.hairline),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = review?.periodKey ?: "本周回顾",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (review == null) {
                                        "还没有生成这一周的回顾"
                                    } else {
                                        "${Timestamps.formatDate(review.periodStart)} – " +
                                            "${Timestamps.formatDate(review.periodEnd)} · " +
                                            "生成于 ${Timestamps.formatDateTime(review.generatedAt)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            if (review == null) {
                                StatusPill(text = "未生成")
                            }
                        }

                        if (review == null) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "回顾只读取你的目标进展、日记线索和成长记忆，不会联网，也不会补写内容。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            Spacer(Modifier.height(16.dp))
                            MorrowPrimaryButton(
                                text = if (state.generating) "本地整理中…" else "生成本周回顾",
                                onClick = onGenerate,
                                enabled = !state.generating,
                                loading = state.generating,
                                testTag = "weekly_review_generate",
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                            WeeklyBlock(
                                title = "本周变化",
                                facts = review.sections.changes,
                                onOpenGoal = onOpenGoal,
                            )
                            BlockDivider()
                            WeeklyBlock(
                                title = "值得肯定",
                                facts = review.sections.wins,
                                onOpenGoal = onOpenGoal,
                            )
                            BlockDivider()
                            WeeklyBlock(
                                title = "值得关注",
                                facts = review.sections.attention,
                                onOpenGoal = onOpenGoal,
                            )
                            if (review.sections.nextSteps.isNotEmpty()) {
                                BlockDivider()
                                WeeklyBlock(
                                    title = "建议下一步",
                                    facts = review.sections.nextSteps,
                                    onOpenGoal = onOpenGoal,
                                )
                            }
                        }

                        state.error?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = it,
                                color = colors.danger,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (review != null) {
                if (ai == null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MorrowTertiaryButton(
                                text = "尝试 AI 增强",
                                onClick = onGenerateAi,
                                enabled = !state.generating,
                                showArrow = true,
                                testTag = "weekly_generate_ai",
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "每周最多一次",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                            Spacer(Modifier.weight(1f))
                            MorrowTertiaryButton(
                                text = if (state.generating) "整理中…" else "重新整理",
                                onClick = onGenerate,
                                enabled = !state.generating,
                                showArrow = true,
                                testTag = "weekly_review_refresh",
                            )
                        }
                    }
                } else {
                    item {
                        AiReviewBlock(
                            status = ai.status,
                            summary = ai.summary,
                            highlights = ai.highlights.map { it.title to it.body },
                            suggestion = ai.suggestion,
                            error = ai.error,
                            meta = "类别：${ai.categories} · ${Timestamps.formatDateTime(ai.generatedAt)}",
                        )
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            MorrowTertiaryButton(
                                text = if (state.generating) "整理中…" else "重新整理",
                                onClick = onGenerate,
                                enabled = !state.generating,
                                showArrow = true,
                                testTag = "weekly_review_refresh_ai",
                            )
                        }
                    }
                }

                if (review.sections.nextSteps.isNotEmpty()) {
                    item {
                        MorrowPrimaryButton(
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

            item {
                InlineNote(
                    "回顾由本机记录生成；AI 增强是可选动作，只在你明确触发且授权有效时发生。",
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun WeeklyBlock(
    title: String,
    facts: List<WeeklyFact>,
    onOpenGoal: (Long) -> Unit,
) {
    val colors = MorrowTheme.colors
    Column(modifier = Modifier.padding(vertical = 13.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        if (facts.isEmpty()) {
            Text(
                text = "这一项暂时没有可核验的内容。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        } else {
            facts.forEachIndexed { index, fact ->
                if (index > 0) Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(4.dp)
                            .background(colors.accent, CircleShape),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            text = fact.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )

                    }
                }
            }
            val goalSource = facts
                .flatMap { it.sources }
                .filter { it.type == "GOAL" }
                .distinctBy { it.recordId }
                .firstOrNull()
            val sourceId = goalSource?.recordId?.toLongOrNull()
            if (sourceId != null) {
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenGoal(sourceId) }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag("weekly_source_goal_${goalSource.recordId}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "查看相关目标：${goalSource.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.forest,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    MorrowIcon(
                        kind = MorrowIconKind.ARROW_RIGHT,
                        tint = colors.forest,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MorrowTheme.colors.hairline),
    )
}

@Composable
private fun AiReviewBlock(
    status: String,
    summary: String,
    highlights: List<Pair<String, String>>,
    suggestion: String,
    error: String?,
    meta: String,
) {
    val colors = MorrowTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.nested)
            .padding(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "AI 增强",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(text = status)
        }
        Spacer(Modifier.height(7.dp))
        if (status == "READY") {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            highlights.take(3).forEach { (title, body) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (suggestion.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "建议：$suggestion",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        } else {
            Text(
                text = error ?: "AI 增强尚未完成；本地回顾仍然有效。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
        }
    }
}
