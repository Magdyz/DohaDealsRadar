package qa.deals.doha.feature.details

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qa.deals.doha.db.DealEntity
import androidx.compose.foundation.BorderStroke
// ✨ NEW: Advanced Coil imports for 2025 performance
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import androidx.compose.foundation.layout.imePadding
import qa.deals.doha.core.design.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

/**
 * Details Screen - Modern 2025 Design
 *
 * REDESIGNED LAYOUT:
 * 1. Interactive circular voting + report flag (top line after image)
 * 2. Title (bold)
 * 3. Description
 * 4. Location/Link
 * 5. Metadata
 *
 * DESIGN UPDATES (2025):
 * - Circular voting buttons (52dp, same as report flag)
 * - Optimistic rendering (no loading spinner)
 * - White emoji + count on solid background when voted
 * - Colored emoji + count on light background when not voted
 * - Better spacing (4dp between emoji and count)
 * - Number formatting for large counts (1.2k style)
 * - White border when voted (elevation effect)
 *
 * ✅ ALL FUNCTIONALITY PRESERVED:
 * - Voting logic (optimistic updates, local lock prevents double voting)
 * - Report functionality (same modal/screen)
 * - Share button
 * - Location copy
 * - Link opening
 */

/**
 * ✨ MODERN 2025: Convert timestamp to relative time
 * Format: "Just now", "5m ago", "3h ago", "2d ago", "1w ago", "1mo ago", "1y ago"
 */
private fun getRelativeTimeString(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""

    try {
        // Parse the timestamp (adjust format based on your backend)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(createdAt) ?: return ""

        val now = System.currentTimeMillis()
        val then = date.time
        val diffMs = now - then

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "Yesterday"
            days < 7 -> "${days}d ago"
            weeks < 4 -> "${weeks}w ago"
            months < 12 -> "${months}mo ago"
            else -> "${years}y ago"
        }
    } catch (e: Exception) {
        Log.e("DetailsScreen", "Error parsing date: $createdAt", e)
        return ""
    }
}

/**
 * ✨ Calculate expiry time from expiresAt timestamp
 * Format: "Expires in X days" or "Expires in less than 1 day"
 */
private fun getExpiryTimeString(expiresAt: String?): String {
    if (expiresAt.isNullOrBlank()) return ""

    try {
        // Parse the timestamp (same format as createdAt)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(expiresAt) ?: return ""

        val now = System.currentTimeMillis()
        val expiryTime = date.time
        val diffMs = expiryTime - now

        // If already expired, return empty string
        if (diffMs < 0) return ""

        val seconds = diffMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 1 -> "Expires in ${days} days"
            days == 1L -> "Expires in 1 day"
            else -> "Expires in less than 1 day"
        }
    } catch (e: Exception) {
        Log.e("DetailsScreen", "Error parsing expiry date: $expiresAt", e)
        return ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    dealId: String,
    onBackClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onAccountClick: () -> Unit = {}  // ✅ NEW: Navigate to verify email screen
) {
    val context = LocalContext.current
    val viewModel = remember(dealId) {
        DetailsViewModel(dealId = dealId, context = context)
    }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Deal Details",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text(
                            "←",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // ✅ PRESERVED: Share functionality unchanged
                    IconButton(
                        onClick = {
                            val shareText = viewModel.getShareText()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share deal")
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // ✅ PRESERVED: Loading state unchanged
                uiState.loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // ✅ PRESERVED: Error state unchanged
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "😞",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = uiState.error ?: "Something went wrong",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = onBackClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Go Back")
                        }
                    }
                }

                // ✅ REDESIGNED: Success state with new circular buttons
                uiState.deal != null -> {
                    DealDetailsContent(
                        deal = uiState.deal!!,
                        uiState = uiState,
                        onOpenLink = { link ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            context.startActivity(intent)
                        },
                        onVote = { voteType ->
                            viewModel.castVote(voteType)
                        },
                        onReport = onReportClick
                    )
                }
            }
        }
    }

    // ========================================
    // ✅ NEW: Vote Authentication Dialog
    // Shows when anonymous user tries to vote
    // ========================================

    uiState.pendingVote?.let { pendingVote ->
        if (uiState.showVoteAuthDialog) {
            VoteAuthDialog(
                voteType = pendingVote.voteType,
                onDismiss = { viewModel.dismissVoteAuthDialog() },
                onLoginClick = {
                    viewModel.dismissVoteAuthDialog()
                    onAccountClick()  // ✅ Navigate to verify email screen (same as account icon)
                },
                onVerifyEmailClick = {
                    viewModel.dismissVoteAuthDialog()
                }
            )
        }
    }
}

/**
 * ✨ HELPER: Format vote count for display
 *
 * Modern 2025 number formatting:
 * - 0-999: Show as-is
 * - 1000+: Show as "1.2k", "5.6k", etc.
 */
private fun formatVoteCount(count: Int): String = when {
    count >= 1000 -> "${count / 1000}.${(count % 1000) / 100}k"
    else -> count.toString()
}

/**
 * ✨ NEW: Modern skeleton loader for image (2025)
 *
 * Features:
 * - Shimmer animation effect
 * - Matches feed skeleton design
 * - Smooth, professional loading state
 *
 * Created: 2025-10-19 14:46:13 UTC by @Magdyz
 */
@Composable
private fun ImageSkeleton() {
    // ✅ UPDATED: Static background - NO ANIMATION to prevent flash
    // Simple, subtle background that doesn't draw attention
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(
                color = Color(0xFFF5F5F5)  // ✅ Very light grey - barely noticeable
            )
    )
}

/**
 * ✅ REDESIGNED: Main content with circular voting buttons
 *
 * NEW DESIGN (2025):
 * - Circular buttons (52dp)
 * - Optimistic rendering (no loading spinner)
 * - Better contrast (white on solid, colored on light)
 * - White border when voted
 * - Better spacing between emoji and count
 *
 * FUNCTIONALITY PRESERVED:
 * - All voting logic intact
 * - Local lock prevents double voting
 * - Report opens same modal
 * - Share works the same
 * - Location copy unchanged
 */

/**
 * ✅ UPDATED: Main content with expandable description & floating button
 *
 * NEW FEATURES (2025):
 * - Expandable description with "See more" link
 * - Floating "View Deal" button for online deals
 * - Location card stays inline (no floating button)
 *
 * Updated: 2025-01-20 09:55:00 UTC by @Magdyz
 */
@Composable
private fun DealDetailsContent(
    deal: DealEntity,
    uiState: DetailsUiState,
    onOpenLink: (String) -> Unit,
    onVote: (String) -> Unit,
    onReport: () -> Unit
) {
    val context = LocalContext.current

    // ✨ NEW: Description expansion state
    var isDescriptionExpanded by remember { mutableStateOf(false) }

// 🔧 FIX: Smart max lines - only truncate if description is actually long
// Short descriptions (≤100 chars) show fully without ellipsis
// Long descriptions (>100 chars) get truncated to 3 lines with "See more" button
    val isDescriptionLong = (deal.description?.length ?: 0) > 100
    val descriptionMaxLines = when {
        !isDescriptionLong -> Int.MAX_VALUE  // ✅ Short text: show everything
        isDescriptionExpanded -> Int.MAX_VALUE  // ✅ Expanded: show everything
        else -> 3  // ✅ Long text collapsed: limit to 3 lines
    }

    // ✨ NEW: Check if deal has a link (online deal)
    val hasLink = !deal.link.isNullOrBlank()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()  // ✨ Automatically adjusts for keyboard
                .verticalScroll(rememberScrollState())
                .padding(bottom = if (hasLink) 88.dp else 0.dp)  // ✅ Extra padding if floating button
        ) {
            // ========================================
            // ✅ Hero Image (unchanged)
            // ========================================
            deal.imageUrl?.let { imageUrl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(0xFFF5F5F5)) // <-- 1. FIX: Add stable background
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("$imageUrl?width=1200&quality=80&format=webp")
                            .scale(Scale.FIT)
                            .memoryCacheKey("detail_w1200_$imageUrl")
                            .diskCacheKey("detail_w1200_$imageUrl")
                            .placeholderMemoryCacheKey("grid_w400_$imageUrl") // ✅ Use our new grid image as placeholder
                            .build(),
                        contentDescription = deal.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = "📷", style = MaterialTheme.typography.displayMedium)
                                    Text(
                                        text = "Image failed to load",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // ========================================
            // Content Section - 2025 Enhanced Spacing & Typography
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 24.dp),  // ✨ More generous vertical padding
                verticalArrangement = Arrangement.spacedBy(24.dp)  // ✨ Increased spacing for better breathing room
            ) {
                // ========================================
                // Voting & Report Buttons (unchanged)
                // ========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hot Vote Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF374151))
                                .then(
                                    if (uiState.hasVoted && uiState.userVoteType == "hot")
                                        Modifier.border(3.dp, Color(0xFFFF9143), CircleShape)
                                    else Modifier
                                )
                                .clickable(
                                    enabled = !uiState.isArchived,
                                    onClick = {
                                        if (!uiState.isArchived) onVote("hot")
                                    }

                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "🔥", style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatVoteCount(deal.hotCount ?: 0),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (uiState.hasVoted && uiState.userVoteType == "hot")
                                            Color(0xFFF3F3F4)
                                        else if (uiState.hasVoted)
                                            Color(0xFF6B7280)
                                        else
                                            Color(0xFFFF6B35)
                                    )
                                )
                            }
                        }

                        // Cold Vote Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF374151))
                                .then(
                                    if (uiState.hasVoted && uiState.userVoteType == "cold")
                                        Modifier.border(3.dp, Color(0xFF4A90E2), CircleShape)
                                    else Modifier
                                )
                                .clickable(
                                    enabled = !uiState.isArchived,
                                    onClick = {
                                        if (!uiState.isArchived) onVote("cold")
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "❄️", style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatVoteCount(deal.coldCount ?: 0),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (uiState.hasVoted && uiState.userVoteType == "cold")
                                            Color(0xFFF3F3F4)
                                        else if (uiState.hasVoted)
                                            Color(0xFF6B7280)
                                        else
                                            Color(0xFF4A90E2)
                                    )
                                )
                            }
                        }
                    }

                    // Report Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF374151))
                            .clickable(    enabled = !uiState.isArchived,
                                onClick = { if (!uiState.isArchived) onReport() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🚩", style = MaterialTheme. typography.titleMedium.copy(fontSize = 20.sp))
                    }
                }

                // Timestamp and Expiry - 2025 Enhanced
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),  // ✨ Reduced top padding
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Posted date (left side)
                    deal.createdAt?.let { createdAt ->
                        Text(
                            text = "Posted ${getRelativeTimeString(createdAt)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,  // ✨ Slightly smaller for subtlety
                                letterSpacing = 0.3.sp  // ✨ Better readability
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)  // ✨ More subtle
                        )
                    }

                    // Expiry date (right side)
                    deal.expiresAt?.let { expiresAt ->
                        val expiryText = getExpiryTimeString(expiresAt)
                        if (expiryText.isNotEmpty()) {
                            Text(
                                text = expiryText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,  // ✨ Slightly smaller for subtlety
                                    letterSpacing = 0.3.sp  // ✨ Better readability
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)  // ✨ More subtle
                            )
                        }
                    }
                }

                // ========================================
                // Title - 2025 Enhanced Typography
                // ========================================
                Text(
                    text = deal.title.replace("\n", " "),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.5).sp  // ✨ Tighter letter spacing for headlines (2025 trend)
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // ========================================
                // ✨ NEW: Price Display (2025-11-16)
                // ========================================
                DealDetailsPrice(
                    originalPrice = deal.originalPrice,
                    discountedPrice = deal.discountedPrice
                )

                // ========================================
                // ✨ UPDATED: Expandable Description - 2025 Enhanced
                // ========================================
                if (!deal.description.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {  // ✨ Increased spacing
                        Text(
                            text = deal.description!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                fontSize = 15.sp,
                                letterSpacing = 0.15.sp  // ✨ Better readability for body text
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),  // ✨ Slightly stronger contrast
                            maxLines = descriptionMaxLines,
                            overflow = if (isDescriptionLong && !isDescriptionExpanded) {
                                TextOverflow.Ellipsis  // ✅ Long text: show "..." when collapsed
                            } else {
                                TextOverflow.Clip  // ✅ Short text or expanded: no ellipsis
                            }
                        )

                        // ✨ "See more" / "See less" button - 2025 Clean Design
                        if (deal.description!!.length > 100) {  // Only show if description is long
                            Text(
                                text = if (isDescriptionExpanded) "Show less" else "Show more",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.3.sp  // ✨ Better spacing
                                ),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),  // ✨ Subtle primary color
                                modifier = Modifier
                                    .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                    .padding(top = 2.dp)  // ✨ Reduced spacing
                            )
                        }
                    }
                }

                // ========================================
                // Category Display - 2025 Enhanced
                // ========================================
                deal.category?.let { categoryId ->
                    val categoryInfo = when (categoryId) {
                        "food_dining" -> "🍔" to "Food & Dining"
                        "shopping_fashion" -> "🛍️" to "Shopping & Fashion"
                        "entertainment" -> "🎮" to "Entertainment & Leisure"
                        "home_services" -> "🏠" to "Home & Services"
                        else -> "⭐" to "Other"
                    }

                    Surface(
                        modifier = Modifier.wrapContentWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = categoryInfo.first, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                            Text(
                                text = categoryInfo.second,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ========================================
                // Posted By (unchanged)
                // ========================================
                deal.postedBy?.let { username ->
                    if (username != "Anonymous") {
                        Row(
                            modifier = Modifier.wrapContentWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "👤", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp))
                            Text(
                                text = "Posted by",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = username,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // ========================================
                // ✅ NEW: Promo Code Card
                // Added as per request, styled like LocationCard
                // ========================================
                if (!deal.promoCode.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    PromoCodeCard(promoCode = deal.promoCode!!, context = context, isArchived = uiState.isArchived)                }


                // ========================================
                // ✅ Location Card (stays inline - NO floating button)
                // ========================================
                if (!deal.location.isNullOrBlank()) {
                    // Add spacing *only if* there was no promo code
                    if (deal.promoCode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    LocationCard(location = deal.location!!, context = context, isArchived = uiState.isArchived)
                    // ========================================
                    // 🗑️ REMOVED: Old promo code logic from here
                    // ========================================

                    // ✅ Show inline link button if both location and link exist
                    // ✅ UPDATED: Always enabled (even for archived deals) so users can check if deal is still active
                    if (deal.link.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { onOpenLink(deal.link) },
                            enabled = true,  // ✅ Always enabled to check if deal is still active
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("🔗 View Online", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                // ✅ NOTE: Online deals (no location) get floating button below

                // Vote Error Display (unchanged)
                uiState.voteError?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ========================================
                // LIABILITY DISCLAIMER - 2025 Clean Design
                // Aligned with description, no background, subtle
                // ========================================
                Spacer(modifier = Modifier.height(20.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Disclaimer",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "All deals are posted by users and are the sole responsibility of the original poster. DohaDealsRadar is not responsible for the accuracy, validity, or availability of any deals. By clicking on deal links, you will be redirected to third-party websites. DohaDealsRadar is not liable for any transactions, issues, or disputes that may arise from these external sites. Please verify all deal details independently before making any purchase.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // ========================================
        // ✨ FLOATING "VIEW DEAL" BUTTON
        // ✅ UPDATED: Always enabled (even for archived deals) so users can check if deal is still active
        // ========================================

        if (hasLink && deal.location.isNullOrBlank()) {
            Button(
                onClick = { onOpenLink(deal.link) },
                enabled = true,  // ✅ Always enabled to check if deal is still active
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .width(280.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE91E63),  // Pink
                                    Color(0xFF9C27B0)   // Purple
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "View Deal",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

/**
 * ✅ PRESERVED: Location card with copy functionality (unchanged)
 *
 * Features:
 * - Shows location with pin icon
 * - Copy to clipboard button
 * - Visual feedback when copied
 */
@Composable
private fun LocationCard(
    location: String,
    context: android.content.Context,
    isArchived: Boolean = false  // Archive status
) {
    var showCopiedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedMessage) {
        if (showCopiedMessage) {
            kotlinx.coroutines.delay(2000)
            showCopiedMessage = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📍",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Copy Button
                IconButton(
                    enabled = !isArchived,
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("location", location)
                        clipboard.setPrimaryClip(clip)
                        showCopiedMessage = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                ) {
                    Text(
                        text = if (showCopiedMessage) "✓" else "📋",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (showCopiedMessage)
                            Color(0xFF10B981)
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Location Text
            Text(
                text = location,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Copied confirmation
            if (showCopiedMessage) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Copied to clipboard",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

// ========================================
// ✅ NEW: Promo Code Card
// Copied from LocationCard to match style, as requested.
// ========================================
@Composable
private fun PromoCodeCard(
    promoCode: String,
    context: android.content.Context,
    isArchived: Boolean = false  // Archive status
) {
    var showCopiedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedMessage) {
        if (showCopiedMessage) {
            kotlinx.coroutines.delay(2000)
            showCopiedMessage = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎟️", // Changed icon
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Promo Code", // Changed title
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Copy Button
                IconButton(
                    enabled = !isArchived,
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        // Changed clip data
                        val clip = android.content.ClipData.newPlainText("Promo Code", promoCode)
                        clipboard.setPrimaryClip(clip)
                        showCopiedMessage = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                ) {
                    Text(
                        text = if (showCopiedMessage) "✓" else "📋",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (showCopiedMessage)
                            Color(0xFF10B981)
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Promo Code Text (Styled to stand out)
            Text(
                text = promoCode,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            // Copied confirmation
            if (showCopiedMessage) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Promo code copied", // Changed text
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

// ========================================
// ✨ NEW: Price Display Component (2025-11-16)
// ========================================
/**
 * ✨ 2025 MODERN SOLUTION: Two-line price display for details screen
 *
 * Display logic:
 * - Both prices: TWO LINES
 *   Line 1: QR 1,995 (pink, 24sp, bold)
 *   Line 2: QR 2,745 -27% (grey + green, 16sp)
 * - One price: SINGLE LINE
 */
@Composable
private fun DealDetailsPrice(
    originalPrice: Double?,
    discountedPrice: Double?
) {
    // Don't show anything if both prices are null
    if (originalPrice == null && discountedPrice == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            // Case 1: Both prices exist - TWO LINES
            originalPrice != null && discountedPrice != null -> {
                val discountPercent = ((originalPrice - discountedPrice) / originalPrice * 100).toInt()

                // Line 1: Discounted price (prominent, pink)
                Text(
                    text = formatPrice(discountedPrice),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp
                    ),
                    color = Color(0xFFE91E63)  // Pink highlight
                )

                // Line 2: Original price + percentage (grey + green)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatPrice(originalPrice),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        ),
                        color = Color.Gray
                    )

                    Text(
                        text = "-$discountPercent%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        ),
                        color = Color(0xFF10B981)  // Green
                    )
                }
            }

            // Case 2: Only original price - SINGLE LINE
            originalPrice != null -> {
                Text(
                    text = formatPrice(originalPrice),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Case 3: Only discounted price - SINGLE LINE (pink)
            discountedPrice != null -> {
                Text(
                    text = formatPrice(discountedPrice),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFE91E63)  // Pink highlight
                )
            }
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
