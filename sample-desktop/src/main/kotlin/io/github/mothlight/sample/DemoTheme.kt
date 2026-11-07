package io.github.mothlight.sample

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The host owns the theme. The embedded panel consumes these same design tokens.
private val LightColors = lightColorScheme(
    primary = Color(0xFF356859), onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EADF), onPrimaryContainer = Color(0xFF102E25),
    secondary = Color(0xFF666048), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5CA), onSecondaryContainer = Color(0xFF302B19),
    tertiary = Color(0xFF815B20), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDA6), onTertiaryContainer = Color(0xFF2A1800),
    background = Color(0xFFF5F6F2), onBackground = Color(0xFF202923),
    surface = Color(0xFFFCFDF9), onSurface = Color(0xFF202923),
    surfaceVariant = Color(0xFFE0E7DF), onSurfaceVariant = Color(0xFF424C44),
    outline = Color(0xFF727D74), outlineVariant = Color(0xFFC1CBC1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA0D4BD), onPrimary = Color(0xFF093829),
    primaryContainer = Color(0xFF285040), onPrimaryContainer = Color(0xFFBCEFD6),
    secondary = Color(0xFFD0C7A8), onSecondary = Color(0xFF36301D),
    secondaryContainer = Color(0xFF4D4732), onSecondaryContainer = Color(0xFFEDE3C4),
    tertiary = Color(0xFFF0BE73), onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634215), onTertiaryContainer = Color(0xFFFFDDA6),
    background = Color(0xFF111914), onBackground = Color(0xFFE0E8DF),
    surface = Color(0xFF19221C), onSurface = Color(0xFFE0E8DF),
    surfaceVariant = Color(0xFF3C4940), onSurfaceVariant = Color(0xFFC0CCC1),
    outline = Color(0xFF89978C), outlineVariant = Color(0xFF3C4940),
)

@Composable
internal fun DemoTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
