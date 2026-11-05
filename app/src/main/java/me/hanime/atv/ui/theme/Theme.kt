package me.hanime.atv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Accent = Color(0xFFFF5C8A)
    val AccentSoft = Color(0xFFFF8FB0)
    val Violet = Color(0xFF7C4DFF)
    val Cyan = Color(0xFF4FD1FF)
    val Background = Color(0xFF090910)
    val BackgroundElevated = Color(0xFF12121D)
    val Surface = Color(0xFF171726)
    val SurfaceHigh = Color(0xFF202033)
    val TextPrimary = Color(0xFFF5F5FA)
    val TextSecondary = Color(0xFFA8A8BC)
    val TextTertiary = Color(0xFF6F6F85)
    val Success = Color(0xFF4ADE80)
    val Danger = Color(0xFFFF6B6B)

    val heroGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xCC090910),
            Background,
        ),
    )

    val accentGradient = Brush.horizontalGradient(listOf(Accent, Violet))
}

private val DarkColors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.Violet,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
)

@Composable
fun HanimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography.copy(
            displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary),
            headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary),
            titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary),
            bodyMedium = TextStyle(fontSize = 14.sp, color = Palette.TextPrimary),
            bodySmall = TextStyle(fontSize = 12.sp, color = Palette.TextSecondary),
            labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary),
        ),
    ) {
        content()
    }
}
