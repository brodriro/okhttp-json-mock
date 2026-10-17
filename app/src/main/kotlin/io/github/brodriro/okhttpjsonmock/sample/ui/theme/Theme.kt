package io.github.brodriro.okhttpjsonmock.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Slate neutrals with one green accent: a quiet developer tool where status colours carry the meaning.
private val LightColors = lightColorScheme(
    primary = Color(0xFF166534),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF334155),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFEEF2F6),
    surfaceContainerHigh = Color(0xFFE8EDF2),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFB91C1C),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0B1120),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF060A16),
    surfaceContainerLow = Color(0xFF0F172A),
    surfaceContainer = Color(0xFF131C2E),
    surfaceContainerHigh = Color(0xFF1A2436),
    surfaceContainerHighest = Color(0xFF1E293B),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

/** Colours of HTTP status classes, as a pair of container and content colour. */
@Immutable
data class StatusColors(
    val success: Pair<Color, Color>,
    val redirect: Pair<Color, Color>,
    val clientError: Pair<Color, Color>,
    val serverError: Pair<Color, Color>,
)

private val LightStatus = StatusColors(
    success = Color(0xFFDCFCE7) to Color(0xFF14532D),
    redirect = Color(0xFFDBEAFE) to Color(0xFF1E3A8A),
    clientError = Color(0xFFFEF3C7) to Color(0xFF78350F),
    serverError = Color(0xFFFEE2E2) to Color(0xFF7F1D1D),
)

private val DarkStatus = StatusColors(
    success = Color(0xFF14532D) to Color(0xFFBBF7D0),
    redirect = Color(0xFF1E3A8A) to Color(0xFFDBEAFE),
    clientError = Color(0xFF713F12) to Color(0xFFFEF3C7),
    serverError = Color(0xFF7F1D1D) to Color(0xFFFEE2E2),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val SampleTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Monospace text for paths, file names, headers and bodies. */
val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

object SampleTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable
        get() = LocalStatusColors.current
}

@Composable
fun SampleTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SampleTypography,
            content = content,
        )
    }
}
