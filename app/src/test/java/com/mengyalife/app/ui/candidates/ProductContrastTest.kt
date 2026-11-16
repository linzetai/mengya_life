package com.mengyalife.app.ui.candidates

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ProductContrastTest {
    private fun luminance(color: Color): Double {
        fun linear(c: Float): Double = c.toDouble().let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
        return .2126 * linear(color.red) + .7152 * linear(color.green) + .0722 * linear(color.blue)
    }
    private fun ratio(a: Color, b: Color): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    @Test fun primaryAndSecondaryBusinessTextMeetAaOnEverySurface() {
        listOf(UiDesignCandidate.A_FOCUS, UiDesignCandidate.B_COMPANION).forEach { candidate ->
            listOf(false, true).forEach { dark ->
                val p = productColors(candidate, dark)
                listOf(p.background, p.surface, p.nested).forEach { background ->
                    listOf(p.text, p.secondary).forEach { foreground ->
                        assertTrue("$candidate dark=$dark contrast=${ratio(foreground, background)}", ratio(foreground, background) >= 4.5)
                    }
                }
            }
        }
    }
    @Test fun accentAndImportantStatusTextMeetAa() {
        listOf(UiDesignCandidate.A_FOCUS, UiDesignCandidate.B_COMPANION).forEach { candidate ->
            listOf(false, true).forEach { dark ->
                val p = productColors(candidate, dark)
                assertTrue(ratio(p.onAccent, p.accent) >= 4.5)
                assertTrue(ratio(p.accent, p.accentSurface) >= 4.5)
                assertTrue(ratio(p.text, p.accentSurface) >= 4.5)
                listOf(p.accent, p.success, p.warning, p.error).forEach { foreground ->
                    assertTrue("$candidate dark=$dark status contrast=${ratio(foreground, p.surface)}", ratio(foreground, p.surface) >= 4.5)
                }
            }
        }
    }
}
