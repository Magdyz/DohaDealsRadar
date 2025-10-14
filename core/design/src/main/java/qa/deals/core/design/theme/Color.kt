package qa.deals.doha.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// 🎨 Vinted-inspired Purple Palette (Modern 2025)
// Light Theme
val PurplePrimary = Color(0xFF7C66FF)        // Soft vibrant purple
val PurplePrimaryDark = Color(0xFF6852E8)    // Deeper purple for contrast
val PurpleLight = Color(0xFFF5F3FF)          // Very light purple tint
val PurpleContainer = Color(0xFFE9E4FF)      // Light purple container

val PeachAccent = Color(0xFFFF8B7C)          // Warm coral/peach
val PeachLight = Color(0xFFFFE5E0)           // Light peach background

// Neutrals (Clean & Minimal)
val BgLight = Color(0xFFFAFBFC)              // Very light gray-blue
val SurfaceLight = Color(0xFFFFFFFF)         // Pure white
val TextPrimary = Color(0xFF0F1419)          // Almost black
val TextSecondary = Color(0xFF5B7083)        // Muted blue-gray
val Border = Color(0xFFE8EAED)               // Light gray border

// Dark Theme
val PurplePrimaryDarkTheme = Color(0xFFA394FF)
val BgDark = Color(0xFF0F1419)
val SurfaceDark = Color(0xFF1E2329)
val TextPrimaryDark = Color(0xFFE8EAED)
val TextSecondaryDark = Color(0xFF8B96A3)

// Status Colors
val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val WarningOrange = Color(0xFFF59E0B)

// Vote Colors
val HotOrange = Color(0xFFFF6B35)
val ColdBlue = Color(0xFF4A90E2)

val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = PurplePrimaryDark,

    secondary = PeachAccent,
    onSecondary = Color.White,
    secondaryContainer = PeachLight,
    onSecondaryContainer = Color(0xFF4A1F1A),

    tertiary = SuccessGreen,
    onTertiary = Color.White,

    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),

    background = BgLight,
    onBackground = TextPrimary,

    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = TextSecondary,

    outline = Border,
    outlineVariant = Color(0xFFF3F4F6),

    scrim = Color(0x66000000)
)

val DarkColors = darkColorScheme(
    primary = PurplePrimaryDarkTheme,
    onPrimary = Color(0xFF1A1625),
    primaryContainer = Color(0xFF2E2640),
    onPrimaryContainer = Color(0xFFE9E4FF),

    secondary = PeachAccent,
    onSecondary = Color(0xFF2A1510),
    secondaryContainer = Color(0xFF4A1F1A),
    onSecondaryContainer = PeachLight,

    tertiary = SuccessGreen,
    onTertiary = Color(0xFF0A3D25),

    error = ErrorRed,
    onError = Color(0xFF3D0A0A),

    background = BgDark,
    onBackground = TextPrimaryDark,

    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2A2F36),
    onSurfaceVariant = TextSecondaryDark,

    outline = Color(0xFF3A404A),
    outlineVariant = Color(0xFF2A2F36),

    scrim = Color(0x99000000)
)