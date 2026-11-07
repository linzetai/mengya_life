package com.mengyalife.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.domain.insight.InsightType
import com.mengyalife.app.ui.components.MorrowGlyph
import com.mengyalife.app.ui.components.MorrowGlyphKind
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.components.ThinProgressBar
import com.mengyalife.app.ui.insight.InsightDetailDialog
import com.mengyalife.app.ui.insight.InsightUiState
import com.mengyalife.app.ui.theme.MorrowSpacing
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.util.Timestamps
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.getDefault())
private val WEEKDAY_LABELS = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

/**
 * 首页「今天」。
 *
 * 所有文案和数字都来自真实状态：
 * - 日期取设备本地日期；
 * - 人生方向取 Goal Repository 的长期目标；
 * - 洞察优先展示当前数据版本生成的洞察卡，没有时退回可核验的本地记录，
 *   再没有就展示温柔空态，不伪造资产/学习/坚持天数；
 * - 日记数量来自本机数据库。
 */
@Composable
fun TodayScreen(
    listState: LazyListState,
    goals: GoalSnapshot,
    entries: List<JournalEntryEntity>,
    insights: InsightUiState,
    onOpenSettings: () -> Unit,
    onOpenPanorama: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onCreateGoal: () -> Unit,
    onOpenExplore: () -> Unit,
    onWriteDiary: () -> Unit,
    onOpenDecision: () -> Unit,
    onOpenTrends: () -> Unit,
    onDiscussInsight: (String) -> Unit = {},
    onDraftInsight: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var detailInsight by remember { mutableStateOf<GrowthInsight?>(null) }
    val today = remember { LocalDate.now() }
    val localeDate = remember(today) {
        "${today.year}年${today.monthValue}月${today.dayOfMonth}日 · " +
            WEEKDAY_LABELS[today.dayOfWeek.value - 1]
    }
    val presentation = remember(
        insights.currentVersion,
        insights.report,
        goals.longTermCount,
        goals.lastCheckinAt,
        goals.roots.map { it.goal.id to it.lastCheckinAt },
        entries.firstOrNull()?.id,
        entries.size,
    ) {
        buildInsightPresentation(insights, goals, entries)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_screen"),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(MorrowSpacing.SectionGap),
    ) {
        item {
            Column {
                MorrowTopBar(
                    label = "萌芽.",
                    onSettingsClick = onOpenSettings,
                    brand = true,
                    showDemoTag = BuildConfig.QA_BUILD,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = localeDate,
                    style = MorrowType.Eyebrow,
                    color = MorrowTheme.colors.textSecondary,
                    modifier = Modifier.testTag("today_date"),
                )
                Spacer(Modifier.height(11.dp))
                Text(
                    text = "慢慢来，\n你已经走在路上。",
                    style = MorrowType.HomeTitle,
                    color = MorrowTheme.colors.textPrimary,
                    modifier = Modifier.testTag("today_title"),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "不必每天都进步。但值得看见每一点真实的变化。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MorrowTheme.colors.textSecondary,
                )
            }
        }

        item {
            InsightCard(
                presentation = presentation,
                onClick = {
                    val insight = presentation.insight
                    when {
                        insight != null -> detailInsight = insight
                        presentation.isLocal -> onOpenTrends()
                        else -> onOpenExplore()
                    }
                },
            )
        }

        item {
            Column {
                SectionHeading(
                    title = "我在意的人生方向",
                    actionLabel = "查看全景",
                    onAction = onOpenPanorama,
                    testTag = "today_open_panorama",
                )
                Spacer(Modifier.height(12.dp))
                if (goals.roots.isEmpty()) {
                    DirectionEmptyRow(onClick = onOpenPanorama)
                } else {
                    goals.roots.take(3).forEachIndexed { index, node ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        DirectionRow(
                            node = node,
                            onClick = { onOpenGoal(node.goal.id) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onCreateGoal)
                            .padding(vertical = 10.dp)
                            .testTag("today_add_direction"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "＋  添加一个方向",
                            style = MaterialTheme.typography.labelMedium,
                            color = MorrowTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionHeading(title = "今天，想从哪里开始？")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionTile(
                        glyph = MorrowGlyphKind.COMPASS,
                        label = "探索\n人生方向",
                        testTag = "today_explore",
                        onClick = onOpenExplore,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        glyph = MorrowGlyphKind.PEN,
                        label = "写一篇\n日记",
                        testTag = "today_write_diary",
                        onClick = onWriteDiary,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        glyph = MorrowGlyphKind.FORK,
                        label = "讨论\n一个决定",
                        testTag = "today_decide",
                        onClick = onOpenDecision,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Column {
                SectionHeading(
                    title = "也值得记住的好事",
                    actionLabel = "更多趋势",
                    onAction = onOpenTrends,
                    testTag = "today_open_trends",
                )
                Spacer(Modifier.height(12.dp))
                GoodThingsCard(entries = entries, onWriteDiary = onWriteDiary)
            }
        }
    }

    detailInsight?.let { insight ->
        InsightDetailDialog(
            insight = insight,
            report = insights.report,
            onDismiss = { detailInsight = null },
            onOpenGoals = onOpenPanorama,
            onOpenGoal = onOpenGoal,
            onOpenTrends = onOpenTrends,
            onDiscuss = {
                onDiscussInsight(insight.title + "：" + insight.body)
                detailInsight = null
            },
            onDraft = {
                onDraftInsight(insight.title + "：" + insight.body)
                detailInsight = null
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 洞察卡
// ---------------------------------------------------------------------------

private data class InsightPresentation(
    val eyebrow: String,
    val quote: String,
    val evidence: String?,
    val cta: String,
    val insight: GrowthInsight? = null,
    val isLocal: Boolean = false,
)

private fun buildInsightPresentation(
    state: InsightUiState,
    goals: GoalSnapshot,
    entries: List<JournalEntryEntity>,
): InsightPresentation {
    val report = state.report
    if (state.hasReport && report != null) {
        if (report.status == InsightReportStatus.READY && report.insights.isNotEmpty()) {
            val insight = report.insights.firstOrNull { it.type == InsightType.POSITIVE }
                ?: report.insights.first()
            val period = "${SHORT_DATE.format(Instant.ofEpochMilli(report.periodStart).atZone(ZoneId.systemDefault()))}" +
                "–${SHORT_DATE.format(Instant.ofEpochMilli(report.periodEnd).atZone(ZoneId.systemDefault()))}"
            return InsightPresentation(
                eyebrow = "本周的一点发现",
                quote = insight.title,
                evidence = "来自 ${report.facts.size} 条可核验事实 · $period",
                cta = "看看这背后发生了什么",
                insight = insight,
            )
        }
        if (report.status == InsightReportStatus.FAILED) {
            val fact = report.facts.firstOrNull()
            return InsightPresentation(
                eyebrow = "本周的一点事实",
                quote = fact?.text ?: "这一周，你仍在留下自己的记录。",
                evidence = "AI 解释尚未生成，这里只呈现本地可核验的事实",
                cta = "看看这背后发生了什么",
                insight = null,
                isLocal = true,
            )
        }
    }

    val now = System.currentTimeMillis()
    val allNodes = goals.roots.flatMap { flattenNode(it) }
    val latestNode = allNodes
        .filter { (it.lastCheckinAt ?: 0L) > 0L }
        .maxByOrNull { it.lastCheckinAt ?: 0L }
    val latestCheckin = latestNode?.lastCheckinAt
    if (latestCheckin != null && now - latestCheckin <= 14L * 24 * 60 * 60 * 1000) {
        val inWeek = now - latestCheckin <= 7L * 24 * 60 * 60 * 1000
        return InsightPresentation(
            eyebrow = if (inWeek) "本周的一点发现" else "最近的一点记录",
            quote = "你在「${latestNode.goal.title}」上，\n又留下了一次进展。",
            evidence = "来自你手动记录的进展 · ${Timestamps.formatDate(latestCheckin)}",
            cta = "看看这背后发生了什么",
            isLocal = true,
        )
    }

    val latestEntry = entries.maxByOrNull { it.updatedAt }
    if (latestEntry != null && now - latestEntry.updatedAt <= 14L * 24 * 60 * 60 * 1000) {
        return InsightPresentation(
            eyebrow = "最近的一点记录",
            quote = "你又为自己，\n留下了一篇记录。",
            evidence = "共 ${entries.size} 篇日记 · 最近一次 ${Timestamps.formatDate(latestEntry.updatedAt)}",
            cta = "看看这背后发生了什么",
            isLocal = true,
        )
    }

    return InsightPresentation(
        eyebrow = "本周的一点发现",
        quote = "给自己一点时间，\n我们从今天开始记录。",
        evidence = "记录不必完整，也不必正确。先从一个真实的瞬间开始。",
        cta = "开始探索",
        isLocal = false,
    )
}

private fun flattenNode(node: GoalNode): List<GoalNode> =
    listOf(node) + node.children.flatMap { flattenNode(it) }

@Composable
private fun InsightCard(
    presentation: InsightPresentation,
    onClick: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(MorrowSpacing.InsightRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.insightCard)
            .clickable(onClick = onClick)
            .testTag("today_insight_card"),
    ) {
        // 右上角很淡的同心弧线，只做安静的装饰。
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width * 1.04f, size.height * 0.06f)
            val unit = size.width * 0.16f
            listOf(1.2f, 1.9f, 2.6f).forEachIndexed { index, factor ->
                drawCircle(
                    color = colors.insightText.copy(alpha = 0.10f + index * 0.012f),
                    radius = unit * factor,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorrowGlyph(
                    kind = MorrowGlyphKind.SPARKLE,
                    tint = colors.insightMuted,
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.2.dp,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = presentation.eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.insightMuted,
                )
            }
            Spacer(Modifier.height(15.dp))
            Text(
                text = presentation.quote,
                style = MorrowType.InsightQuote,
                color = colors.insightText,
            )
            if (presentation.evidence != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = presentation.evidence,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.insightMuted.copy(alpha = 0.78f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = presentation.cta,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.insightText,
                    modifier = Modifier.weight(1f),
                )
                MorrowGlyph(
                    kind = MorrowGlyphKind.ARROW,
                    tint = colors.insightText,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.4.dp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 人生方向
// ---------------------------------------------------------------------------

@Composable
private fun DirectionRow(
    node: GoalNode,
    onClick: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val goal = node.goal
    val done = node.children.count { it.goal.status == GoalStatus.DONE }
    val progress = if (node.children.isEmpty()) 0f else done.toFloat() / node.children.size
    val subtitle = goal.description?.takeIf { it.isNotBlank() }
        ?: buildString {
            if (node.children.isEmpty()) {
                append("还没有拆解阶段目标")
            } else {
                append("${node.children.size} 个阶段")
                if (node.checkinCount > 0) append(" · ${node.checkinCount} 次进展")
            }
        }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("direction_row_${goal.id}"),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.nested),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = domainGlyph(goal.domain.label),
                        style = MorrowType.Brand.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = colors.forest,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${goal.domain.label} · ${goal.title}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                MorrowGlyph(
                    kind = MorrowGlyphKind.ARROW,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(15.dp),
                )
            }
            if (node.children.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ThinProgressBar(progress = progress, color = colors.accent)
            }
        }
    }
}

@Composable
private fun DirectionEmptyRow(onClick: () -> Unit) {
    val colors = MorrowTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.nested),
                contentAlignment = Alignment.Center,
            ) {
                MorrowGlyph(
                    kind = MorrowGlyphKind.SPARKLE,
                    tint = colors.forest,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "还没有设定这个方向",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "可以先看看全景，或者从一次探索开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            MorrowGlyph(
                kind = MorrowGlyphKind.ARROW,
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun domainGlyph(label: String): String = when (label) {
    "健康" -> "健"
    "职业" -> "职"
    "财富" -> "财"
    "学习" -> "学"
    else -> "生"
}

// ---------------------------------------------------------------------------
// 日常入口
// ---------------------------------------------------------------------------

@Composable
private fun ActionTile(
    glyph: MorrowGlyphKind,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 98.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.nested),
            contentAlignment = Alignment.Center,
        ) {
            MorrowGlyph(
                kind = glyph,
                tint = colors.forest,
                modifier = Modifier.size(17.dp),
                strokeWidth = 1.4.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// 正向记忆
// ---------------------------------------------------------------------------

@Composable
private fun GoodThingsCard(
    entries: List<JournalEntryEntity>,
    onWriteDiary: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val latest = entries.maxByOrNull { it.updatedAt }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.nested)
            .clickable(onClick = onWriteDiary)
            .testTag("today_memory_card")
            .padding(horizontal = 18.dp, vertical = 17.dp),
    ) {
        Text(
            text = if (latest == null) {
                "还没有写下第一篇。"
            } else {
                "你写下的记录，正在慢慢连成一条线。"
            },
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (latest == null) {
                "今天也可以写下一个瞬间，不必完整，也不必正确。"
            } else {
                "已经留下 ${entries.size} 篇日记，最近一次是 ${Timestamps.formatDate(latest.updatedAt)}。" +
                    "对这些时刻的回看，就是理解自己的开始。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "写下一篇",
                style = MaterialTheme.typography.labelMedium,
                color = colors.forest,
            )
            Spacer(Modifier.width(5.dp))
            MorrowGlyph(
                kind = MorrowGlyphKind.ARROW,
                tint = colors.forest,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
