package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val SpaceXAiDarkColorScheme = darkColorScheme(
    primary = LimeGreen,
    onPrimary = DarkTextOnLime,
    primaryContainer = OrbitalSlate,
    onPrimaryContainer = LimeGreen,
    secondary = NeonGreen,
    onSecondary = DarkTextOnLime,
    secondaryContainer = ElevatedNavy,
    onSecondaryContainer = CrispWhite,
    tertiary = OrbitalCyan,
    onTertiary = DarkTextOnLime,
    background = MidnightBlue,
    onBackground = CrispWhite,
    surface = DeepNavy,
    onSurface = CrispWhite,
    surfaceVariant = OrbitalSlate,
    onSurfaceVariant = SoftGrey,
    surfaceContainerLowest = MidnightBlue,
    surfaceContainerLow = DeepNavy,
    surfaceContainer = OrbitalSlate,
    surfaceContainerHigh = ElevatedNavy,
    outline = GlassBorder,
    outlineVariant = MutedSlate,
    error = AlertCrimson,
    onError = CrispWhite
)

private val SpaceXAiLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightCard,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightPrimary,
    secondary = LightSecondary,
    onSecondary = LightCard,
    secondaryContainer = ColorTokens.LightSecondaryContainer,
    onSecondaryContainer = LightTextPrimary,
    tertiary = OrbitalCyan,
    onTertiary = DarkTextOnLime,
    background = LightCloud,
    onBackground = LightTextPrimary,
    surface = LightCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightCard,
    surfaceContainerLow = LightCloud,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = ColorTokens.LightSurfaceHigh,
    outline = ColorTokens.LightOutline,
    outlineVariant = ColorTokens.LightOutlineVariant,
    error = AlertCrimson,
    onError = CrispWhite
)

private object ColorTokens {
    val LightOutline = Color(0xFFCBD5E1)
    val LightOutlineVariant = Color(0xFFE2E8F0)
    val LightSurfaceHigh = Color(0xFFD8E2EE)
    val LightSecondaryContainer = Color(0xFFD9EBDD)
}

val SpaceXAiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SpaceXAiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SpaceXAiDarkColorScheme else SpaceXAiLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpaceXAiTypography,
        shapes = SpaceXAiShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

