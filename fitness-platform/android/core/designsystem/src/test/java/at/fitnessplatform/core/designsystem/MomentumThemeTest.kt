package at.fitnessplatform.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumThemeTest {
    @Test
    fun `key light theme pairs meet WCAG AA contrast`() {
        assertContrast(MomentumLightColors.primary, MomentumLightColors.onPrimary)
        assertContrast(MomentumLightColors.background, MomentumLightColors.onBackground)
        assertContrast(MomentumLightColors.surface, MomentumLightColors.onSurface)
        assertContrast(MomentumLightColors.error, MomentumLightColors.onError)
    }

    @Test
    fun `key dark theme pairs meet WCAG AA contrast`() {
        assertContrast(MomentumDarkColors.primary, MomentumDarkColors.onPrimary)
        assertContrast(MomentumDarkColors.background, MomentumDarkColors.onBackground)
        assertContrast(MomentumDarkColors.surface, MomentumDarkColors.onSurface)
        assertContrast(MomentumDarkColors.error, MomentumDarkColors.onError)
    }

    private fun assertContrast(background: Color, foreground: Color) {
        val lighter = maxOf(luminance(background), luminance(foreground))
        val darker = minOf(luminance(background), luminance(foreground))
        assertTrue("contrast was ${(lighter + 0.05) / (darker + 0.05)}", (lighter + 0.05) / (darker + 0.05) >= 4.5)
    }

    private fun luminance(color: Color): Double = sequenceOf(color.red, color.green, color.blue)
        .map { channel -> if (channel <= 0.04045f) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4) }
        .mapIndexed { index, channel -> channel * listOf(0.2126, 0.7152, 0.0722)[index] }
        .sum()
}
