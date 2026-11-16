package com.mengyalife.app.ui.candidates

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mengyalife.app.ui.theme.LocalMorrowPalette
import com.mengyalife.app.ui.theme.MengyaLifeTheme
import com.mengyalife.app.ui.theme.MengyaTypography
import com.mengyalife.app.ui.theme.MorrowPalette

/** Complete semantic palette used by both runnable candidates; not selected for production yet. */
data class ProductColors(
    val background: Color,
    val surface: Color,
    val nested: Color,
    val border: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSurface: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

fun productColors(candidate: UiDesignCandidate, dark: Boolean): ProductColors = when {
    dark && candidate == UiDesignCandidate.B_COMPANION -> ProductColors(
        Color(0xFF171B1E), Color(0xFF22282B), Color(0xFF2D3537), Color(0xFF4C5855),
        Color(0xFFE9EEEA), Color(0xFFBDC7C0), Color(0xFFAFD0B3), Color(0xFF15251B),
        Color(0xFF304336), Color(0xFFB0D3AE), Color(0xFFE8C184), Color(0xFFF0AEA5),
    )
    dark -> ProductColors(
        Color(0xFF191B1B), Color(0xFF242828), Color(0xFF303534), Color(0xFF4D5650),
        Color(0xFFEBEEE8), Color(0xFFBEC6BC), Color(0xFFB6CCA9), Color(0xFF1C281A),
        Color(0xFF35432F), Color(0xFFB4D1A8), Color(0xFFE5C17D), Color(0xFFF1B0A5),
    )
    candidate == UiDesignCandidate.B_COMPANION -> ProductColors(
        Color(0xFFF7F8F4), Color(0xFFFFFEFA), Color(0xFFEDF2E9), Color(0xFFCDD6CB),
        Color(0xFF25332B), Color(0xFF536359), Color(0xFF44684D), Color(0xFFFFFFFF),
        Color(0xFFE4EEDC), Color(0xFF426A48), Color(0xFF805D27), Color(0xFFA13F36),
    )
    else -> ProductColors(
        Color(0xFFF5F3EE), Color(0xFFFFFEFA), Color(0xFFECEAE3), Color(0xFFD1D4CA),
        Color(0xFF292F2A), Color(0xFF566056), Color(0xFF536B50), Color(0xFFFFFFFF),
        Color(0xFFE6EBDD), Color(0xFF416447), Color(0xFF805E25), Color(0xFFA23E33),
    )
}

val LocalUiDesignCandidate = staticCompositionLocalOf { UiDesignCandidate.NONE }
val LocalProductColors = staticCompositionLocalOf { productColors(UiDesignCandidate.A_FOCUS, false) }

@Composable
fun DesignCandidateTheme(candidate: UiDesignCandidate, dark: Boolean, content: @Composable () -> Unit) {
    // All production screens retain the original implementation until a direction is selected.
    MengyaLifeTheme(darkTheme = dark) {
        if (candidate == UiDesignCandidate.NONE) {
            content()
        } else {
            val p = productColors(candidate, dark)
            val base = if (dark) darkColorScheme() else lightColorScheme()
            val scheme = base.copy(
                primary = p.accent, onPrimary = p.onAccent,
                primaryContainer = p.accentSurface, onPrimaryContainer = p.text,
                secondary = p.accent, onSecondary = p.onAccent,
                secondaryContainer = p.nested, onSecondaryContainer = p.text,
                tertiary = p.warning, onTertiary = p.surface,
                tertiaryContainer = p.nested, onTertiaryContainer = p.text,
                background = p.background, onBackground = p.text,
                surface = p.surface, onSurface = p.text,
                surfaceVariant = p.nested, onSurfaceVariant = p.secondary,
                surfaceDim = p.background, surfaceBright = p.surface,
                surfaceContainerLowest = p.background, surfaceContainerLow = p.surface,
                surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
                surfaceContainerHighest = p.nested, surfaceTint = p.accent,
                outline = p.secondary, outlineVariant = p.border,
                error = p.error, onError = p.surface, errorContainer = p.nested, onErrorContainer = p.error,
            )
            val legacy = MorrowPalette(
                canvas = p.background, card = p.surface, nested = p.nested,
                textPrimary = p.text, textSecondary = p.secondary, textTertiary = p.secondary,
                textDisabled = p.secondary.copy(alpha = .65f), forest = p.accent,
                forestPressed = p.accent, primaryPressed = p.accent,
                accent = p.accent, hairline = p.border,
                insightCard = p.accent, insightText = p.onAccent, insightMuted = p.onAccent,
                selectedNav = p.accentSurface, danger = p.error,
            )
            val typography = MengyaTypography.copy(
                headlineLarge = MengyaTypography.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, lineHeight = 34.sp),
                headlineMedium = MengyaTypography.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 23.sp, lineHeight = 31.sp),
                headlineSmall = MengyaTypography.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 21.sp, lineHeight = 29.sp),
                titleLarge = MengyaTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = MengyaTypography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
                titleSmall = MengyaTypography.titleSmall.copy(fontSize = 17.sp, lineHeight = 25.sp),
                bodyLarge = MengyaTypography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 27.sp),
                bodyMedium = MengyaTypography.bodyMedium.copy(fontSize = 16.sp, lineHeight = 25.sp),
                bodySmall = MengyaTypography.bodySmall.copy(fontSize = 14.sp, lineHeight = 22.sp),
                labelLarge = MengyaTypography.labelLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
                labelMedium = MengyaTypography.labelMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                labelSmall = MengyaTypography.labelSmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
            )
            CompositionLocalProvider(
                LocalUiDesignCandidate provides candidate,
                LocalProductColors provides p,
                LocalMorrowPalette provides legacy,
            ) {
                MaterialTheme(colorScheme = scheme, typography = typography, content = content)
            }
        }
    }
}
