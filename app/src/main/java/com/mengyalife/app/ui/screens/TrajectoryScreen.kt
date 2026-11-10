package com.mengyalife.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.data.repository.GoalTrend
import com.mengyalife.app.data.repository.TrendSnapshot
import com.mengyalife.app.domain.goals.GoalTrends
import com.mengyalife.app.domain.goals.ProgressKind
import com.mengyalife.app.ui.components.GentleEmptyCard
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MetricTrendChart
import com.mengyalife.app.ui.components.MorrowChoicePill
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionCard
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.components.StatusPill
import com.mengyalife.app.ui.components.TrendPointList
import com.mengyalife.app.ui.theme.MorrowSpacing
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.ui.weekly.WeeklyReviewUiState
import com.mengyalife.app.util.MetricFormat
import com.mengyalife.app.util.Timestamps

private enum class TrajectoryRange(val label: String, val days: Long) {
    WEEK("近一周", 7),
    MONTH("近一月", 30),
    QUARTER("近三月", 90),
}

/**
 * 「轨迹」：真实数值趋势 + 可核验的变化与解释入口。
 *
 * 不把不同单位相加，也没有演示曲线；数据不足时展示温柔空态和补录建议。
 * 每周本地回顾在这里作为精选入口，不额外堆很多无解释的图表。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrajectoryScreen(
    listState: LazyListState,
    trends: TrendSnapshot,
    goals: GoalSnapshot,
    entries: List<JournalEntryEntity>,
    weekly: WeeklyReviewUiState,
    onOpenSettings: () -> Unit,
    onOpenWeeklyReview: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenPanorama: () -> Unit,
    onWriteDiary: () -> Unit,
    onOpenDiaryEntry: (JournalEntryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    var range by rememberSaveable { mutableStateOf(TrajectoryRange.MONTH) }
    val now = System.currentTimeMillis()
    val windowMillis = range.days * 24L * 60 * 60 * 1000
    val allTrends = trends.trends
    val filtered = allTrends.mapNotNull { item ->
        val points = item.points.filter { now - it.recordedAt <= windowMillis }
        if (points.isEmpty()) {
            null
        } else {
            GoalTrend(
                goalId = item.goalId,
                goalTitle = item.goalTitle,
                series = item.series.copy(points = points),
            )
        }
    }
    val shownTrends = filtered.take(2)
    val shownNodes = remember(goals.roots) { goals.roots.flatMap { flattenNode(it) } }
    val latestNode = shownNodes
        .filter { (it.lastCheckinAt ?: 0L) > 0L }
        .maxByOrNull { it.lastCheckinAt ?: 0L }
    val weeklyReview = weekly.review

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("trajectory_screen"),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(MorrowSpacing.SectionGap),
    ) {
        stickyHeader {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.canvas),
            ) {
                MorrowTopBar(
                    label = "轨迹",
                    onSettingsClick = onOpenSettings,
                )
            }
        }
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "原来，变化已经发生。",
                    style = MorrowType.PageTitle,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "这里只画你亲手记录过的数值；日记和目标里的文字不会被推断成曲线。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrajectoryRange.entries.forEach { option ->
                        MorrowChoicePill(
                            text = option.label,
                            selected = range == option,
                            onClick = { range = option },
                            modifier = Modifier.testTag("trajectory_range_${option.name.lowercase()}"),
                        )
                    }
                }
            }
        }

        item {
            if (allTrends.isEmpty()) {
                Column(modifier = Modifier.testTag("trend_real_section")) {
                    GentleEmptyCard(
                        title = "还没有可画出的变化",
                        body = "在目标的「记录进展」里填写数值和单位，这里就会出现真实曲线。" +
                            "只有一条记录时只显示一个点，不会补线。",
                        actionLabel = "去看看我的方向",
                        onAction = onOpenPanorama,
                        secondaryLabel = "先写一篇日记",
                        onSecondary = onWriteDiary,
                        modifier = Modifier.testTag("trend_real_empty"),
                    )
                }
            } else {
                SectionCard(
                    title = "你的数值变化",
                    subtitle = "按单位和目标分开绘制，不做单位换算",
                    modifier = Modifier.testTag("trend_real_section"),
                    trailing = { StatusPill(text = "真实记录") },
                ) {
                    if (shownTrends.isEmpty()) {
                        InlineNote(
                            "所选范围（${range.label}）没有新的数值记录；换一个时间范围，或再记录一次进展。",
                        )
                    } else {
                        shownTrends.forEachIndexed { index, trend ->
                            if (index > 0) {
                                Spacer(Modifier.height(20.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.hairline),
                                )
                                Spacer(Modifier.height(18.dp))
                            }
                            TrendBlock(trend = trend, index = index, onOpenGoal = onOpenGoal)
                        }
                        if (allTrends.size > 2) {
                            Spacer(Modifier.height(14.dp))
                            InlineNote("这里展示最近 2 组；另有 ${allTrends.size - 2} 组记录可到目标进展历史查看。")
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.testTag("trajectory_weekly")) {
                SectionHeading(title = "这段时间的变化")
                Spacer(Modifier.height(10.dp))
                ChangesCard(
                    trends = filtered,
                    latestNode = latestNode,
                    entries = entries,
                    rangeLabel = range.label,
                )
                Spacer(Modifier.height(10.dp))
                WeeklyEntryCard(
                    weekly = weekly,
                    onOpen = onOpenWeeklyReview,
                )
            }
        }

        item {
            Column {
                SectionHeading(title = "线索与回溯")
                Spacer(Modifier.height(10.dp))
                EvidenceCard(
                    weekly = weeklyReview,
                    entries = entries,
                    onOpenWeekly = onOpenWeeklyReview,
                    onOpenDiary = onOpenDiaryEntry,
                    onOpenGoal = onOpenGoal,
                    latestNode = latestNode,
                )
            }
        }

        item {
            InlineNote("没有足够的记录时，这里不会生成趋势结论，也不会给出「人生整体评分」。")
        }
    }
}

@Composable
private fun TrendBlock(trend: GoalTrend, index: Int, onOpenGoal: (Long) -> Unit) {
    val colors = MorrowTheme.colors
    val unit = trend.unitLabel.takeIf { it != GoalTrends.UNIT_NONE }
    val isDelta = trend.series.kind == ProgressKind.DELTA
    val kindLabel = ProgressKind.label(trend.series.kind)
    Column(
        modifier = Modifier
            .testTag("trend_block_$index")
            .clickable { onOpenGoal(trend.goalId) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = trend.goalTitle,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = if (trend.unitLabel == GoalTrends.UNIT_NONE) kindLabel else "$kindLabel · ${trend.unitLabel}",
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = MetricFormat.withUnit(trend.latest.value, unit),
                style = MorrowType.CardTitle,
                color = colors.forest,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = (if (isDelta) "最近一次新增 · ${trend.points.size} 条" else "最近一次累计 · ${trend.points.size} 条"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        MetricTrendChart(
            series = trend.series,
            modifier = Modifier.fillMaxWidth(),
            color = colors.accent,
            chartHeight = 150.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isDelta) {
                "增量记录仅表示每次新增，不是累计总额。"
            } else {
                "横轴为真实记录时间，纵轴为累计状态。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        if (trend.points.size == 1) {
            Text(
                text = "只有 1 条数值记录，无法判断趋势；这里只显示该数据点。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.testTag("trend_block_single_$index"),
            )
        }
        Spacer(Modifier.height(8.dp))
        TrendPointList(series = trend.series, maxRows = 3)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "数据来源：你在目标里记录的进展（手动或顾问确认）",
            style = MaterialTheme.typography.labelSmall,
            color = colors.forest,
            modifier = Modifier.testTag("trend_block_source_$index"),
        )
    }
}

@Composable
private fun WeeklyEntryCard(
    weekly: WeeklyReviewUiState,
    onOpen: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val review = weekly.review
    val firstChange = review?.sections?.changes?.firstOrNull() ?: review?.facts?.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MorrowSpacing.CardRadius))
            .background(colors.nested)
            .clickable(onClick = onOpen)
            .testTag("trajectory_weekly_open")
            .padding(18.dp),
    ) {
        Text(
            text = review?.periodKey ?: "本周",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = firstChange?.text ?: "用本机记录，安静地整理这一周。",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = if (review == null) "还没有生成回顾 · 打开看看" else "查看完整回顾",
            style = MaterialTheme.typography.labelMedium,
            color = colors.forest,
        )
    }
}

private data class ChangeLine(val text: String, val source: String)

private fun buildChanges(
    trends: List<GoalTrend>,
    latestNode: GoalNode?,
    entries: List<JournalEntryEntity>,
): List<ChangeLine> {
    val result = mutableListOf<ChangeLine>()
    trends.firstOrNull { it.points.size >= 2 }?.let { trend ->
        val first = trend.points.first()
        val last = trend.points.last()
        val unit = trend.unitLabel.takeIf { it != GoalTrends.UNIT_NONE }
        val isDelta = trend.series.kind == ProgressKind.DELTA
        if (isDelta) {
            // 增量是“每次新增”，不能写成总额从 A 到 B，否则会读成累计下降。
            result += ChangeLine(
                "「${trend.goalTitle}」有 ${trend.points.size} 次新增记录，最近一次 " +
                    MetricFormat.withUnit(last.value, unit) + "。",
                "来自你的增量进展",
            )
        } else {
            result += ChangeLine(
                "「${trend.goalTitle}」累计从 ${MetricFormat.withUnit(first.value, unit)} " +
                    "到 ${MetricFormat.withUnit(last.value, unit)}，共 ${trend.points.size} 次累计快照。",
                "来自你的累计快照",
            )
        }
    }
    latestNode?.lastCheckinAt?.let { at ->
        result += ChangeLine(
            "你最近一次为「${latestNode.goal.title}」记录进展，是在 ${Timestamps.formatDate(at)}。",
            "来自你在目标里记录的进展",
        )
    }
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val recentEntries = entries.count { it.createdAt >= weekAgo }
    if (recentEntries > 0) {
        result += ChangeLine("这周你写下了 $recentEntries 篇日记。", "来自本机日记")
    }
    return result.distinctBy { it.text }.take(4)
}

@Composable
private fun ChangesCard(
    trends: List<GoalTrend>,
    latestNode: GoalNode?,
    entries: List<JournalEntryEntity>,
    rangeLabel: String,
) {
    val colors = MorrowTheme.colors
    val changes = remember(trends, latestNode?.goal?.id, entries.size, rangeLabel) {
        buildChanges(trends, latestNode, entries)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MorrowSpacing.CardRadius))
            .background(colors.card)
            .padding(18.dp),
    ) {
        Text(
            text = "在${rangeLabel}里",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(8.dp))
        if (changes.isEmpty()) {
            Text(
                text = "还没有足够的变化可以描述。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "记录一次进展，或写下一篇日记；以后会在这里看到真实的线索。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        } else {
            changes.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .background(colors.accent, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = line.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceCard(
    weekly: com.mengyalife.app.domain.weekly.WeeklyReview?,
    entries: List<JournalEntryEntity>,
    onOpenWeekly: () -> Unit,
    onOpenDiary: (JournalEntryEntity) -> Unit,
    onOpenGoal: (Long) -> Unit,
    latestNode: GoalNode?,
) {
    val colors = MorrowTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MorrowSpacing.CardRadius))
            .background(colors.card)
            .padding(18.dp),
    ) {
        weekly?.facts?.take(2)?.forEachIndexed { index, fact ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenWeekly)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fact.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                MorrowIcon(
                    kind = MorrowIconKind.ARROW_RIGHT,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (weekly?.facts?.isNotEmpty() != true) {
            Text(
                text = "还没有可展开的回顾来源。打开每周回顾，看看本机记录怎样被整理。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            MorrowPrimaryButton(
                text = "打开一周回顾",
                onClick = onOpenWeekly,
                testTag = "trajectory_open_weekly",
            )
        }
        val recentEntries = entries.sortedByDescending { it.updatedAt }.take(2)
        if (recentEntries.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "日记里的原始记录",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            recentEntries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenDiary(entry) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title?.takeIf { it.isNotBlank() } ?: "无标题",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Timestamps.formatDate(entry.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
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
        latestNode?.let { node ->
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenGoal(node.goal.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "回到「${node.goal.title}」",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.forest,
                    modifier = Modifier.weight(1f),
                )
                MorrowIcon(
                    kind = MorrowIconKind.ARROW_RIGHT,
                    tint = colors.forest,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun flattenNode(node: GoalNode): List<GoalNode> =
    listOf(node) + node.children.flatMap { flattenNode(it) }
