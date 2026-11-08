package com.mengyalife.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mengyalife.app.data.repository.GoalSnapshot
import com.mengyalife.app.ui.components.InlineNote
import com.mengyalife.app.ui.components.MorrowIcon
import com.mengyalife.app.ui.components.MorrowIconKind
import com.mengyalife.app.ui.components.MorrowPrimaryButton
import com.mengyalife.app.ui.components.MorrowSecondaryButton
import com.mengyalife.app.ui.components.MorrowTertiaryButton
import com.mengyalife.app.ui.components.MorrowTextField
import com.mengyalife.app.ui.components.MorrowTopBar
import com.mengyalife.app.ui.components.SectionHeading
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType

/**
 * 「讨论一个决定」：用用户写下的内容整理一个可核验的思考框架。
 *
 * 这里不做 AI 推断，也不写财务数据。金额只在用户填写时被当作「用户假设」，
 * 页面明确标注为思考框架，而不是预测。
 */
@Composable
fun DecisionScreen(
    goals: GoalSnapshot,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onDiscussWithAdvisor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    var decision by rememberSaveable { mutableStateOf("") }
    var options by rememberSaveable { mutableStateOf("") }
    var constraints by rememberSaveable { mutableStateOf("") }
    var timing by rememberSaveable { mutableStateOf("") }
    var showFramework by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var actionBarHeight by remember { mutableStateOf(0.dp) }

    val directions = goals.roots.take(2).joinToString("、") { it.goal.title }
    val involvesMoney = listOf(decision, options, constraints).any {
        listOf("钱", "元", "万", "投资", "学费", "预算", "储蓄").any { word -> it.contains(word) }
    }

    LaunchedEffect(showFramework) { scrollState.scrollTo(0) }

    BackHandler {
        if (showFramework) showFramework = false else onBack()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = colors.canvas,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp)
                    .padding(bottom = if (showFramework) actionBarHeight + 16.dp else 0.dp),
            ) {
            MorrowTopBar(
                label = "讨论一个决定",
                onSettingsClick = null,
                onBack = onBack,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "一个决定，\n可以先看清楚一点。",
                style = MorrowType.PageTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "先不用急着选。把你已经知道的写下来，再一起看看它和长期方向的关系。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(22.dp))

            if (!showFramework) {
            FieldLabel("你在考虑什么")
            Spacer(Modifier.height(7.dp))
            MorrowTextField(
                value = decision,
                onValueChange = { decision = it },
                placeholder = "你在考虑什么决定？例如：要不要投入一笔钱去学习",
                minLines = 3,
                maxLines = 5,
                testTag = "decision_question",
            )
            Spacer(Modifier.height(14.dp))
            FieldLabel("有哪些选项")
            Spacer(Modifier.height(7.dp))
            MorrowTextField(
                value = options,
                onValueChange = { options = it },
                placeholder = "有哪些选项？（可以只写你正在犹豫的两三个）",
                minLines = 3,
                maxLines = 5,
                testTag = "decision_options",
            )
            Spacer(Modifier.height(14.dp))
            FieldLabel("你最在意的约束")
            Spacer(Modifier.height(7.dp))
            MorrowTextField(
                value = constraints,
                onValueChange = { constraints = it },
                placeholder = "你最在意的约束是什么？时间、精力、钱、家人……",
                minLines = 3,
                maxLines = 5,
                testTag = "decision_constraints",
            )
            Spacer(Modifier.height(14.dp))
            FieldLabel("什么时候要决定")
            Spacer(Modifier.height(7.dp))
            MorrowTextField(
                value = timing,
                onValueChange = { timing = it },
                placeholder = "大概什么时候需要做决定？（可留空）",
                singleLine = true,
                testTag = "decision_timing",
            )
            Spacer(Modifier.height(20.dp))
            MorrowPrimaryButton(
                text = "整理成一个思考框架",
                onClick = { showFramework = true },
                enabled = decision.isNotBlank(),
                testTag = "decision_build",
            )
            Spacer(Modifier.height(12.dp))
            InlineNote("本页不联网，也不会替你做决定。金额只会按你写下的数字被引用，不做收益预测。")
            } else {
                Spacer(Modifier.height(12.dp))
                SectionHeading(title = "可以这样看这件事")
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    color = colors.card,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.hairline),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                        FrameworkRow(
                            index = "01",
                            title = "可能的短期影响",
                            body = buildString {
                                append("先看它对你接下来 1–3 个月的时间、精力和现金流的占用。")
                                if (constraints.isNotBlank()) append("\n你写下的约束：${constraints.trim()}")
                            },
                            testTag = "decision_short_term",
                        )
                        FrameworkDivider()
                        FrameworkRow(
                            index = "02",
                            title = "和长期方向的关系",
                            body = if (directions.isNotBlank()) {
                                "你现在在意的方向是：$directions。\n问自己：这个决定会让其中一个方向更清楚，还是只是更忙？"
                            } else {
                                "你还没有设定长期方向。可以先问自己：这个决定会让我更了解自己想要什么吗？"
                            },
                            testTag = "decision_long_term",
                        )
                        FrameworkDivider()
                        FrameworkRow(
                            index = "03",
                            title = "还不确定的因素",
                            body = buildString {
                                if (options.isNotBlank()) append("你已经写下的选项：${options.trim()}\n")
                                append("哪一项最不可逆？有没有一个信息，是你在决定前必须补上的？")
                            },
                            testTag = "decision_uncertain",
                        )
                        FrameworkDivider()
                        FrameworkRow(
                            index = "04",
                            title = "可以先验证的小行动",
                            body = "在做出承诺前，先做一个可逆的小验证：找一位做过这件事的人聊 30 分钟，" +
                                "或先试一个月。" +
                                if (timing.isNotBlank()) "\n你写下的时间：${timing.trim()}" else "",
                            testTag = "decision_action",
                        )
                    }
                }
                if (involvesMoney) {
                    Spacer(Modifier.height(12.dp))
                    InlineNote("你提到了金额相关的内容。这里没有做财务模拟或收益预测，只保留你写下的假设。")
                }
                Spacer(Modifier.height(10.dp))
                InlineNote(
                    "「和顾问继续讨论」只会把你刚才写下的内容带到对话框；" +
                        "是否发送、发送哪些类别，仍由你在顾问页确认。",
                )
            }
            Spacer(Modifier.height(32.dp))
            }
            if (showFramework) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            actionBarHeight = with(density) { size.height.toDp() }
                        }
                        .background(colors.canvas)
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.hairline),
                    )
                    Spacer(Modifier.height(8.dp))
                    MorrowPrimaryButton(
                        text = "和顾问继续讨论",
                        onClick = {
                            onDiscussWithAdvisor(buildAdvisorPrompt(decision, options, constraints, timing))
                        },
                        testTag = "decision_discuss",
                    )
                    Spacer(Modifier.height(8.dp))
                    MorrowSecondaryButton(
                        text = "修改刚才的输入",
                        onClick = { showFramework = false },
                        testTag = "decision_edit",
                    )
                    Spacer(Modifier.height(2.dp))
                    MorrowTertiaryButton(
                        text = "先不决定，回到今天",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MorrowTheme.colors.textSecondary,
    )
}

@Composable
private fun FrameworkRow(
    index: String,
    title: String,
    body: String,
    testTag: String,
) {
    val colors = MorrowTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun FrameworkDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp)
            .height(1.dp)
            .background(MorrowTheme.colors.hairline),
    )
}


private fun buildAdvisorPrompt(
    decision: String,
    options: String,
    constraints: String,
    timing: String,
): String = buildString {
    append("我想讨论一个决定：").append(decision.trim()).append('\n')
    if (options.isNotBlank()) append("我想到的选项：").append(options.trim()).append('\n')
    if (constraints.isNotBlank()) append("我在意的约束：").append(constraints.trim()).append('\n')
    if (timing.isNotBlank()) append("时间：").append(timing.trim()).append('\n')
    append("请先帮我把短期影响、长期方向、不确定因素和可以验证的小行动分开，不要替我下结论。")
}
