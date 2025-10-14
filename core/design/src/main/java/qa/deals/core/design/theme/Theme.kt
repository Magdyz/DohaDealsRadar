package qa.deals.doha.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Modern Vinted-inspired theme for Doha Deals
 * Clean, minimal, purple palette
 */
@Composable
fun DohaDealsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = DohaTypography,
        shapes = DohaShapes,
        content = content
    )
}