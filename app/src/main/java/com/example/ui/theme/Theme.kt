package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val WildHearthDarkColorScheme = darkColorScheme(
    primary = CampfireEmber,
    onPrimary = NightVoid,
    primaryContainer = CampfireEmberDark,
    onPrimaryContainer = ParchmentInk,
    secondary = LanternGold,
    onSecondary = NightVoid,
    secondaryContainer = LanternGoldDark,
    onSecondaryContainer = ParchmentInk,
    tertiary = SanityViolet,
    onTertiary = NightVoid,
    background = NightVoid,
    onBackground = ParchmentInk,
    surface = HearthCharcoal,
    onSurface = ParchmentInk,
    surfaceVariant = BarkSurface,
    onSurfaceVariant = MutedAsh,
    surfaceContainer = BarkSurface,
    surfaceContainerHigh = BarkSurfaceVariant,
    outline = OutlineStone,
    error = ErrorCrimson,
    onError = ParchmentInk
)

private val WildHearthShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WildHearthTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WildHearthDarkColorScheme,
        typography = WildHearthTypography,
        shapes = WildHearthShapes,
        content = content
    )
}
