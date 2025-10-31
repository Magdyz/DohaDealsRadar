package qa.deals.doha.feature.feed.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ========================================
 * âœ¨ SKELETON DEAL CARD - 2025 MODERN
 * ========================================
 *
 * Purpose: Loading placeholder that matches DealCard structure
 *
 * Features:
 * - Shimmer animation (smooth, subtle)
 * - Matches DealCard layout exactly
 * - Purple accent color (#9C27B0)
 * - Modern, professional appearance
 *
 * Design:
 * - Image placeholder (4:4 ratio)
 * - Vote buttons placeholder
 * - Title placeholder (2 lines)
 * - Button placeholder
 *
 * Created: 2025-10-28 for professional loading states
 */
@Composable
fun SkeletonDealCard(
    modifier: Modifier = Modifier
) {
    // âœ… Shimmer animation - smooth and subtle
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    // Skeleton colors - light grey base with shimmer
    val baseColor = Color(0xFFE0E0E0)
    val shimmerColor = baseColor.copy(alpha = shimmerAlpha)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ========================================
            // Image Placeholder with Vote Buttons
            // ========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 4f)
                    .padding(8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(shimmerColor)
            ) {
                // Vote buttons placeholder at bottom center
                Row(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hot vote button skeleton
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(40.dp)
                            .clip(CircleShape)
                            .background(baseColor.copy(alpha = 0.5f))
                    )

                    // Cold vote button skeleton
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(40.dp)
                            .clip(CircleShape)
                            .background(baseColor.copy(alpha = 0.5f))
                    )
                }
            }

            // ========================================
            // Content Section: Title + Button
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title placeholder (2 lines)
                Column(
                    modifier = Modifier.height(44.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(shimmerColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(shimmerColor)
                    )
                }

                // Button placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF9C27B0).copy(alpha = 0.3f),
                                    Color(0xFF9C27B0).copy(alpha = 0.3f)
                                )
                            )
                        )
                )
            }
        }
    }
}