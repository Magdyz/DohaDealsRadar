package qa.deals.doha.design.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// 🎨 Vinted-inspired Purple Palette (Modern 2025)
// Light Theme
val PurplePrimary = Color(0xFFC57AF7)        // Soft vibrant purple
val PurplePrimaryDark = Color(0xFFC57AF7)    // Deeper purple for contrast
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

// ✨ NEW: Action Colors (High Contrast for Primary Actions)
val ActionPrimary = Color(0xFF9046CF)         // Vibrant purple for primary CTA
val ActionPrimaryHover = Color(0xFF9046CF)    // Darker on hover
val ActionSecondary = Color(0xFFF3F4F6)       // Light gray for secondary actions
val ActionSecondaryBorder = Color(0xFFE8EAED) // Border for secondary chips

// ✨ NEW: Vote Chip Colors (Distinct from primary actions)
val VoteHotBg = Color(0xFFFFF4ED)             // Light orange background
val VoteHotContent = Color(0xFFFF6B35)        // Hot orange text/icon
val VoteColdBg = Color(0xFFEFF6FF)            // Light blue background
val VoteColdContent = Color(0xFF4A90E2)       // Cold blue text/icon

// ✨ NEW: Report/Warning Colors
val ReportIconColor = Color(0xFF8B96A3)       // Subtle gray for report icon
val ReportBgColor = Color(0xFFF3F4F6)         // Light background for report chip

// ✨ NEW: Dark Theme Action Colors
val ActionPrimaryDark = Color(0xFF9046CF)
val ActionSecondaryDark = Color(0xFF2A2F36)
val VoteHotBgDark = Color(0xFF2A1510)
val VoteHotContentDark = Color(0xFFFF8B7C)
val VoteColdBgDark = Color(0xFF0A2540)
val VoteColdContentDark = Color(0xFF60A5FA)
val ReportIconColorDark = Color(0xFF8B96A3)
val ReportBgColorDark = Color(0xFF2A2F36)

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