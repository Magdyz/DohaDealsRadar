package qa.deals.doha.feature.feed.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import qa.deals.doha.db.DealEntity
import com.google.accompanist.placeholder.material.placeholder
import com.google.accompanist.placeholder.material.shimmer
import androidx.compose.ui.graphics.Color
import com.google.accompanist.placeholder.PlaceholderHighlight

/**
 * Compact deal card for grid layout
 * Vinted-inspired: minimal, clean, image-focused
 * ✅ OPTIMIZED: Cached values, minimal recompositions
 */
@Composable
fun DealCard(
    deal: DealEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    // ========================================
    // ✅ PERFORMANCE: Cache computed values
    // ========================================
    val hotCountText = remember(deal.hotCount) { "${deal.hotCount ?: 0}" }
    val coldCountText = remember(deal.coldCount) { "${deal.coldCount ?: 0}" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        onClick = { onClick?.invoke() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ========================================
            // ✅ OPTIMIZED: Image loading
            // ========================================
            deal.imageUrl?.let { imageUrl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // Square images for grid
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    // ✅ PERFORMANCE: Remember painter to avoid recreation
                    val painter = rememberAsyncImagePainter(
                        model = imageUrl,
                        // ✅ NEW: Placeholder while loading (smoother)
                        placeholder = null,
                        error = null
                    )

                    val isLoading = painter.state is coil.compose.AsyncImagePainter.State.Loading

                    Image(
                        painter = painter,
                        contentDescription = deal.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .placeholder(
                                visible = isLoading,
                                color = Color.LightGray,
                                highlight = PlaceholderHighlight.shimmer(),
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ========================================
            // ✅ OPTIMIZED: Content section
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title (2 lines max)
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // ========================================
                // ✅ OPTIMIZED: Vote counts with cached values
                // ========================================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hot votes
                    VoteCount(
                        emoji = "🔥",
                        count = hotCountText  // ✅ Use cached value
                    )

                    // Cold votes
                    VoteCount(
                        emoji = "❄️",
                        count = coldCountText  // ✅ Use cached value
                    )
                }
            }
        }
    }
}

// ========================================
// ✅ NEW: Extracted composable to prevent recomposition
// ========================================
/**
 * Vote count display - extracted to minimize recompositions
 */
@Composable
private fun VoteCount(
    emoji: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}