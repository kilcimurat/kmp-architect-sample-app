package com.mkilci.kmparchitect.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens and theme only — deliberately no components.
 *
 * Every UI module depends on this one, so it is the single worst place for churn: a change here
 * recompiles the whole UI tree. Components live in `core:ui`, which changes often and is depended
 * on by fewer modules. Keeping the split is a build-isolation decision, not a stylistic one.
 */
object AppSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing }

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5AAC),
    secondary = Color(0xFF4A6572),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB7F0),
    secondary = Color(0xFFB0C4CE),
)

@Composable
fun AppTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}

object AppTheme {
    val spacing: AppSpacing
        @Composable @ReadOnlyComposable get() = LocalAppSpacing.current
}
