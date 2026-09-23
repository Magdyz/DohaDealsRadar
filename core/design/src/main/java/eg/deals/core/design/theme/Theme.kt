package eg.deals.radar.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import eg.deals.core.design.theme.EgyptArabicTypography
import eg.deals.core.design.theme.EgyptTypography
import eg.deals.core.design.theme.isArabicUi

/**
 * Modern Vinted-inspired theme for EgyptDealRadar
 * Clean, minimal, purple palette
 * Uses the Arabic typography (Cairo) when the app language is Arabic.
 */
@Composable
fun EgyptDealsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = if (isArabicUi()) EgyptArabicTypography else EgyptTypography,
        shapes = EgyptShapes,
        content = content
    )
}
