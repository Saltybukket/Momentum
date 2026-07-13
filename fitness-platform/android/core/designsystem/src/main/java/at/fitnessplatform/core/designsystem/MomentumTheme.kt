package at.fitnessplatform.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val MomentumLightColors = lightColorScheme(
    primary = Color(0xFF142B4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5F0),
    onPrimaryContainer = Color(0xFF08182B),
    secondary = Color(0xFF805A16),
    secondaryContainer = Color(0xFFFFE2A8),
    tertiary = Color(0xFF52657C),
    tertiaryContainer = Color(0xFFD9E3F0),
    surface = Color(0xFFF6F8FB),
    surfaceContainer = Color(0xFFEBEFF4),
    surfaceContainerHigh = Color(0xFFE1E6ED),
    error = Color(0xFFBA1A1A),
)

private val MomentumDarkColors = darkColorScheme(
    primary = Color(0xFFDCE5F0),
    onPrimary = Color(0xFF142B4A),
    primaryContainer = Color(0xFF243A57),
    onPrimaryContainer = Color(0xFFF4F7FB),
    secondary = Color(0xFFE2AD52),
    secondaryContainer = Color(0xFF5C4215),
    tertiary = Color(0xFFB8C7D9),
    tertiaryContainer = Color(0xFF354961),
    surface = Color(0xFF071426),
    surfaceContainer = Color(0xFF0E1E33),
    surfaceContainerHigh = Color(0xFF17283F),
    error = Color(0xFFFFB4AB),
)

object MomentumSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object MomentumElevation {
    val card = 1.dp
    val emphasized = 3.dp
    val modal = 6.dp
}

object MomentumMotion {
    const val FAST = 150
    const val STANDARD = 220
    const val EMPHASIZED = 300
}

@Immutable
data class MomentumSemanticColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val activeWorkout: Color,
    val offline: Color,
)

private val LightSemanticColors = MomentumSemanticColors(
    success = Color(0xFF37664B),
    warning = Color(0xFF805A16),
    info = Color(0xFF405F7C),
    activeWorkout = Color(0xFF142B4A),
    offline = Color(0xFF5A6573),
)
private val DarkSemanticColors = MomentumSemanticColors(
    success = Color(0xFF8BC9A4),
    warning = Color(0xFFE2AD52),
    info = Color(0xFFA9C7E2),
    activeWorkout = Color(0xFFDCE5F0),
    offline = Color(0xFFB8C1CC),
)

val LocalMomentumSemanticColors = staticCompositionLocalOf { LightSemanticColors }
val LocalReducedMotion = staticCompositionLocalOf { false }

object MomentumThemeValues {
    val semanticColors: MomentumSemanticColors
        @Composable get() = LocalMomentumSemanticColors.current
    val reducedMotion: Boolean
        @Composable get() = LocalReducedMotion.current
}

@Composable
fun MomentumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val typography = Typography(
        displaySmall = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMomentumSemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) MomentumDarkColors else MomentumLightColors,
            typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(26.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
