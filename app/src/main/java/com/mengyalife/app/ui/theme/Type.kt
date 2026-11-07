package com.mengyalife.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// 字体层级：衬线只用于有文学感的标题与洞察句；正文、按钮和导航保持系统
// 中文无衬线字体，避免全局宋体造成阅读疲劳。
//
// Android 的 FontFamily.Serif 在中文环境会回退到设备自带宋体（通常为
// Noto Serif CJK），不额外引入字体文件，规避包体和授权问题。
// ---------------------------------------------------------------------------

val MorrowSerif = FontFamily.Serif
val MorrowSans = FontFamily.SansSerif

/** 额外语义样式：首页/页面标题、洞察句、区块标题等。 */
object MorrowType {
    /** 首页开场大标题：30sp / 41sp，常规字重，左对齐。 */
    val HomeTitle = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 30.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp,
    )

    /** 二级页主标题：27sp / 37sp。 */
    val PageTitle = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 27.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp,
    )

    /** 二级页小一号标题（探索/决定等单页流程）。 */
    val SheetTitle = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    )

    /** 深绿洞察卡核心句：23sp / 33sp。 */
    val InsightQuote = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 23.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.3.sp,
    )

    /** 品牌文字标记。 */
    val Brand = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
    )

    /** 区块标题：15–16sp 半粗无衬线。 */
    val SectionTitle = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    )

    /** 日期 / eyebrow：11–12sp，少量字距。 */
    val Eyebrow = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.6.sp,
    )

    /** 卡片里的轻柔标题（衬线，但比洞察句小）。 */
    val CardTitle = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 18.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    )
}

val MengyaTypography = Typography(
    headlineLarge = MorrowType.HomeTitle,
    headlineMedium = MorrowType.PageTitle,
    headlineSmall = TextStyle(
        fontFamily = MorrowSerif,
        fontSize = 22.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleLarge = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = MorrowType.SectionTitle,
    titleSmall = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
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
        fontSize = 12.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = MorrowSans,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
)
