package com.mengyalife.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * 主题模式。用户选择由 [com.mengyalife.app.data.preferences.ThemePreferences]
 * 写入 DataStore，**重启后保持**；选「跟随系统」时由系统深浅色实时决定实际配色。
 */
enum class ThemeMode(val label: String, val testTag: String) {
    SYSTEM("跟随系统", "theme_system"),
    LIGHT("浅色模式", "theme_light"),
    DARK("深色模式", "theme_dark"),
}

/** 超出 M3 语义色之外、各页面都要用的品牌令牌。 */
@Immutable
data class MorrowPalette(
    val canvas: Color,
    val card: Color,
    val nested: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val forest: Color,
    val forestPressed: Color,
    val primaryPressed: Color,
    val accent: Color,
    val hairline: Color,
    val insightCard: Color,
    val insightText: Color,
    val insightMuted: Color,
    val selectedNav: Color,
    val danger: Color,
)

private val LightPalette = MorrowPalette(
    canvas = MorrowLight.Canvas,
    card = MorrowLight.Card,
    nested = MorrowLight.Nested,
    textPrimary = MorrowLight.TextPrimary,
    textSecondary = MorrowLight.TextSecondary,
    textTertiary = MorrowLight.TextTertiary,
    textDisabled = MorrowLight.TextDisabled,
    forest = MorrowLight.Forest,
    forestPressed = MorrowLight.ForestPressed,
    primaryPressed = Color(0xFF274A3A),
    accent = MorrowLight.Accent,
    hairline = MorrowLight.Hairline,
    insightCard = MorrowLight.InsightCard,
    insightText = MorrowLight.InsightText,
    insightMuted = MorrowLight.InsightMuted,
    selectedNav = MorrowLight.SelectedNav,
    danger = MorrowLight.Danger,
)

private val DarkPalette = MorrowPalette(
    canvas = MorrowDark.Canvas,
    card = MorrowDark.Card,
    nested = MorrowDark.Nested,
    textPrimary = MorrowDark.TextPrimary,
    textSecondary = MorrowDark.TextSecondary,
    textTertiary = MorrowDark.TextTertiary,
    textDisabled = MorrowDark.TextDisabled,
    forest = MorrowDark.Forest,
    forestPressed = MorrowDark.ForestPressed,
    primaryPressed = Color(0xFF365947),
    accent = MorrowDark.Accent,
    hairline = MorrowDark.Hairline,
    insightCard = MorrowDark.InsightCard,
    insightText = MorrowDark.InsightText,
    insightMuted = MorrowDark.InsightMuted,
    selectedNav = MorrowDark.SelectedNav,
    danger = MorrowDark.Danger,
)

val LocalMorrowPalette = staticCompositionLocalOf { LightPalette }

/** 页面统一间距 / 圆角。所有页面从这里取值，不再各自复制相近数字。 */
object MorrowSpacing {
    val PageHorizontal = 22.dp
    val SectionGap = 26.dp
    val BlockGap = 14.dp
    val CardPadding = 20.dp
    val RowPadding = 16.dp
    val CardRadius = 18.dp
    val InsightRadius = 22.dp
    val PillRadius = 999.dp
    val MinTouch = 48.dp
    val ButtonRadius = 14.dp
    val ButtonHeight = 52.dp
    val SecondaryHeight = 50.dp
    val ButtonGap = 10.dp
    val IconSize = 20.dp
    val IconSmall = 16.dp
}

/** 取当前主题的品牌令牌。 */
object MorrowTheme {
    val colors: MorrowPalette
        @Composable @ReadOnlyComposable get() = LocalMorrowPalette.current
}

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

private val MengyaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * 主题入口。`darkTheme` 由上层根据 [ThemeMode] 决定，
 * 这样「跟随系统 / 浅色 / 深色」三态切换可以立即作用于整个 App。
 */
@Composable
fun MengyaLifeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMorrowPalette provides if (darkTheme) DarkPalette else LightPalette,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MengyaTypography,
            shapes = MengyaShapes,
            content = content,
        )
    }
}

/** 当前主题是否为深色，供图表等自绘组件取色。 */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun androidx.compose.ui.graphics.Color.luminance(): Float =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue)
