package com.mengyalife.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mengyalife.app.data.DemoData
import com.mengyalife.app.ui.theme.MorrowSpacing
import com.mengyalife.app.ui.theme.MorrowTheme
import com.mengyalife.app.ui.theme.MorrowType
import com.mengyalife.app.ui.theme.isDarkTheme

/** 页面内容统一的内边距：22dp 左右，顶部克制。 */
val ScreenPadding = PaddingValues(
    start = MorrowSpacing.PageHorizontal,
    end = MorrowSpacing.PageHorizontal,
    top = 6.dp,
    bottom = 32.dp,
)

/** 「演示数据」标记：只在 QA 构建里出现。 */
@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MorrowTheme.colors.nested,
        contentColor = MorrowTheme.colors.textSecondary,
    ) {
        Text(
            text = DemoData.NOTICE,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * 轻量顶栏：左侧品牌/页面名，右侧「演示数据」（仅 QA）与纯文字「设置」。
 *
 * 顶栏高度 56dp，页面左右边距 22dp；设置文字不包按钮背景、不带图标，
 * 通过 48dp 触控区保证可点击。
 */
@Composable
fun MorrowTopBar(
    label: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    brand: Boolean = false,
    showDemoTag: Boolean = false,
    backTestTag: String = "page_back",
    settingsTestTag: String = "btn_settings",
    horizontalPadding: Dp = 0.dp,
) {
    val colors = MorrowTheme.colors
    val fontScale = LocalDensity.current.fontScale
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(backTestTag),
            ) {
                MorrowIcon(
                    kind = MorrowIconKind.ARROW_LEFT,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = label,
            style = if (brand) MorrowType.Brand else MorrowType.SectionTitle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showDemoTag) {
            Surface(
                modifier = if (fontScale <= 1.1f) Modifier.offset(x = 10.dp) else Modifier,
                shape = CircleShape,
                color = colors.nested,
                contentColor = colors.textSecondary,
            ) {
                Text(
                    text = "演示数据",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (onSettingsClick != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = onSettingsClick,
                    )
                    .testTag(settingsTestTag),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "设置",
                    style = MorrowType.HeaderAction,
                    color = colors.forest,
                )
            }
        }
    }
}

/**
 * 页首：标题 + 一行说明。真实数据页面必须传 [showDemoTag] = false。
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    showDemoTag: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = MorrowType.PageTitle,
                color = MorrowTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (showDemoTag) DemoTag()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MorrowTheme.colors.textSecondary,
        )
    }
}

/** 区块标题 + 右侧轻量文本入口。 */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    testTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MorrowType.SectionTitle,
            color = MorrowTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(start = 8.dp, end = 2.dp, top = 8.dp, bottom = 8.dp)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MorrowTheme.colors.textSecondary,
                )
                Spacer(Modifier.width(5.dp))
                MorrowIcon(
                    kind = MorrowIconKind.ARROW_RIGHT,
                    tint = MorrowTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 通用卡片容器：白卡圆角 18dp、极细描边。 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MorrowTheme.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(MorrowSpacing.CardRadius),
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(modifier = Modifier.padding(MorrowSpacing.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(MorrowSpacing.BlockGap))
            content()
        }
    }
}

/** 小圆角状态标签。 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MorrowTheme.colors.nested,
    contentColor: Color = MorrowTheme.colors.textSecondary,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

/** 可点击的选项药丸：外层 48dp 触摸区，内部 36dp 视觉高度。 */
@Composable
fun MorrowChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MorrowTheme.colors
    val dark = isDarkTheme()
    val selectedContainer = if (dark) colors.insightCard else colors.forest
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                !enabled -> colors.nested
                selected -> selectedContainer
                else -> colors.card
            },
            contentColor = when {
                !enabled -> colors.textDisabled
                selected -> colors.insightText
                else -> colors.textSecondary
            },
            border = BorderStroke(
                1.dp,
                when {
                    !enabled -> colors.hairline
                    selected -> selectedContainer
                    else -> colors.hairline
                },
            ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

/** Primary：森林绿填充、52dp 高、14dp 圆角，用于页面唯一主行动。 */
@Composable
fun MorrowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    testTag: String? = null,
) {
    val colors = MorrowTheme.colors
    val dark = isDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            !enabled || loading -> colors.nested
            pressed -> colors.primaryPressed
            dark -> colors.insightCard
            else -> colors.forest
        },
        animationSpec = tween(durationMillis = 120),
        label = "primaryContainer",
    )
    val content = if (enabled && !loading) colors.insightText else colors.textDisabled
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(MorrowSpacing.ButtonRadius))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(MorrowSpacing.ButtonRadius),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = content,
                    strokeWidth = 1.8.dp,
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Secondary：纸色/描边、50dp 高、与 Primary 同圆角。 */
@Composable
fun MorrowSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = MorrowTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            pressed -> colors.nested
            else -> colors.card
        },
        animationSpec = tween(durationMillis = 120),
        label = "secondaryContainer",
    )
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(MorrowSpacing.ButtonRadius))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(MorrowSpacing.ButtonRadius),
        color = container,
        contentColor = if (enabled) colors.textPrimary else colors.textDisabled,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Tertiary：无卡片背景的文字操作，可带右箭头；触摸区仍为 48dp。 */
@Composable
fun MorrowTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = false,
    testTag: String? = null,
    contentColor: Color? = null,
) {
    val colors = MorrowTheme.colors
    val tint = if (enabled) contentColor ?: colors.forest else colors.textDisabled
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
        if (showArrow) {
            Spacer(Modifier.width(6.dp))
            MorrowIcon(
                kind = MorrowIconKind.ARROW_RIGHT,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 兼容旧调用：Quiet = Secondary。 */
@Composable
fun MorrowQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) = MorrowSecondaryButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    testTag = testTag,
)

/** 设置页统一输入框配色。 */
@Composable
fun morrowTextFieldColors(): TextFieldColors {
    val colors = MorrowTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textTertiary,
        focusedBorderColor = colors.forest,
        unfocusedBorderColor = colors.hairline,
        disabledBorderColor = colors.hairline,
        focusedContainerColor = colors.card,
        unfocusedContainerColor = colors.card,
        disabledContainerColor = colors.nested,
        cursorColor = colors.forest,
        focusedLabelColor = colors.forest,
        unfocusedLabelColor = colors.textSecondary,
        focusedPlaceholderColor = colors.textTertiary,
        unfocusedPlaceholderColor = colors.textTertiary,
        errorBorderColor = colors.danger,
        errorLabelColor = colors.danger,
        errorSupportingTextColor = colors.danger,
    )
}

/** 统一的纸色输入框。 */
@Composable
fun MorrowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 6,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    supportingTestTag: String? = null,
    testTag: String? = null,
) {
    val colors = MorrowTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) colors.danger else colors.textSecondary,
                    modifier = if (supportingTestTag != null) Modifier.testTag(supportingTestTag)
                    else Modifier,
                )
            }
        },
        colors = morrowTextFieldColors(),
    )
}

/** 键值行：设置页里大量复用。 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MorrowTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MorrowTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 设置分组：卡片 + 组标题。 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MorrowTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(MorrowSpacing.CardRadius),
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/**
 * 设置项行。dimmed 只做视觉弱化，仍然可点击，由调用方给出真实说明。
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    dimmed: Boolean = false,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = MorrowTheme.colors
    val titleColor = if (dimmed) colors.textSecondary else colors.textPrimary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) StatusPill(text = badge)
            trailing?.invoke()
        }
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

/** 组内分隔线。 */
@Composable
fun GroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 18.dp, end = 18.dp),
        thickness = 0.5.dp,
        color = MorrowTheme.colors.hairline,
    )
}

/** 顶部栏（兼容旧调用）：右侧统一是设置入口。 */
@OptIn(ExperimentalMaterial3Api::class)
/** 一个很细的横向进度条。 */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MorrowTheme.colors.accent,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(MorrowTheme.colors.nested, CircleShape),
        horizontalArrangement = Arrangement.Start,
    ) {
        Spacer(
            Modifier
                .fillMaxWidth(clamped)
                .height(3.dp)
                .background(color, CircleShape),
        )
    }
}

/** 空状态 / 说明块。 */
@Composable
fun InlineNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(
            Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(MorrowTheme.colors.textTertiary, CircleShape),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MorrowTheme.colors.textSecondary,
        )
    }
}

/** 温柔空态卡片。 */
@Composable
fun GentleEmptyCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = MorrowTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MorrowSpacing.CardRadius))
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(MorrowSpacing.CardRadius))
            .padding(22.dp),
    ) {
        Text(
            text = title,
            style = MorrowType.CardTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            MorrowPrimaryButton(text = actionLabel, onClick = onAction)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(10.dp))
            MorrowSecondaryButton(text = secondaryLabel, onClick = onSecondary)
        }
    }
}

/** 小标签行，例如「财务 · 稳步推进」。 */
@Composable
fun MetaLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MorrowTheme.colors.textSecondary,
        modifier = modifier,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 统一线性图标的语义集合。 */
enum class MorrowIconKind {
    HOME, TREND, BOOK, SPARKLE, WALLET, BRIEFCASE, LEAF, HEART,
    COMPASS, PENCIL, BRANCH, ARROW_RIGHT, ARROW_LEFT,
    CHEVRON_DOWN, CHEVRON_UP, MORE, PLUS, SEND,
}

/** 图标在默认、选中、弱化和禁用状态下的统一取色。 */
object MorrowIconColors {
    val Default: Color
        @Composable get() = MorrowTheme.colors.textSecondary
    val Active: Color
        @Composable get() = MorrowTheme.colors.forest
    val Muted: Color
        @Composable get() = MorrowTheme.colors.textTertiary
    val Disabled: Color
        @Composable get() = MorrowTheme.colors.textDisabled
    val OnDark: Color
        @Composable get() = MorrowTheme.colors.insightText
}

/**
 * 统一线性图标。
 *
 * 24×24 逻辑画布、2.1 单位描边、圆头圆角；所有图标共用同一套绘制规则，
 * 不引入 material-icons-extended，也不使用汉字/Emoji 占位。
 */
@Composable
fun MorrowIcon(
    kind: MorrowIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.then(semanticsModifier)) {
        val scale = minOf(size.width, size.height) / 24f
        if (scale <= 0f) return@Canvas
        val ox = (size.width - 24f * scale) / 2f
        val oy = (size.height - 24f * scale) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * scale, oy + y * scale)
        fun path(block: Path.() -> Unit): Path = Path().apply(block)
        val stroke = Stroke(width = 2.1f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), stroke.width, StrokeCap.Round)

        when (kind) {
            MorrowIconKind.HOME -> {
                drawPath(path {
                    moveTo(ox + 3.8f * scale, oy + 10.4f * scale)
                    lineTo(ox + 12f * scale, oy + 3.6f * scale)
                    lineTo(ox + 20.2f * scale, oy + 10.4f * scale)
                }, tint, style = stroke)
                drawPath(path {
                    moveTo(ox + 6f * scale, oy + 9.4f * scale)
                    lineTo(ox + 6f * scale, oy + 20.2f * scale)
                    lineTo(ox + 18f * scale, oy + 20.2f * scale)
                    lineTo(ox + 18f * scale, oy + 9.4f * scale)
                }, tint, style = stroke)
                line(10.4f, 20.2f, 10.4f, 14.6f)
                line(10.4f, 14.6f, 13.6f, 14.6f)
                line(13.6f, 14.6f, 13.6f, 20.2f)
            }

            MorrowIconKind.TREND -> {
                drawPath(path {
                    moveTo(ox + 3.4f * scale, oy + 17.2f * scale)
                    lineTo(ox + 8.8f * scale, oy + 11.4f * scale)
                    lineTo(ox + 12.6f * scale, oy + 14.2f * scale)
                    lineTo(ox + 20.4f * scale, oy + 6.2f * scale)
                }, tint, style = stroke)
                line(16.4f, 6.2f, 20.4f, 6.2f)
                line(20.4f, 6.2f, 20.4f, 10.2f)
            }

            MorrowIconKind.BOOK -> {
                drawPath(path {
                    moveTo(ox + 12f * scale, oy + 7f * scale)
                    cubicTo(ox + 9.4f * scale, oy + 5.3f * scale, ox + 5.3f * scale, oy + 5.2f * scale, ox + 4f * scale, oy + 6.4f * scale)
                    lineTo(ox + 4f * scale, oy + 18.2f * scale)
                    cubicTo(ox + 7.2f * scale, oy + 16.8f * scale, ox + 10f * scale, oy + 17.2f * scale, ox + 12f * scale, oy + 18.6f * scale)
                }, tint, style = stroke)
                drawPath(path {
                    moveTo(ox + 12f * scale, oy + 7f * scale)
                    cubicTo(ox + 14.6f * scale, oy + 5.3f * scale, ox + 18.7f * scale, oy + 5.2f * scale, ox + 20f * scale, oy + 6.4f * scale)
                    lineTo(ox + 20f * scale, oy + 18.2f * scale)
                    cubicTo(ox + 16.8f * scale, oy + 16.8f * scale, ox + 14f * scale, oy + 17.2f * scale, ox + 12f * scale, oy + 18.6f * scale)
                }, tint, style = stroke)
                line(12f, 7f, 12f, 18.6f)
            }

            MorrowIconKind.SPARKLE -> {
                drawPath(path {
                    moveTo(ox + 12f * scale, oy + 3.2f * scale)
                    quadraticTo(ox + 13.2f * scale, oy + 10.2f * scale, ox + 20.6f * scale, oy + 12f * scale)
                    quadraticTo(ox + 13.2f * scale, oy + 13.8f * scale, ox + 12f * scale, oy + 20.8f * scale)
                    quadraticTo(ox + 10.8f * scale, oy + 13.8f * scale, ox + 3.4f * scale, oy + 12f * scale)
                    quadraticTo(ox + 10.8f * scale, oy + 10.2f * scale, ox + 12f * scale, oy + 3.2f * scale)
                    close()
                }, tint, style = stroke)
            }

            MorrowIconKind.WALLET -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(3.2f, 6.4f),
                    size = Size(17.6f * scale, 13.2f * scale),
                    cornerRadius = CornerRadius(2.6f * scale),
                    style = stroke,
                )
                line(3.2f, 10.2f, 20.8f, 10.2f)
                drawCircle(tint, radius = 1.35f * scale, center = p(16.8f, 15f))
            }

            MorrowIconKind.BRIEFCASE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(3.2f, 7.6f),
                    size = Size(17.6f * scale, 12.4f * scale),
                    cornerRadius = CornerRadius(2.4f * scale),
                    style = stroke,
                )
                line(9f, 7.6f, 9f, 5.2f)
                line(9f, 5.2f, 15f, 5.2f)
                line(15f, 5.2f, 15f, 7.6f)
                line(3.2f, 12.2f, 20.8f, 12.2f)
            }

            MorrowIconKind.LEAF -> {
                drawPath(path {
                    moveTo(ox + 4.8f * scale, oy + 19.2f * scale)
                    cubicTo(ox + 3.6f * scale, oy + 11.8f * scale, ox + 8.2f * scale, oy + 4.6f * scale, ox + 19.4f * scale, oy + 4.4f * scale)
                    cubicTo(ox + 19.6f * scale, oy + 14f * scale, ox + 13f * scale, oy + 19.2f * scale, ox + 4.8f * scale, oy + 19.2f * scale)
                    close()
                }, tint, style = stroke)
                drawPath(path {
                    moveTo(ox + 5.2f * scale, oy + 18.8f * scale)
                    cubicTo(ox + 8f * scale, oy + 14.2f * scale, ox + 11.8f * scale, oy + 9.8f * scale, ox + 16.6f * scale, oy + 7.2f * scale)
                }, tint, style = stroke)
            }

            MorrowIconKind.HEART -> {
                drawPath(path {
                    moveTo(ox + 12f * scale, oy + 20f * scale)
                    cubicTo(ox + 10.2f * scale, oy + 18.2f * scale, ox + 3.8f * scale, oy + 14.2f * scale, ox + 3.8f * scale, oy + 9.2f * scale)
                    cubicTo(ox + 3.8f * scale, oy + 6.2f * scale, ox + 5.8f * scale, oy + 4.4f * scale, ox + 8.2f * scale, oy + 4.4f * scale)
                    cubicTo(ox + 10.2f * scale, oy + 4.4f * scale, ox + 11.4f * scale, oy + 5.8f * scale, ox + 12f * scale, oy + 7.2f * scale)
                    cubicTo(ox + 12.6f * scale, oy + 5.8f * scale, ox + 13.8f * scale, oy + 4.4f * scale, ox + 15.8f * scale, oy + 4.4f * scale)
                    cubicTo(ox + 18.2f * scale, oy + 4.4f * scale, ox + 20.2f * scale, oy + 6.2f * scale, ox + 20.2f * scale, oy + 9.2f * scale)
                    cubicTo(ox + 20.2f * scale, oy + 14.2f * scale, ox + 13.8f * scale, oy + 18.2f * scale, ox + 12f * scale, oy + 20f * scale)
                    close()
                }, tint, style = stroke)
            }

            MorrowIconKind.COMPASS -> {
                drawCircle(tint, radius = 8.4f * scale, center = p(12f, 12f), style = stroke)
                drawPath(path {
                    moveTo(ox + 15.6f * scale, oy + 8.4f * scale)
                    lineTo(ox + 13.4f * scale, oy + 13.4f * scale)
                    lineTo(ox + 8.4f * scale, oy + 15.6f * scale)
                    lineTo(ox + 10.6f * scale, oy + 10.6f * scale)
                    close()
                }, tint, style = stroke)
            }

            MorrowIconKind.PENCIL -> {
                drawPath(path {
                    moveTo(ox + 4f * scale, oy + 20f * scale)
                    lineTo(ox + 5.2f * scale, oy + 15.2f * scale)
                    lineTo(ox + 16f * scale, oy + 4.4f * scale)
                    lineTo(ox + 19.6f * scale, oy + 8f * scale)
                    lineTo(ox + 8.8f * scale, oy + 18.8f * scale)
                    close()
                }, tint, style = stroke)
                line(4f, 20f, 8.8f, 18.8f)
                line(16f, 4.4f, 19.6f, 8f)
            }

            MorrowIconKind.BRANCH -> {
                line(8f, 19f, 8f, 8f)
                drawCircle(tint, radius = 1.6f * scale, center = p(8f, 5.8f), style = stroke)
                drawCircle(tint, radius = 1.6f * scale, center = p(8f, 19.2f), style = stroke)
                drawPath(path {
                    moveTo(ox + 8f * scale, oy + 12f * scale)
                    cubicTo(ox + 15.6f * scale, oy + 12f * scale, ox + 16f * scale, oy + 9f * scale, ox + 16f * scale, oy + 7.4f * scale)
                }, tint, style = stroke)
                drawCircle(tint, radius = 1.6f * scale, center = p(16f, 5.8f), style = stroke)
            }

            MorrowIconKind.ARROW_RIGHT -> {
                line(4f, 12f, 20f, 12f)
                line(15.4f, 7.4f, 20f, 12f)
                line(20f, 12f, 15.4f, 16.6f)
            }

            MorrowIconKind.ARROW_LEFT -> {
                line(20f, 12f, 4f, 12f)
                line(8.6f, 7.4f, 4f, 12f)
                line(4f, 12f, 8.6f, 16.6f)
            }

            MorrowIconKind.CHEVRON_DOWN -> {
                line(7f, 9.6f, 12f, 14.6f)
                line(12f, 14.6f, 17f, 9.6f)
            }

            MorrowIconKind.CHEVRON_UP -> {
                line(7f, 14.4f, 12f, 9.4f)
                line(12f, 9.4f, 17f, 14.4f)
            }

            MorrowIconKind.MORE -> {
                drawCircle(tint, radius = 1.35f * scale, center = p(6f, 12f))
                drawCircle(tint, radius = 1.35f * scale, center = p(12f, 12f))
                drawCircle(tint, radius = 1.35f * scale, center = p(18f, 12f))
            }

            MorrowIconKind.PLUS -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }

            MorrowIconKind.SEND -> {
                drawPath(path {
                    moveTo(ox + 3.5f * scale, oy + 11.5f * scale)
                    lineTo(ox + 20.5f * scale, oy + 4f * scale)
                    lineTo(ox + 13.5f * scale, oy + 20.5f * scale)
                    lineTo(ox + 10.8f * scale, oy + 13.2f * scale)
                    close()
                }, tint, style = stroke)
                line(10.8f, 13.2f, 20.5f, 4f)
            }
        }
    }
}
