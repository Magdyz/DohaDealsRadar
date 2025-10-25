package qa.deals.doha.feature.feed.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import qa.deals.doha.design.theme.*

/**
 * ✨ REDESIGNED: Modern card with vote buttons overlaid on image
 * ✅ ENHANCED: Comprehensive logging for image loading debugging
 *
 * CHANGES:
 * 1. Vote buttons: Smaller, positioned at bottom-center of image
 * 2. View Deal button: Full width, more padding, prominent
 * 3. Cards: Fixed aspect ratio (1:1), consistent sizing
 * 4. Rounded edges: All corners rounded for modern look
 * 5. ✨ NEW: Glass-morphism effect for vote buttons container
 * 6. ✨ NEW: Circular vote buttons with smaller size
 */
@Composable
fun DealCard(
    deal: DealEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onVoteHot: (() -> Unit)? = null,
    onVoteCold: (() -> Unit)? = null,
    hasVoted: Boolean = false,           // Keep for backwards compatibility
    userVoteType: String? = null,        // Keep for backwards compatibility
    optimisticHotCount: Int? = null,
    optimisticColdCount: Int? = null
) {
    val TAG = "DealCard"
    val context = androidx.compose.ui.platform.LocalContext.current

    // ✅ FIX: Check vote status directly from DeviceIdManager (source of truth)
    val deviceIdManager = remember { qa.deals.doha.datastore.DeviceIdManager.getInstance(context) }
    val actualHasVoted = remember(deal.id) { deviceIdManager.hasVoted(deal.id) }
    val actualUserVoteType = remember(deal.id) { deviceIdManager.getVoteType(deal.id) }

    // ✅ Use actual vote status (not the stale parameter)
    val effectiveHasVoted = actualHasVoted
    val effectiveUserVoteType = actualUserVoteType

    // ========================================
    // Vote count calculations
    // ========================================
    val displayHotCount = optimisticHotCount ?: (deal.hotCount ?: 0)
    val displayColdCount = optimisticColdCount ?: (deal.coldCount ?: 0)

    val hotCountText = remember(displayHotCount) { "$displayHotCount" }
    val coldCountText = remember(displayColdCount) { "$displayColdCount" }
    val isDarkTheme = false

    // ========================================
    // ✅ ENHANCED: Log image URL being displayed
    // ========================================
    LaunchedEffect(deal.id, deal.imageUrl) {
        val imageType = when {
            deal.imageUrl?.contains("thumb_") == true -> "📸 THUMBNAIL"
            deal.imageUrl?.contains("full_") == true -> "📷 FULL IMAGE"
            deal.imageUrl?.startsWith("http") == true -> "🔗 EXTERNAL"
            deal.imageUrl == null -> "❌ NO IMAGE"
            else -> "❓ UNKNOWN"
        }

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🖼️  DISPLAY: Rendering deal card")
        Log.d(TAG, "   → Deal ID: ${deal.id.take(8)}...")
        Log.d(TAG, "   → Title: ${deal.title.take(40)}")
        Log.d(TAG, "   → Image type: $imageType")
        Log.d(TAG, "   → URL: ${deal.imageUrl}")
        Log.d(TAG, "   → Hot votes: ${deal.hotCount ?: 0}")
        Log.d(TAG, "   → Cold votes: ${deal.coldCount ?: 0}")
    }

    // ========================================
    // Fixed card with rounded edges
    // ========================================
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp),
        onClick = { onClick?.invoke() },
        shape = MaterialTheme.shapes.medium, // ✅ Rounded edges
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ========================================
            // Image with vote buttons overlay at bottom center
            // ========================================
            deal.imageUrl?.let { imageUrl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 4f) // ✅ 4:4 ratio
                        .padding(8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(Color.White)
                ) {
                    // ✅ ENHANCED: Image painter with load state tracking
                    val painter = rememberAsyncImagePainter(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .size(400, 400) // ✅ Grid thumbnail size (prevents loading full 4K images)
                            .crossfade(150) // ✅ Smooth fade-in animation
                            // ✅ Cache this specific size
                            .memoryCacheKey("grid_$imageUrl")
                            .diskCacheKey("grid_$imageUrl")
                            .build(),
                        placeholder = null,
                        error = null
                    )

                    // ✅ ENHANCED: Log image loading state changes
                    LaunchedEffect(painter.state) {
                        when (val state = painter.state) {
                            is coil.compose.AsyncImagePainter.State.Loading -> {
                                Log.d(TAG, "   ⏳ Loading image for ${deal.id.take(8)}...")
                            }
                            is coil.compose.AsyncImagePainter.State.Success -> {
                                val imageType = when {
                                    imageUrl.contains("thumb_") -> "THUMBNAIL ⚠️"
                                    imageUrl.contains("full_") -> "FULL IMAGE ✅"
                                    else -> "EXTERNAL"
                                }
                                Log.d(TAG, "   ✅ Image loaded: $imageType")
                                Log.d(TAG, "      Deal: ${deal.id.take(8)}")
                                Log.d(TAG, "      URL: $imageUrl")
                            }
                            is coil.compose.AsyncImagePainter.State.Error -> {
                                Log.e(TAG, "   ❌ Image load FAILED")
                                Log.e(TAG, "      Deal: ${deal.id.take(8)}")
                                Log.e(TAG, "      URL: $imageUrl")
                                Log.e(TAG, "      Error: ${state.result.throwable.message}")
                            }
                            else -> { /* Empty or other state */ }
                        }
                    }

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
                        contentScale = ContentScale.Fit
                    )

                    // ========================================
                    // ✨ UPDATED: Vote buttons with glass-morphism effect
                    // ========================================
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 3.dp)
                            // ✨ CHANGE 2: More transparent background (was 0.5f, now 0.25f)
                            // ✨ CHANGE 2: Glass-morphism effect with blur
                            .background(
                                color = Color.Black.copy(alpha = 0.25f), // More transparent
                                shape = MaterialTheme.shapes.large
                            )
                            // ✨ CHANGE 1: Smaller padding (was 8dp horizontal, now 6dp)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        // ✨ CHANGE 1: Smaller spacing (was 6dp, now 4dp)
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ✨ CHANGE 1 & 3: Smaller, circular Hot Vote Button
                        CompactVoteButton(
                            emoji = "🔥",
                            count = hotCountText,
                            onClick = {
                                Log.d(TAG, "🔥 HOT VOTE clicked for ${deal.id.take(8)}")
                                onVoteHot?.invoke()
                            },
                            enabled = !hasVoted,
                            isVoted = userVoteType == "hot",
                            backgroundColor = if (isDarkTheme) VoteHotBgDark else VoteHotBg,
                            contentColor = if (isDarkTheme) VoteHotContentDark else VoteHotContent
                        )

                        // ✨ CHANGE 1 & 3: Smaller, circular Cold Vote Button
                        CompactVoteButton(
                            emoji = "❄️",
                            count = coldCountText,
                            onClick = {
                                Log.d(TAG, "❄️ COLD VOTE clicked for ${deal.id.take(8)}")
                                onVoteCold?.invoke()
                            },
                            enabled = !hasVoted,
                            isVoted = userVoteType == "cold",
                            backgroundColor = if (isDarkTheme) VoteColdBgDark else VoteColdBg,
                            contentColor = if (isDarkTheme) VoteColdContentDark else VoteColdContent
                        )
                    }
                }
            }

            // ========================================
            // Content Section: Title + View Deal Button
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title (2 lines max)
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,  // ✅ More prominent
                        fontSize = 13.sp,              // ✅ Font size title
                        lineHeight = 20.sp             // ✅ Optimal 1.43x ratio
                    ),
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.height(44.dp)  // ✅ More space
                )

                // ========================================
                // View Deal Button - Full width, prominent
                // ========================================
                Button(
                    onClick = {
                        Log.d(TAG, "👁️ VIEW clicked for ${deal.id.take(8)}: ${deal.title.take(30)}")
                        onClick?.invoke()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp), // Taller, not squashed
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9046CF),
                        contentColor = Color(0xFFF3F3F4)

                    ),
                    shape = MaterialTheme.shapes.large, // Rounded edges
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RemoveRedEye,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Deal",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * ✨ UPDATED: Compact Vote Button - Smaller and Circular
 *
 * CHANGES:
 * 1. ✨ CHANGE 1: Reduced size (was 28dp height, now 24dp)
 * 2. ✨ CHANGE 3: Circular shape (CircleShape instead of MaterialTheme.shapes.small)
 * 3. ✨ CHANGE 1: Smaller padding and font sizes
 */
@Composable
private fun CompactVoteButton(
    emoji: String,
    count: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isVoted: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    // Button background logic
    val buttonBg = when {
        !enabled && isVoted -> Color.White.copy(alpha = 0.9f)  // This vote - white background
        !enabled -> Color.Gray.copy(alpha = 0.5f)              // Other vote - grey
        else -> backgroundColor                                 // Not voted - colorful
    }

    // Emoji opacity
    val emojiAlpha = when {
        !enabled && !isVoted -> 0.4f  // Other vote - very grey
        !enabled && isVoted -> 0.7f   // This vote - slightly grey
        else -> 1f                     // Not voted - full color
    }

    // Number color
    val numberColor = when {
        !enabled && isVoted -> contentColor  // This vote - colored number
        !enabled -> Color.Gray               // Other vote - grey
        else -> contentColor                 // Not voted - colored
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            // ✨ CHANGE 1: Smaller height (was 28dp, now 24dp)
            .height(24.dp)
            // ✨ CHANGE 1: Smaller minimum width (was 45dp, now 40dp)
            .widthIn(min = 40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonBg,
            contentColor = numberColor,
            disabledContainerColor = buttonBg,
            disabledContentColor = numberColor
        ),
        // ✨ CHANGE 3: Circular shape instead of small rounded rectangle
        shape = CircleShape, // Perfectly round buttons!
        // ✨ CHANGE 1: Smaller padding (was 6dp horizontal, now 5dp)
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) contentColor.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Row(
            // ✨ CHANGE 1: Smaller spacing (was 3dp, now 2dp)
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji
            Text(
                text = emoji,
                style = MaterialTheme.typography.labelSmall.copy(
                    // ✨ CHANGE 1: Smaller emoji (was 12sp, now 11sp)
                    fontSize = 11.sp
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = emojiAlpha
                }
            )

            // Number
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    // ✨ CHANGE 1: Smaller number (was 11sp, now 10sp)
                    fontSize = 10.sp
                )
            )
        }
    }
}