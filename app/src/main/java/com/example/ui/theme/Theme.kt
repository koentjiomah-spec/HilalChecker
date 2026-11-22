package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ObservatoryDarkColorScheme = darkColorScheme(
    primary = CrescentGold,
    onPrimary = Color(0xFF1A1202),
    primaryContainer = CrescentGoldContainer,
    onPrimaryContainer = CrescentGold,
    secondary = RukyatEmerald,
    onSecondary = Color(0xFF041D13),
    secondaryContainer = RukyatEmeraldContainer,
    onSecondaryContainer = RukyatEmerald,
    tertiary = TwilightAmber,
    onTertiary = Color(0xFF261202),
    tertiaryContainer = TwilightAmberContainer,
    onTertiaryContainer = TwilightAmber,
    error = IstikmalCoral,
    onError = Color(0xFF2B0709),
    errorContainer = IstikmalCoralContainer,
    onErrorContainer = IstikmalCoral,
    background = MidnightDeep,
    onBackground = StarWhite,
    surface = ObservatoryNavy,
    onSurface = StarWhite,
    surfaceVariant = ObservatoryCard,
    onSurfaceVariant = NebulaSlate,
    surfaceContainerLow = ObservatoryNavy,
    surfaceContainer = ObservatoryCard,
    surfaceContainerHigh = ObservatoryCardElevated,
    outline = ObservatoryOutline,
    outlineVariant = Color(0xFF1E2D52)
)

private val FajarLightColorScheme = lightColorScheme(
    primary = DeepIndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = DeepIndigoContainer,
    onPrimaryContainer = DeepIndigoPrimary,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldLightContainer,
    onSecondaryContainer = EmeraldLight,
    tertiary = WarmGoldAccent,
    onTertiary = Color.White,
    tertiaryContainer = WarmGoldContainer,
    onTertiaryContainer = WarmGoldAccent,
    error = CoralLight,
    onError = Color.White,
    errorContainer = CoralLightContainer,
    onErrorContainer = CoralLight,
    background = ParchmentLight,
    onBackground = InkDark,
    surface = ParchmentSurface,
    onSurface = InkDark,
    surfaceVariant = ParchmentCard,
    onSurfaceVariant = InkMuted,
    surfaceContainerLow = ParchmentSurface,
    surfaceContainer = ParchmentCard,
    surfaceContainerHigh = Color(0xFFE7E1D3),
    outline = ParchmentOutline,
    outlineVariant = Color(0xFFE4DEC9)
)

val HilalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HilalCheckerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ObservatoryDarkColorScheme else FajarLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HilalTypography,
        shapes = HilalShapes,
        content = content
    )
}
