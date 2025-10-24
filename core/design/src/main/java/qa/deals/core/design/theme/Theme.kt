package qa.deals.doha.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
// ✅ ADD THESE IMPORTS
import qa.deals.core.design.theme.DohaTypography
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