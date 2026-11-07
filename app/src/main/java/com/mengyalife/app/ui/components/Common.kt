package com.mengyalife.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * 轻量顶栏：左侧是品牌或页面名，右侧统一保留设置入口。
 *
 * 首页传 [brand] = true 显示「萌芽.」；二级页可传 [onBack] 显示返回。
 */
@Composable
fun MorrowTopBar(
    label: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    brand: Boolean = false,
    showDemoTag: Boolean = false,
    backTestTag: String = "page_back",
) {
    val colors = MorrowTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MorrowSpacing.PageHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag(backTestTag),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = if (brand) MorrowType.Brand else MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showDemoTag) {
            Surface(
                shape = CircleShape,
                color = colors.nested,
                contentColor = colors.textSecondary,
            ) {
                Text(
                    text = "演示数据",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        SettingsAvatar(onClick = onSettingsClick)
    }
}

/** 圆形文字头像；它同时是设置入口，保留 btn_settings 供自动化定位。 */
@Composable
fun SettingsAvatar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MorrowTheme.colors
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colors.nested)
            .border(1.dp, colors.hairline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "设置" }
            .testTag("btn_settings"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "芽",
            style = TextStyle(
                fontFamily = MorrowType.Brand.fontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = colors.forest,
        )
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
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MorrowTheme.colors.textSecondary,
                )
                Spacer(Modifier.width(4.dp))
                MorrowGlyph(
                    kind = MorrowGlyphKind.ARROW,
                    tint = MorrowTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp),
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

/** 可点击的选项药丸：用于时间范围、领域选择等。 */
@Composable
fun MorrowChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    val dark = isDarkTheme()
    val selectedContainer = if (dark) colors.insightCard else colors.forest
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) selectedContainer else colors.card,
        contentColor = if (selected) colors.insightText else colors.textSecondary,
        border = BorderStroke(1.dp, if (selected) selectedContainer else colors.hairline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .defaultMinSize(minHeight = 34.dp)
                .padding(horizontal = 15.dp, vertical = 8.dp),
        )
    }
}

/** 主按钮：森林绿、低阴影、圆角 16dp。 */
@Composable
fun MorrowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = MorrowTheme.colors
    val dark = isDarkTheme()
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = when {
            !enabled -> colors.nested
            dark -> colors.insightCard
            else -> colors.forest
        },
        contentColor = if (enabled) colors.insightText else colors.textTertiary,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
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

/** 次按钮：纸色底 + 细线。 */
@Composable
fun MorrowQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val colors = MorrowTheme.colors
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.Transparent,
        contentColor = colors.textPrimary,
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
@Composable
fun AppTopBar(
    title: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MorrowTheme.colors.textPrimary,
            )
        },
        actions = {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("btn_settings"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MorrowTheme.colors.textSecondary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MorrowTheme.colors.canvas,
            actionIconContentColor = MorrowTheme.colors.textSecondary,
        ),
    )
}

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
            .height(4.dp)
            .background(MorrowTheme.colors.nested, CircleShape),
        horizontalArrangement = Arrangement.Start,
    ) {
        Spacer(
            Modifier
                .fillMaxWidth(clamped)
                .height(4.dp)
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
            MorrowQuietButton(text = secondaryLabel, onClick = onSecondary)
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

/** 品牌细线图标类型。 */
enum class MorrowGlyphKind { HOME, CHART, BOOK, SPARKLE, PEN, COMPASS, FORK, ARROW }

/**
 * 统一的细线图标（Canvas 绘制）。
 * 不引入 material-icons-extended，避免为了几个图标显著增加包体。
 */
@Composable
fun MorrowGlyph(
    kind: MorrowGlyphKind,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 1.5.dp,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val sw = strokeWidth.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(w * x, h * y)

        when (kind) {
            MorrowGlyphKind.HOME -> {
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.46f)
                    lineTo(w * 0.5f, h * 0.2f)
                    lineTo(w * 0.82f, h * 0.46f)
                    lineTo(w * 0.82f, h * 0.8f)
                    lineTo(w * 0.18f, h * 0.8f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }

            MorrowGlyphKind.CHART -> {
                val path = Path().apply {
                    moveTo(w * 0.14f, h * 0.72f)
                    lineTo(w * 0.38f, h * 0.46f)
                    lineTo(w * 0.56f, h * 0.58f)
                    lineTo(w * 0.84f, h * 0.27f)
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(0.14f, 0.86f), p(0.86f, 0.86f), sw, StrokeCap.Round)
            }

            MorrowGlyphKind.BOOK -> {
                val left = Path().apply {
                    moveTo(w * 0.5f, h * 0.27f)
                    cubicTo(w * 0.38f, h * 0.2f, w * 0.22f, h * 0.2f, w * 0.15f, h * 0.26f)
                    lineTo(w * 0.15f, h * 0.77f)
                    cubicTo(w * 0.26f, h * 0.72f, w * 0.4f, h * 0.73f, w * 0.5f, h * 0.8f)
                }
                val right = Path().apply {
                    moveTo(w * 0.5f, h * 0.27f)
                    cubicTo(w * 0.62f, h * 0.2f, w * 0.78f, h * 0.2f, w * 0.85f, h * 0.26f)
                    lineTo(w * 0.85f, h * 0.77f)
                    cubicTo(w * 0.74f, h * 0.72f, w * 0.6f, h * 0.73f, w * 0.5f, h * 0.8f)
                }
                drawPath(left, tint, style = stroke)
                drawPath(right, tint, style = stroke)
            }

            MorrowGlyphKind.SPARKLE -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    quadraticBezierTo(w * 0.56f, h * 0.44f, w * 0.88f, h * 0.5f)
                    quadraticBezierTo(w * 0.56f, h * 0.56f, w * 0.5f, h * 0.88f)
                    quadraticBezierTo(w * 0.44f, h * 0.56f, w * 0.12f, h * 0.5f)
                    quadraticBezierTo(w * 0.44f, h * 0.44f, w * 0.5f, h * 0.12f)
                    close()
                }
                drawPath(path, tint, style = Stroke(width = sw * 0.85f, join = StrokeJoin.Round))
            }

            MorrowGlyphKind.PEN -> {
                val path = Path().apply {
                    moveTo(w * 0.24f, h * 0.76f)
                    lineTo(w * 0.32f, h * 0.5f)
                    lineTo(w * 0.68f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.32f)
                    lineTo(w * 0.5f, h * 0.68f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(0.24f, 0.76f), p(0.38f, 0.86f), sw, StrokeCap.Round)
                drawLine(tint, p(0.38f, 0.86f), p(0.44f, 0.7f), sw, StrokeCap.Round)
            }

            MorrowGlyphKind.COMPASS -> {
                drawCircle(tint, radius = w * 0.36f, style = stroke)
                val needle = Path().apply {
                    moveTo(w * 0.62f, h * 0.38f)
                    lineTo(w * 0.44f, h * 0.44f)
                    lineTo(w * 0.38f, h * 0.62f)
                    lineTo(w * 0.56f, h * 0.56f)
                    close()
                }
                drawPath(needle, tint, style = Stroke(width = sw * 0.9f, join = StrokeJoin.Round))
            }

            MorrowGlyphKind.FORK -> {
                drawLine(tint, p(0.5f, 0.84f), p(0.5f, 0.52f), sw, StrokeCap.Round)
                drawLine(tint, p(0.5f, 0.52f), p(0.28f, 0.26f), sw, StrokeCap.Round)
                drawLine(tint, p(0.5f, 0.52f), p(0.72f, 0.26f), sw, StrokeCap.Round)
                drawCircle(tint, radius = sw * 1.15f, center = p(0.28f, 0.26f))
                drawCircle(tint, radius = sw * 1.15f, center = p(0.72f, 0.26f))
            }

            MorrowGlyphKind.ARROW -> {
                drawLine(tint, p(0.18f, 0.5f), p(0.82f, 0.5f), sw, StrokeCap.Round)
                drawLine(tint, p(0.62f, 0.3f), p(0.82f, 0.5f), sw, StrokeCap.Round)
                drawLine(tint, p(0.82f, 0.5f), p(0.62f, 0.7f), sw, StrokeCap.Round)
            }
        }
    }
}
