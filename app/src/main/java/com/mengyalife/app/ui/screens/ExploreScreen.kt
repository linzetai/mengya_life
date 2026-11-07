package com.mengyalife.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowGlyph
import com.mengyalife.app.ui.components.MorrowGlyphKind
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowQuietButton
import com.mengyalife.app.ui.components.MorrowTextField
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType

private val DOMAIN_OPTIONS = listOf(
    GoalDomain.CAREER,
    GoalDomain.WEALTH,
    GoalDomain.HEALTH,
    GoalDomain.LEARNING,
    GoalDomain.LIFE,
    null,
)

/**
 * 「探索人生方向」：三步轻量流程 + 待确认草案。
 *
 * 整个过程不联网、不调用模型；草案只有在用户点击保存并进入目标编辑器、
 * 再次确认后才会写入目标表。
 */
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveDraft: (title: String, description: String, domain: GoalDomain) -> Unit,
    onDiscussWithAdvisor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    var domainName by rememberSaveable { mutableStateOf<String?>(null) }
    var hope by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val displayDomain = DOMAIN_OPTIONS.firstOrNull { it != null && it.name == domainName }
    val draftTitle = buildString {
        if (displayDomain != null) append(displayDomain.label).append(" · ")
        append(hope.trim().take(44).ifEmpty { "先找到下一步" })
    }.take(60)
    val draftDescription = buildString {
        if (hope.isNotBlank()) append("希望半年后的变化：").append(hope.trim()).append("\n\n")
        if (difficulty.isNotBlank()) append("目前最难的部分：").append(difficulty.trim()).append("\n\n")
        append("这是你在探索里写下的方向，还没有保存。")
    }

    BackHandler {
        if (step > 0) step -= 1 else onBack()
    }
    LaunchedEffect(step) { scrollState.scrollTo(0) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = colors.canvas,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp),
        ) {
            MorrowTopBar(
                label = "探索人生方向",
                onSettingsClick = onOpenSettings,
                onBack = { if (step > 0) step -= 1 else onBack() },
            )
            Spacer(Modifier.height(10.dp))
            StepDots(active = step)
            Spacer(Modifier.height(22.dp))

            when (step) {
                0 -> StepDomain(
                    selectedName = domainName,
                    onSelect = { domainName = it?.name ?: "UNSURE" },
                    onNext = { step = 1 },
                )

                1 -> StepHope(
                    value = hope,
                    onChange = { hope = it },
                    onNext = { step = 2 },
                    onSkip = {
                        hope = ""
                        step = 2
                    },
                )

                2 -> StepDifficulty(
                    value = difficulty,
                    onChange = { difficulty = it },
                    onNext = { step = 3 },
                    onBack = { step = 1 },
                )

                else -> StepDraft(
                    title = draftTitle,
                    description = draftDescription,
                    onSave = {
                        val targetDomain = displayDomain ?: GoalDomain.LIFE
                        onSaveDraft(draftTitle, draftDescription, targetDomain)
                    },
                    onDiscuss = {
                        onDiscussWithAdvisor(
                            "我想探索一个方向：${draftTitle}\n\n${draftDescription}\n" +
                                "请先帮我把这个模糊的想法问清楚，不要直接替我下结论。",
                        )
                    },
                    onRestart = {
                        step = 0
                        domainName = null
                        hope = ""
                        difficulty = ""
                    },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepDots(active: Int) {
    val colors = MorrowTheme.colors
    val dotActive = active.coerceAtMost(2)
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == dotActive) 20.dp else 6.dp, 6.dp)
                    .clip(CircleShape)
                    .background(if (index <= dotActive) colors.forest else colors.hairline),
            )
            if (index < 2) Spacer(Modifier.width(5.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (active >= 3) "待确认草案" else "第 ${active + 1} 步，共 3 步",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(
        text = title,
        style = MorrowType.SheetTitle,
        color = MorrowTheme.colors.textPrimary,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MorrowTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun StepDomain(
    selectedName: String?,
    onSelect: (GoalDomain?) -> Unit,
    onNext: () -> Unit,
) {
    val colors = MorrowTheme.colors
    StepTitle(
        title = "最近最在意的是什么？",
        subtitle = "不需要想得很完整，先选中此刻最靠近你的那个方向。",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DOMAIN_OPTIONS.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { option ->
                    val isSelected = (option?.name ?: "UNSURE") == selectedName
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(15.dp))
                            .clickable { onSelect(option) }
                            .testTag("explore_domain_${option?.name ?: "UNSURE"}"),
                        shape = RoundedCornerShape(15.dp),
                        color = if (isSelected) colors.forest else colors.card,
                        contentColor = if (isSelected) colors.insightText else colors.textPrimary,
                        border = BorderStroke(1.dp, if (isSelected) colors.forest else colors.hairline),
                    ) {
                        Text(
                            text = option?.label ?: "还不确定",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    MorrowPrimaryButton(
        text = "继续",
        onClick = onNext,
        enabled = selectedName != null,
        testTag = "explore_next",
    )
    Spacer(Modifier.height(14.dp))
    InlineNote("不选也可以直接离开；这里不会自动创建任何目标。")
}

@Composable
private fun StepHope(
    value: String,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    StepTitle(
        title = "希望半年后有什么不同？",
        subtitle = "写一个画面就够了，比如「每周能留出两个晚上做真正想做的事」。",
    )
    MorrowTextField(
        value = value,
        onValueChange = onChange,
        placeholder = "如果一切顺利，半年后的生活会有什么不一样？",
        minLines = 5,
        maxLines = 8,
        testTag = "explore_hope",
    )
    Spacer(Modifier.height(22.dp))
    MorrowPrimaryButton(
        text = "继续",
        onClick = onNext,
        testTag = "explore_next",
    )
    Spacer(Modifier.height(10.dp))
    MorrowQuietButton(
        text = "暂时想不到，先跳过",
        onClick = onSkip,
        testTag = "explore_skip",
    )
}

@Composable
private fun StepDifficulty(
    value: String,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    StepTitle(
        title = "目前什么最难？",
        subtitle = "时间、精力、钱、信息，或者只是还没想清楚——都可以写。",
    )
    MorrowTextField(
        value = value,
        onValueChange = onChange,
        placeholder = "现在最卡住你的是什么？",
        minLines = 5,
        maxLines = 8,
        testTag = "explore_difficulty",
    )
    Spacer(Modifier.height(22.dp))
    MorrowPrimaryButton(
        text = "看看我的草案",
        onClick = onNext,
        testTag = "explore_draft",
    )
    Spacer(Modifier.height(10.dp))
    MorrowQuietButton(
        text = "上一步",
        onClick = onBack,
    )
}

@Composable
private fun StepDraft(
    title: String,
    description: String,
    onSave: () -> Unit,
    onDiscuss: () -> Unit,
    onRestart: () -> Unit,
) {
    val colors = MorrowTheme.colors
    StepTitle(
        title = "也许可以先从这里开始",
        subtitle = "这是一份根据你刚才的话整理的草案，还没有进入你的目标树。",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(20.dp)
            .testTag("explore_draft_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorrowGlyph(
                kind = MorrowGlyphKind.SPARKLE,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "待确认的方向草案",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MorrowType.CardTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
    Spacer(Modifier.height(16.dp))
    SectionHeading(title = "接下来")
    Spacer(Modifier.height(8.dp))
    InlineNote("保存会进入目标编辑器，你仍可以修改标题、领域和状态；只有再次确认后才会写入。")
    Spacer(Modifier.height(16.dp))
    MorrowPrimaryButton(
        text = "带着这份草案去保存",
        onClick = onSave,
        testTag = "explore_save",
    )
    Spacer(Modifier.height(10.dp))
    MorrowQuietButton(
        text = "和顾问继续聊",
        onClick = onDiscuss,
        testTag = "explore_discuss",
    )
    Spacer(Modifier.height(10.dp))
    MorrowQuietButton(
        text = "重新想一想",
        onClick = onRestart,
        testTag = "explore_restart",
    )
}
