package com.mengyalife.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mengyalife.app.BuildConfig
import com.mengyalife.app.data.local.JournalEntryEntity
import com.mengyalife.app.data.repository.GoalNode
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.domain.goals.GoalStatus
import com.mengyalife.app.domain.insight.GrowthInsight
import com.mengyalife.app.domain.insight.InsightReportStatus
import com.mengyalife.app.domain.insight.InsightType
import com.mengyalife.app.domain.insight.InsightReference
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.ScreenPadding
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.components.ThinProgressBar
import com.mengyalife.app.ui.components.goalDomainIcon
import com.mengyalife.app.ui.components.rememberToday
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
 * 信息层级只有三层：
 * 1. 当前关注：本周可核验的洞察、或最近一次真实记录、或「还在认识你」；
 * 2. 下一步：一个由真实状态推导出的行动（含主动关心卡）；
 * 3. 成长概览：精简的方向与进展，详情进人生全景。
 *
 * 所有数字来自本机 Repository；没有数据时不生成任何判断或数值。
 */
@Composable
fun TodayScreen(
    listState: LazyListState,
    goals: GoalSnapshot,
    plans: List<PlanTodayItem> = emptyList(),
    onOpenPlan: (String) -> Unit = {},
    careCard: com.mengyalife.app.ui.care.CareCard? = null,
    onCareEngage: () -> Unit = {},
    onCareIgnore: () -> Unit = {},
    onCareSilence: () -> Unit = {},
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
    onGenerateInsight: () -> Unit = {},
    onDiscussInsight: (InsightReference) -> Unit = {},
    onTalkToAdvisor: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var detailInsight by remember { mutableStateOf<GrowthInsight?>(null) }
    val today = rememberToday()
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
    val nextStep = buildNextStep(
        careCard = careCard,
        plans = plans,
        goals = goals,
        entries = entries,
        today = today,
        onCareEngage = onCareEngage,
        onCareIgnore = onCareIgnore,
        onCareSilence = onCareSilence,
        onOpenPlan = onOpenPlan,
        onCreateGoal = onCreateGoal,
        onOpenGoal = onOpenGoal,
        onOpenExplore = onOpenExplore,
        onWriteDiary = onWriteDiary,
        onTalkToAdvisor = onTalkToAdvisor,
    )
    val latestEntryToday = entries.any {
        Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("today_screen"),
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        stickyHeader {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorrowTheme.colors.canvas),
            ) {
                MorrowTopBar(
                    label = "MengYa",
                    onSettingsClick = onOpenSettings,
                    brand = true,
                    qaTag = BuildConfig.QA_BUILD,
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = localeDate,
                    style = MorrowType.Eyebrow,
                    color = MorrowTheme.colors.textSecondary,
                    modifier = Modifier.testTag("today_date"),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = greetingFor(today),
                    style = MorrowType.HomeTitle,
                    color = MorrowTheme.colors.textPrimary,
                    modifier = Modifier.testTag("today_title"),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "不必每天都进步，值得看见每一点真实的变化。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MorrowTheme.colors.textSecondary,
                )
            }
        }

        item {
            FocusCard(
                presentation = presentation,
                loading = insights.loading,
                error = insights.error,
                onGenerate = onGenerateInsight,
                onClick = {
                    val insight = presentation.insight
                    when {
                        insight != null -> detailInsight = insight
                        presentation.isLearning -> onTalkToAdvisor("想聊聊最近在意的事。")
                        presentation.isLocal -> onOpenTrends()
                        else -> onWriteDiary()
                    }
                },
            )
        }

        item {
            NextStepCard(step = nextStep)
        }

        item {
            GrowthOverview(
                goals = goals,
                entries = entries,
                onOpenPanorama = onOpenPanorama,
                onOpenGoal = onOpenGoal,
                onCreateGoal = onCreateGoal,
                onOpenTrends = onOpenTrends,
            )
        }

        item {
            QuietLinks(
                wroteToday = latestEntryToday,
                onWriteDiary = onWriteDiary,
                onOpenExplore = onOpenExplore,
                onOpenDecision = onOpenDecision,
            )
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
            onDiscuss = { reference ->
                onDiscussInsight(reference)
                detailInsight = null
            },
        )
    }
}

private fun greetingFor(today: LocalDate): String = when (today.dayOfWeek.value) {
    1, 2, 3, 4, 5 -> "慢慢来，\n你已经走在路上。"
    6, 7 -> "周末好，\n把这一周轻轻收个尾。"
    else -> "慢慢来，\n你已经走在路上。"
}

// ---------------------------------------------------------------------------
// 当前关注
// ---------------------------------------------------------------------------

private data class InsightPresentation(
    val eyebrow: String,
    val quote: String,
    val evidence: String?,
    val cta: String,
    val insight: GrowthInsight? = null,
    val isLocal: Boolean = false,
    val isLearning: Boolean = false,
)

private fun buildInsightPresentation(
    state: InsightUiState,
    goals: GoalSnapshot,
    entries: List<JournalEntryEntity>,
): InsightPresentation {
    val report = state.report
    if (state.hasReport && report != null) {
        if (report.status == InsightReportStatus.READY && report.facts.any { it.id == "insufficient" }) {
            val fact = report.facts.first { it.id == "insufficient" }
            return InsightPresentation(
                eyebrow = "数据不足 · 暂不下结论",
                quote = "先看见已经留下的真实记录。",
                evidence = fact.text,
                cta = "看看已有记录",
                isLocal = true,
            )
        }
        if (report.status == InsightReportStatus.READY && report.insights.isNotEmpty()) {
            val insight = report.insights.firstOrNull { it.type == InsightType.SUGGESTION }
                ?: report.insights.firstOrNull { it.type == InsightType.POSITIVE }
                ?: report.insights.first()
            val period = "${SHORT_DATE.format(Instant.ofEpochMilli(report.periodStart).atZone(ZoneId.systemDefault()))}" +
                "–${SHORT_DATE.format(Instant.ofEpochMilli(report.periodEnd).atZone(ZoneId.systemDefault()))}"
            return InsightPresentation(
                eyebrow = when {
                    insight.type == InsightType.SUGGESTION -> "下一步建议 · 需由你决定"
                    insight.isInference -> "AI 推测 · 不是既定事实"
                    else -> "规则事实 · 可追溯记录"
                },
                quote = insight.title,
                evidence = "引用 ${insight.factIds.size} 条依据 · $period",
                cta = if (insight.type == InsightType.SUGGESTION) "和顾问讨论这条建议" else "查看依据与来源",
                insight = insight,
            )
        }
        if (report.status == InsightReportStatus.FAILED) {
            val fact = report.facts.firstOrNull { it.id == "insufficient" } ?: report.facts.firstOrNull()
            val insufficient = fact?.id == "insufficient"
            return InsightPresentation(
                eyebrow = if (insufficient) "数据不足 · 暂不下结论" else "这一周的一点事实",
                quote = if (insufficient) "先看见已经留下的真实记录。"
                else fact?.text ?: "这一周，你仍在留下自己的记录。",
                evidence = if (insufficient) fact?.text.orEmpty() else "AI 解释尚未生成，这里只呈现本地可核验的事实",
                cta = if (insufficient) "补充记录后再评估" else "看看这背后发生了什么",
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
            eyebrow = if (inWeek) "本周的一点记录" else "最近的一点记录",
            quote = "你又留下了一次\n真实的进展。",
            evidence = "来自「${latestNode.goal.title}」· ${Timestamps.formatDate(latestCheckin)}",
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

    val empty = goals.roots.isEmpty() && entries.isEmpty()
    return InsightPresentation(
        eyebrow = if (empty) "还在认识你" else "本周的一点关注",
        quote = if (empty) {
            "从一个真实的瞬间开始，\n我会慢慢记住你。"
        } else {
            "最近还没有新的记录，\n不着急。"
        },
        evidence = if (empty) {
            "聊聊最近在意的事，或者写下一句话。"
        } else {
            "记录一次进展或写一篇日记后，这里会显示属于自己的观察。"
        },
        cta = if (empty) "和 MengYa 聊聊" else "去写一篇日记",
        isLocal = false,
        isLearning = empty,
    )
}

private fun flattenNode(node: GoalNode): List<GoalNode> =
    listOf(node) + node.children.flatMap { flattenNode(it) }

@Composable
private fun FocusCard(
    presentation: InsightPresentation,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(MorrowSpacing.InsightRadius)
    val darkCard = !presentation.isLearning
    val container = if (darkCard) colors.insightCard else colors.card
    val titleColor = if (darkCard) colors.insightText else colors.textPrimary
    val mutedColor = if (darkCard) colors.insightMuted else colors.textSecondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick)
            .testTag("today_insight_card"),
    ) {
        if (darkCard) {
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
        }
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorrowIcon(
                    kind = MorrowIconKind.SPARKLE,
                    tint = mutedColor,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = presentation.eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = presentation.quote,
                style = MorrowType.InsightQuote,
                color = titleColor,
            )
            presentation.evidence?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = presentation.cta,
                    style = MaterialTheme.typography.labelLarge,
                    color = titleColor,
                    modifier = Modifier.weight(1f),
                )
                MorrowIcon(
                    kind = MorrowIconKind.ARROW_RIGHT,
                    tint = titleColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            TextButton(
                onClick = onGenerate,
                enabled = !loading,
                modifier = Modifier.testTag("today_insight_generate"),
            ) {
                Text(if (loading) "正在重新评估…" else "生成 / 更新洞察")
            }
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (darkCard) colors.insightMuted else colors.textSecondary,
                    modifier = Modifier.testTag("today_insight_error"),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 下一步（含主动关心）
// ---------------------------------------------------------------------------

private class NextStep(
    val eyebrow: String,
    val title: String,
    val body: String,
    val cta: String,
    val tag: String,
    val onPrimary: () -> Unit,
    val isCare: Boolean = false,
    val secondary: List<Pair<String, () -> Unit>> = emptyList(),
)

private fun buildNextStep(
    careCard: com.mengyalife.app.ui.care.CareCard?,
    plans: List<PlanTodayItem>,
    goals: GoalSnapshot,
    entries: List<JournalEntryEntity>,
    today: LocalDate,
    onCareEngage: () -> Unit,
    onCareIgnore: () -> Unit,
    onCareSilence: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onCreateGoal: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onOpenExplore: () -> Unit,
    onWriteDiary: () -> Unit,
    onTalkToAdvisor: (String) -> Unit,
): NextStep {
    val spec = decideTodayNextStep(
        careText = careCard?.text,
        careSourceType = careCard?.sourceType,
        plans = plans,
        goals = goals,
        entries = entries,
        today = today,
    )
    val primary: () -> Unit = when (spec.kind) {
        TodayNextStepKind.CARE -> onCareEngage
        TodayNextStepKind.PLAN -> ({ onOpenPlan(spec.planTitle.orEmpty()) })
        TodayNextStepKind.CREATE_GOAL -> onCreateGoal
        TodayNextStepKind.STALE_GOAL -> ({ spec.goalId?.let(onOpenGoal) })
        TodayNextStepKind.WRITE_DIARY -> onWriteDiary
        TodayNextStepKind.TALK -> ({ onTalkToAdvisor("最近我注意到一些变化，想和你聊聊。") })
    }
    val secondary: List<Pair<String, () -> Unit>> = when (spec.kind) {
        TodayNextStepKind.CARE -> listOf("暂时忽略" to onCareIgnore, "不再提醒" to onCareSilence)
        TodayNextStepKind.CREATE_GOAL -> listOf("先探索一下" to onOpenExplore)
        TodayNextStepKind.WRITE_DIARY -> listOf(
            "先和 MengYa 聊聊" to { onTalkToAdvisor("今天想聊聊最近的状态。") },
        )
        else -> emptyList()
    }
    val tag = when (spec.kind) {
        TodayNextStepKind.CARE -> "today_care_card"
        TodayNextStepKind.PLAN -> "today_plan_${spec.planId ?: 0}"
        TodayNextStepKind.CREATE_GOAL -> "today_next_create_goal"
        TodayNextStepKind.STALE_GOAL -> "today_next_goal_${spec.goalId ?: 0}"
        TodayNextStepKind.WRITE_DIARY -> "today_next_write_diary"
        TodayNextStepKind.TALK -> "today_next_talk"
    }
    return NextStep(
        eyebrow = spec.eyebrow,
        title = spec.title,
        body = spec.body,
        cta = spec.cta,
        tag = tag,
        onPrimary = primary,
        isCare = spec.kind == TodayNextStepKind.CARE,
        secondary = secondary,
    )
}

@Composable
private fun NextStepCard(step: NextStep) {
    val colors = MorrowTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(step.tag),
        shape = RoundedCornerShape(MorrowSpacing.CardRadius),
        color = colors.card,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = step.eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = if (step.isCare) colors.forest else colors.textTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            MorrowPrimaryButton(
                text = step.cta,
                onClick = step.onPrimary,
                testTag = "today_next_action",
            )
            if (step.secondary.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    step.secondary.forEach { (label, action) ->
                        TextButton(onClick = action) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 成长概览
// ---------------------------------------------------------------------------

@Composable
private fun GrowthOverview(
    goals: GoalSnapshot,
    entries: List<JournalEntryEntity>,
    onOpenPanorama: () -> Unit,
    onOpenGoal: (Long) -> Unit,
    onCreateGoal: () -> Unit,
    onOpenTrends: () -> Unit,
) {
    val colors = MorrowTheme.colors
    Column {
        SectionHeading(
            title = "成长概览",
            actionLabel = "查看全景",
            onAction = onOpenPanorama,
            testTag = "today_open_panorama",
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverviewStat(value = "${goals.longTermCount}", label = "个方向")
            OverviewDot()
            OverviewStat(value = "${goals.stageCount}", label = "个阶段")
            OverviewDot()
            OverviewStat(value = "${entries.size}", label = "篇日记")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = goals.lastCheckinAt?.let { "最近一次进展 · " + Timestamps.formatDate(it) }
                ?: "还没有进展记录",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        if (goals.roots.isEmpty()) {
            DirectionEmptyRow(onClick = onOpenPanorama)
        } else {
            goals.roots.take(2).forEachIndexed { index, node ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                DirectionRow(node = node, onClick = { onOpenGoal(node.goal.id) })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (goals.roots.size > 2) {
                    Text(
                        text = "还有 ${goals.roots.size - 2} 个方向",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenPanorama)
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "看轨迹",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.forest,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenTrends)
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun OverviewStat(value: String, label: String) {
    val colors = MorrowTheme.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MorrowType.DateNumber,
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun OverviewDot() {
    Spacer(Modifier.width(10.dp))
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MorrowTheme.colors.textTertiary),
    )
    Spacer(Modifier.width(10.dp))
}

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
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(
            modifier = Modifier
                .defaultMinSize(minHeight = 72.dp)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.nested),
                    contentAlignment = Alignment.Center,
                ) {
                    MorrowIcon(
                        kind = goalDomainIcon(goal.domain),
                        tint = colors.forest,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        maxLines = 2,
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
                MorrowIcon(
                    kind = MorrowIconKind.ARROW_RIGHT,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (node.children.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
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
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.nested),
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(
                    kind = MorrowIconKind.LEAF,
                    tint = colors.forest,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "还没有设定方向",
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
            MorrowIcon(
                kind = MorrowIconKind.ARROW_RIGHT,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 次要入口
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuietLinks(
    wroteToday: Boolean,
    onWriteDiary: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenDecision: () -> Unit,
) {
    val colors = MorrowTheme.colors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = onWriteDiary, modifier = Modifier.testTag("today_write_diary")) {
            Text(
                text = if (wroteToday) "再写一篇" else "写日记",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        TextButton(onClick = onOpenExplore, modifier = Modifier.testTag("today_explore")) {
            Text("探索方向", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
        TextButton(onClick = onOpenDecision, modifier = Modifier.testTag("today_decide")) {
            Text("讨论一个决定", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
    }
}
