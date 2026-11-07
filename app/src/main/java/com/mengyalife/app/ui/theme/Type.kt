package com.mengyalife.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// 三个字体角色：
//   Brand   — 品牌文字 MengYa（衬线，Medium）
//   Display — 页面大标题 / 发现卡叙述（中文衬线，Regular）
//   UI      — 导航、章节、卡片、正文、表单、按钮、日期（中文无衬线）
//
// Android 的 FontFamily.Serif 会回退到设备自带中文宋体（通常 Noto Serif CJK），
// 不引入额外字体文件，保证字形来源一致且不增加包体。
// ---------------------------------------------------------------------------

val MorrowSerif = FontFamily.Serif
val MorrowSans = FontFamily.SansSerif

object MorrowType {
    /** A. 品牌文字：MengYa。 */
    val Brand = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    )

    /** B. 页面展示标题：30sp / 40sp 衬线常规字重。 */
    val Display = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp,
    )

    /** B. 二级页/弹层的大标题：26sp / 35sp 衬线常规字重。 */
    val DisplaySmall = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 26.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    )

    /** B. 发现卡叙述：26sp / 35sp 衬线常规字重。 */
    val InsightQuote = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 26.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    )

    /** C. 章节标题：18sp / 26sp 半粗无衬线。 */
    val SectionTitle = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    )

    /** C. 卡片标题：16sp / 24sp 中等字重无衬线。 */
    val CardTitle = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    )

    /** C. 日期数字：20sp / 26sp 无衬线，日记时间线与日期信息共用。 */
    val DateNumber = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    )

    /** C. 顶栏文字操作（设置）：14sp / 20sp 中等字重。 */
    val HeaderAction = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    )

    /** C. 日期 / eyebrow：12sp / 18sp，小型大写感。 */
    val Eyebrow = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.6.sp,
    )

    // 语义别名，避免页面再发明新字号。
    val HomeTitle get() = Display
    val PageTitle get() = Display
    val SheetTitle get() = DisplaySmall
}

val MengyaTypography = Typography(
    headlineLarge = MorrowType.Display,
    headlineMedium = MorrowType.Display,
    headlineSmall = MorrowType.DisplaySmall,
    titleLarge = MorrowType.SectionTitle,
    titleMedium = MorrowType.CardTitle,
    titleSmall = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
)
