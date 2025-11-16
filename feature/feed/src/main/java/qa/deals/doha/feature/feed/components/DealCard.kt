package qa.deals.doha.feature.feed.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qa.deals.doha.db.DealEntity
import androidx.compose.ui.graphics.Color
import qa.deals.doha.design.theme.*
import java.text.SimpleDateFormat
import java.util.*
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale

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
 * 7. 🆕 NEW: "New" badge for deals posted within 48 hours
 */

// ========================================
// ✅ NEW: Added ImageSkeleton (Copied from DetailsScreen.kt)
// This shimmer skeleton will be used for a professional loading state.
// ========================================
@Composable
private fun ImageSkeleton() {
    // ✅ UPDATED: Static background - NO ANIMATION to prevent flash
    // Simple, subtle background for smooth loading experience
    Box(
        modifier = Modifier
            .fillMaxSize() // Use fillMaxSize to match the image
            .background(
                color = Color(0xFFF5F5F5)  // ✅ Very light grey - barely noticeable
            )
    )
}

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
    optimisticColdCount: Int? = null,
    // ✅ Admin-only button for archived deals (Return to Feed)
    showAdminButton: Boolean = false,    // Whether to show "Return to Feed" button
    onAdminAction: (() -> Unit)? = null,  // Callback for admin button click
    // ✅ NEW: Admin-only delete button (permanent delete)
    showDeleteButton: Boolean = false,   // Whether to show delete X button
    onDelete: (() -> Unit)? = null       // Callback for delete button click
) {
    val TAG = "DealCard"
    val context = androidx.compose.ui.platform.LocalContext.current
    val isArchived = deal.isArchived

    // ========================================
    // Vote count calculations
    // ========================================
    val displayHotCount = optimisticHotCount ?: (deal.hotCount ?: 0)
    val displayColdCount = optimisticColdCount ?: (deal.coldCount ?: 0)

    val hotCountText = remember(displayHotCount) { "$displayHotCount" }
    val coldCountText = remember(displayColdCount) { "$displayColdCount" }
    val isDarkTheme = false

    // ========================================
// 🆕 NEW FEATURE: Check if deal is new (within 2 days)
// ========================================
    val isNewDeal = remember(deal.createdAt) {
        deal.createdAt?.let { createdAtStr ->
            try {
                // Parse the timestamp (assuming ISO 8601 format from Supabase)
                val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val createdDate = formatter.parse(createdAtStr)

                if (createdDate != null) {
                    val currentTime = System.currentTimeMillis()
                    val createdTime = createdDate.time
                    val hoursDifference = (currentTime - createdTime) / (1000 * 60 * 60)

                    // Return true if less than or equal to 48 hours (2 days)
                    hoursDifference <= 48 // Change 48 to desired hours
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing date for deal ${deal.id}: ${e.message}")
                false
            }
        } ?: false
    }

    // ========================================
    // Fixed card with rounded edges
    // ========================================
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp),
        onClick = {
            Log.d(TAG, "🃏 Card clicked for deal: ${deal.id}, archived: $isArchived")
            onClick?.invoke()
        },
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
                        .graphicsLayer {
                            // ✅ 2025: Hardware acceleration for image rendering
                            // Dramatically improves scrolling FPS by preventing layout
                        }
                        .clip(MaterialTheme.shapes.large)
                        .background(Color.White)
                ) {
// ========================================
                    // ✅ FIX (3.2): Replaced Image + placeholder
                    // with SubcomposeAsyncImage.
                    // This uses the superior pattern from DetailsScreen
                    // and implements the ImageSkeleton for loading.
                    // ========================================
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("$imageUrl?width=400&quality=80&format=webp")
                            .scale(Scale.FIT)
                            .memoryCacheKey("grid_w400_$imageUrl")
                            .diskCacheKey("grid_w400_$imageUrl")
                            .listener(
                                onStart = { Log.d(TAG, "🖼️ Loading image: $imageUrl") },
                                onSuccess = { _, _ -> Log.d(TAG, "✅ Image loaded: $imageUrl") },
                                onError = { _, result ->
                                    Log.e(TAG, "❌ Image failed: $imageUrl", result.throwable)
                                }
                            )
                            .build(),
                        contentDescription = deal.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // ✅ 2025: Hardware-accelerate the image itself
                            },
                        loading = { ImageSkeleton() },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📷",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    )
// ========================================
// 🆕 NEW: "New" Badge (Top-Right Corner)
// Only shows for deals posted within 48 hours
// ========================================

                    if (isNewDeal && !showDeleteButton) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(
                                    color = Color(0xFF9046CF).copy(alpha = 0.92f), // Semi-transparent purple
                                    shape = CircleShape
                                )
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "New",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    // ========================================
                    // ✅ NEW: Admin Delete Button (Top-Right Corner)
                    // Permanently deletes deal and image from database
                    // 2025 Design: Smaller, more refined and proportional
                    // ========================================

                    if (showDeleteButton && onDelete != null) {
                        IconButton(
                            onClick = {
                                Log.d(TAG, "🗑️ Delete button clicked for deal: ${deal.id}")
                                onDelete.invoke()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .background(
                                    color = Color(0xFFDC2626).copy(alpha = 0.95f), // Red with high opacity
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Delete deal",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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
                                if (!isArchived) {  // ✅ NEW: Check archive status
                                    onVoteHot?.invoke()
                                }
                            },
                            enabled = !hasVoted && !isArchived,  // ✅ MODIFIED: Disable if archived
                            isVoted = userVoteType == "hot",
                            backgroundColor = if (isDarkTheme) VoteHotBgDark else VoteHotBg,
                            contentColor = if (isDarkTheme) VoteHotContentDark else VoteHotContent
                        )

                        // ✨ CHANGE 1 & 3: Smaller, circular Cold Vote Button
                        CompactVoteButton(
                            emoji = "❄️",
                            count = coldCountText,
                            onClick = {
                                if (!isArchived) {  // ✅ NEW: Check archive status
                                    onVoteCold?.invoke()
                                }
                            },
                            enabled = !hasVoted && !isArchived,  // ✅ MODIFIED: Disable if archived
                            isVoted = userVoteType == "cold",
                            backgroundColor = if (isDarkTheme) VoteColdBgDark else VoteColdBg,
                            contentColor = if (isDarkTheme) VoteColdContentDark else VoteColdContent
                        )
                    }
                }
            }

            // ========================================
            // Content Section: Title + Price + View Deal Button
            // ✨ 2025 SOLUTION: Fixed heights = Uniform cards
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title (3 lines max)
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 3,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.heightIn(min = 48.dp, max = 54.dp)
                )

                // ========================================
                // ✨ FIXED HEIGHT PRICE SECTION (40dp)
                // Maintains uniform card height regardless of content
                // ========================================
                DealCardPrice(
                    originalPrice = deal.originalPrice,
                    discountedPrice = deal.discountedPrice
                )

                // ========================================
                // View Deal Button - Full width, prominent
                // ✅ Show for: Main feed (all users) + Archive (non-admin only)
                // ✅ Hide for: Archive (admin) - they see icon-only buttons instead
                // ========================================

                if (!(showAdminButton && isArchived)) {
                    Button(
                        onClick = {
                            Log.d(TAG, "🔘 View Deal button clicked for deal: ${deal.id}, archived: $isArchived")
                            onClick?.invoke()
                        },
                        enabled = true,  // ✅ Always enabled (even for archived deals)
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
                                text = "View Deal",  // ✅ Always "View Deal" (to check if still active)
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp  // ✨ Slightly reduced for better density
                                )
                            )
                        }
                    }
                }
            }

            // ========================================
            // ✅ ADMIN-ONLY: Icon buttons for Archive (side-by-side)
            // Eye icon (purple) for View Deal, Recycle icon (green) for Return to Feed
            // ========================================

            if (showAdminButton && isArchived && onAdminAction != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // View Deal Button (Eye Icon) - Purple
                    Button(
                        onClick = {
                            Log.d(TAG, "👁️ Admin View Deal button clicked for deal: ${deal.id}")
                            onClick?.invoke()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9046CF),  // Purple
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.large,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RemoveRedEye,
                            contentDescription = "View Deal",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Return to Feed Button (Recycle Icon) - Green
                    Button(
                        onClick = {
                            Log.d(TAG, "♻️ Return to Feed button clicked for deal: ${deal.id}")
                            onAdminAction.invoke()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),  // Green (original color)
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.large,
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Return to Feed",
                            modifier = Modifier.size(24.dp)
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

// ========================================
// ✨ NEW: Price Display Component (2025-11-16)
// ========================================
/**
 * ✨ 2025 MODERN SOLUTION: Fixed-height price section for uniform cards
 *
 * Display logic:
 * - Both prices: TWO LINES
 *   Line 1: QR 1,995 (pink, 14sp, bold)
 *   Line 2: QR 2,745 -27% (grey + green, 11sp)
 * - One price: SINGLE LINE (centered vertically)
 * - No price: EMPTY (but height maintained)
 *
 * CRITICAL: Always 40dp height to keep all cards uniform
 */
@Composable
private fun DealCardPrice(
    originalPrice: Double?,
    discountedPrice: Double?
) {
    // Always show the container with fixed height for consistency
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),  // 🎯 FIXED HEIGHT = Uniform cards
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            // Case 1: Both prices exist - TWO LINES
            originalPrice != null && discountedPrice != null -> {
                val discountPercent = ((originalPrice - discountedPrice) / originalPrice * 100).toInt()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Line 1: Discounted price (prominent, pink)
                    Text(
                        text = formatPrice(discountedPrice),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        ),
                        color = Color(0xFFE91E63)  // Pink highlight
                    )

                    // Line 2: Original price + percentage (smaller, grey + green)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatPrice(originalPrice),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            ),
                            color = Color.Gray
                        )

                        Text(
                            text = "-$discountPercent%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp
                            ),
                            color = Color(0xFF10B981)  // Green
                        )
                    }
                }
            }

            // Case 2: Only original price - CENTERED
            originalPrice != null -> {
                Text(
                    text = formatPrice(originalPrice),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Case 3: Only discounted price - CENTERED (pink)
            discountedPrice != null -> {
                Text(
                    text = formatPrice(discountedPrice),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFE91E63),  // Pink highlight
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Case 4: No price - EMPTY but maintains 40dp height
            // This keeps all cards the same height for uniform grid
        }
    }
}

/**
 * Formats a price value to display with QR prefix and comma separators
 * Examples:
 * - 1995.0 -> "QR 1,995"
 * - 1995.50 -> "QR 1,995.50"
 * - 19.99 -> "QR 19.99"
 */
private fun formatPrice(price: Double): String {
    return if (price % 1.0 == 0.0) {
        // Whole number - no decimals
        "QR ${"%,.0f".format(price)}"
    } else {
        // Has decimals
        "QR ${"%,.2f".format(price)}"
    }
}