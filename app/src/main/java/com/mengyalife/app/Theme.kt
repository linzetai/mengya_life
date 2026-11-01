package com.mengyalife.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 「萌芽」配色：以嫩叶绿为主色，米白为底。
private val LightColors = lightColorScheme(
    primary = Color(0xFF4C6A2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE8A9),
    onPrimaryContainer = Color(0xFF15200A),
    secondary = Color(0xFF5B6248),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFDFCF5),
    onBackground = Color(0xFF1B1C17),
    surface = Color(0xFFFDFCF5),
    onSurface = Color(0xFF1B1C17),
    surfaceVariant = Color(0xFFE7E8DA),
    onSurfaceVariant = Color(0xFF45483B),
    outline = Color(0xFF75786A),
)

/**
 * Gate 0 主题：固定 Light 配色，不启用动态取色（动态取色需 API 31+，
 * 且会让「按钮点击后状态变化」的视觉验证依赖机型）。
 */
@Composable
fun MengyaLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
