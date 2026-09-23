// ========================================
// ✅ START OF FIX 1
// ========================================
// ✨ CHANGED: This package now matches your file's folder structure.
package eg.deals.core.design.theme
// ========================================
// ✅ END OF FIX 1
// ========================================

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
// ========================================
// ✅ START OF FIX 2
// ========================================
// ✨ 1. Import the R file from your core.design module.
// This path is now correct because the package above is fixed.
import eg.deals.radar.core.design.R

// ✨ 2. Define the new "Inter" font family.
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

// ✨ 3. Arabic UI font: Cairo (variable font, SIL Open Font License)
@OptIn(ExperimentalTextApi::class)
private fun cairo(weight: FontWeight) = Font(
    R.font.cairo_variable,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

private val Cairo = FontFamily(
    cairo(FontWeight.Normal),
    cairo(FontWeight.Medium),
    cairo(FontWeight.SemiBold),
    cairo(FontWeight.Bold)
)
// ========================================
// ✅ END OF FIX 2
// ========================================

/**
 * Modern 2025 Typography System
 * Clean, readable, with generous line heights
 */

/** English typography (Inter). */
val EgyptTypography = buildTypography(Inter)

/** Arabic typography (Cairo) - used when the app language is Arabic. */
val EgyptArabicTypography = buildTypography(Cairo)

private fun buildTypography(font: FontFamily) = Typography(
    // Display - Large hero text
    displayLarge = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),

    // Headlines
    headlineLarge = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // Titles
    titleLarge = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body
    bodyLarge = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Labels
    labelLarge = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = font,
        textDirection = TextDirection.Content, // mixed EN/AR user text reads correctly in both layouts
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)