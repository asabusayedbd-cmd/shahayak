package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = EmeraldDarkOnPrimary,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldDarkOnContainer,
    secondary = AmberDarkSecondary,
    onSecondary = AmberDarkOnSecondary,
    secondaryContainer = AmberDarkContainer,
    onSecondaryContainer = AmberDarkOnContainer,
    tertiary = SlateBlueDarkTertiary,
    tertiaryContainer = SlateBlueDarkContainer,
    background = DeepForestBackground,
    onBackground = SoftIvoryText,
    surface = DeepForestSurface,
    onSurface = SoftIvoryText,
    surfaceVariant = DeepForestSurfaceVariant,
    onSurfaceVariant = MutedSageText,
    outline = DeepForestOutline,
    error = CoralError,
    onError = CoralOnError
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = AmberSecondary,
    onSecondary = AmberOnSecondary,
    secondaryContainer = AmberSecondaryContainer,
    onSecondaryContainer = AmberOnSecondaryContainer,
    tertiary = SlateBlueTertiary,
    onTertiary = SlateBlueOnTertiary,
    tertiaryContainer = SlateBlueContainer,
    onTertiaryContainer = SlateBlueOnContainer,
    background = WarmOffWhiteBackground,
    onBackground = InkCharcoal,
    surface = CardSurfaceLight,
    onSurface = InkCharcoal,
    surfaceVariant = SoftSageSurfaceVariant,
    onSurfaceVariant = MutedSlate,
    outline = SageOutline,
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CoralOnErrorContainer
)

@Composable
fun SahayakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
