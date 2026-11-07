package com.mengyalife.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Morrow 风格设计令牌。
//
// 目标气质：微暖浅灰绿纸面、深森林绿洞察卡、低对比细描边、克制留白。
// 这里只放「原生可执行目标值」，不声称与原版逐像素一致。
// ---------------------------------------------------------------------------

/** 浅色主题令牌。 */
object MorrowLight {
    val Canvas = Color(0xFFF6F7F1)
    val Card = Color(0xFFFFFEFC)
    val Nested = Color(0xFFEDF2EA)
    val TextPrimary = Color(0xFF28372E)
    val TextSecondary = Color(0xFF7B857B)
    val TextTertiary = Color(0xFF9BA59A)
    val Forest = Color(0xFF345E4A)
    val ForestPressed = Color(0xFF274A3A)
    val Accent = Color(0xFF628F76)
    val Hairline = Color(0xFFE3E8DF)
    val InsightCard = Color(0xFF345E4A)
    val InsightText = Color(0xFFF9FBF7)
    val InsightMuted = Color(0xFFD7E2D8)
    val SelectedNav = Color(0xFFE6EDE5)
    val Danger = Color(0xFFA6473F)
}

/** 深色主题令牌。 */
object MorrowDark {
    val Canvas = Color(0xFF18221D)
    val Card = Color(0xFF202C25)
    val Nested = Color(0xFF28362D)
    val TextPrimary = Color(0xFFEEF3E9)
    val TextSecondary = Color(0xFFB0BEB1)
    val TextTertiary = Color(0xFF8B9A8D)
    val Forest = Color(0xFF8FB99C)
    val ForestPressed = Color(0xFFA9CBB2)
    val Accent = Color(0xFF9FC4AA)
    val Hairline = Color(0xFF3A4B3D)
    val InsightCard = Color(0xFF446E58)
    val InsightText = Color(0xFFF9FBF7)
    val InsightMuted = Color(0xFFD6E4D9)
    val SelectedNav = Color(0xFF2B3B31)
    val Danger = Color(0xFFE2A49E)
}

// M3 方案映射。保留旧令牌名，避免下层组件出现两套颜色 API。
val LightPrimary = MorrowLight.Forest
val LightOnPrimary = Color(0xFFFFFEFC)
val LightPrimaryContainer = Color(0xFFDDE9DF)
val LightOnPrimaryContainer = Color(0xFF1E3A2D)
val LightSecondary = MorrowLight.Accent
val LightOnSecondary = Color(0xFFFFFEFC)
val LightSecondaryContainer = Color(0xFFE7EEE4)
val LightOnSecondaryContainer = Color(0xFF2B4438)
val LightTertiary = Color(0xFF8A7A55)
val LightOnTertiary = Color(0xFFFFFEFC)
val LightTertiaryContainer = Color(0xFFF1EBDD)
val LightOnTertiaryContainer = Color(0xFF3A301B)
val LightBackground = MorrowLight.Canvas
val LightOnBackground = MorrowLight.TextPrimary
val LightSurface = MorrowLight.Card
val LightOnSurface = MorrowLight.TextPrimary
val LightSurfaceVariant = MorrowLight.Nested
val LightOnSurfaceVariant = MorrowLight.TextSecondary
val LightOutline = MorrowLight.TextTertiary
val LightOutlineVariant = MorrowLight.Hairline
val LightError = MorrowLight.Danger
val LightOnError = Color(0xFFFFFEFC)
val LightErrorContainer = Color(0xFFF6E3E0)
val LightOnErrorContainer = Color(0xFF5A1B16)

val DarkPrimary = MorrowDark.Forest
val DarkOnPrimary = Color(0xFF14251C)
val DarkPrimaryContainer = Color(0xFF304A3A)
val DarkOnPrimaryContainer = Color(0xFFDCE9DE)
val DarkSecondary = MorrowDark.Accent
val DarkOnSecondary = Color(0xFF14251C)
val DarkSecondaryContainer = Color(0xFF31463A)
val DarkOnSecondaryContainer = Color(0xFFDCE9DE)
val DarkTertiary = Color(0xFFD2C39C)
val DarkOnTertiary = Color(0xFF2F2713)
val DarkTertiaryContainer = Color(0xFF473C22)
val DarkOnTertiaryContainer = Color(0xFFF1EBDD)
val DarkBackground = MorrowDark.Canvas
val DarkOnBackground = MorrowDark.TextPrimary
val DarkSurface = MorrowDark.Card
val DarkOnSurface = MorrowDark.TextPrimary
val DarkSurfaceVariant = MorrowDark.Nested
val DarkOnSurfaceVariant = MorrowDark.TextSecondary
val DarkOutline = MorrowDark.TextTertiary
val DarkOutlineVariant = MorrowDark.Hairline
val DarkError = MorrowDark.Danger
val DarkOnError = Color(0xFF3B0A07)
val DarkErrorContainer = Color(0xFF5A2520)
val DarkOnErrorContainer = Color(0xFFF6DDDA)

// 图表用色：低饱和、同色系；深色主题下仍可辨。
val ChartGreen = Color(0xFF628F76)
val ChartOchre = Color(0xFFB49A6A)
val ChartSlate = Color(0xFF8D9A93)
