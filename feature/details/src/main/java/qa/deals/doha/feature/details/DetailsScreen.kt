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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
// ✨ NEW: Advanced Coil imports for 2025 performance
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.imePadding

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    dealId: String,
    onBackClick: () -> Unit = {},
    onReportClick: () -> Unit = {}
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
    // ✨ Shimmer animation
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)  // ✅ Square like feed cards
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
            )
    ) {
        // ✅ Centered loading indicator
        CircularProgressIndicator(
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
    }
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
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            .scale(Scale.FILL)
                            .crossfade(300)
                            .placeholderMemoryCacheKey(imageUrl)
                            .build(),
                        contentDescription = deal.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ImageSkeleton() },
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
            // Content Section
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
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
                                    enabled = !uiState.hasVoted,
                                    onClick = { if (!uiState.hasVoted) onVote("hot") }
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
                                    enabled = !uiState.hasVoted,
                                    onClick = { if (!uiState.hasVoted) onVote("cold") }
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
                            .clickable(onClick = onReport),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🚩", style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
                    }
                }

                // Timestamp
                deal.createdAt?.let { createdAt ->
                    Text(
                        text = "Posted ${getRelativeTimeString(createdAt)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ========================================
                // Title (unchanged)
                // ========================================
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // ========================================
                // ✨ UPDATED: Expandable Description
                // ========================================
                if (!deal.description.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = deal.description!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = descriptionMaxLines,
                            overflow = if (isDescriptionLong && !isDescriptionExpanded) {
                                TextOverflow.Ellipsis  // ✅ Long text: show "..." when collapsed
                            } else {
                                TextOverflow.Clip  // ✅ Short text or expanded: no ellipsis
                            }
                        )

                        // ✨ "See more" / "See less" button
                        if (deal.description!!.length > 100) {  // Only show if description is long
                            Text(
                                text = if (isDescriptionExpanded) "See less ▲" else "See more ▼",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF8B7BA8),  // App primary color (purple)
                                modifier = Modifier.clickable {
                                    isDescriptionExpanded = !isDescriptionExpanded
                                }
                                    .padding(top = 4.dp)  // Extra spacing from description
                            )
                        }
                    }
                }

                // ========================================
                // Category Display (unchanged)
                // ========================================
                deal.category?.let { categoryId ->
                    Spacer(modifier = Modifier.height(12.dp))
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
                // ✅ Location Card (stays inline - NO floating button)
                // ========================================
                if (!deal.location.isNullOrBlank()) {
                    LocationCard(location = deal.location!!, context = context)

                    // Promo code extraction (unchanged)
                    if (deal.link != null && !deal.description.isNullOrBlank()) {
                        val promoMatch = Regex("code[:=]?\\s*([A-Z0-9]+)", RegexOption.IGNORE_CASE)
                            .find(deal.description!!)
                        val promoCode = promoMatch?.groupValues?.getOrNull(1)

                        if (promoCode != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Promo Code", promoCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "✅ Promo code copied!", Toast.LENGTH_SHORT).show()
                                    },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "🎟️ Promo Code",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = promoCode,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    letterSpacing = 1.2.sp
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(text = "📋", style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // ✅ Show inline link button if both location and link exist
                    if (deal.link.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { onOpenLink(deal.link) },
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
            }
        }

        // ========================================
        // ✨ FLOATING "VIEW DEAL" BUTTON
        // Only for online deals (has link, no location)
        // ========================================
        if (hasLink && deal.location.isNullOrBlank()) {
            Button(
                onClick = { onOpenLink(deal.link) },
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
    context: android.content.Context
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