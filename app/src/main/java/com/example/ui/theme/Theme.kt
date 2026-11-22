package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealOnContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = AmberOnSecondaryDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberOnContainerDark,
    tertiary = CoralTertiaryDark,
    onTertiary = CoralOnTertiaryDark,
    tertiaryContainer = CoralContainerDark,
    onTertiaryContainer = CoralOnContainerDark,
    background = ObsidianBg,
    onBackground = ObsidianText,
    surface = ObsidianSurface,
    onSurface = ObsidianText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextMuted,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutline.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = AmberOnSecondaryLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = CoralTertiaryLight,
    onTertiary = CoralOnTertiaryLight,
    tertiaryContainer = CoralContainerLight,
    onTertiaryContainer = CoralOnContainerLight,
    background = AlabasterBg,
    onBackground = AlabasterText,
    surface = AlabasterSurface,
    onSurface = AlabasterText,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = AlabasterTextMuted,
    outline = AlabasterOutline,
    outlineVariant = AlabasterOutline.copy(alpha = 0.6f)
)

val FolioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun FolioFlowTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FolioTypography,
        shapes = FolioShapes,
        content = content
    )
}
