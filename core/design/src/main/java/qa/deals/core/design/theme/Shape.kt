package qa.deals.doha.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Modern shape system - Soft, friendly rounded corners
 */
val DohaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // Small elements
    small = RoundedCornerShape(8.dp),         // Buttons, chips
    medium = RoundedCornerShape(12.dp),       // Cards
    large = RoundedCornerShape(16.dp),        // Large cards, dialogs
    extraLarge = RoundedCornerShape(24.dp)    // Bottom sheets, special surfaces
)