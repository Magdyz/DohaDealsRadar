package qa.deals.doha.feature.feed

import androidx.compose.animation.animateContentSize
import qa.deals.doha.feature.feed.components.SkeletonDealCard  //  NEW: Skeleton loading card
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import qa.deals.domain.DealCategory
import qa.deals.doha.feature.feed.components.DealCard
import qa.deals.doha.feature.feed.components.VoteAuthDialog  // ✅ NEW: Vote authentication dialog
import qa.deals.doha.feature.feed.components.GradientMoreMenu  // ✅ NEW: Gradient menu component
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.rememberCoroutineScope // ✅ NEW: Import
import kotlinx.coroutines.delay // ✅ NEW: Import (was previously used by GlobalScope)
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import android.net.Uri
import qa.deals.doha.analytics.AnalyticsManager


/**
 * ========================================
 * ✨ CATEGORY FILTER CHIPS - 2025 MODERN
 * ========================================
 *
 * Created: 2025-10-19 19:48:28 UTC by @Magdyz
 * Updated: 2025-11-13 - Replaced dropdown with toggleable All/Newest chips
 *
 * FEATURES:
 * - Horizontal scrolling category chips (smaller, 3-4 fit on screen)
 * - All + Newest (mutually exclusive sort options)
 * - Individual category filters (toggleable, only one at a time)
 * - Selected state with purple filled background
 * - Unselected state with light grey outline
 * - Smooth animations on selection
 *
 * DESIGN:
 * - Smaller chips: 32dp height, 13sp font for better density
 * - Selected: Purple (#9C27B0) filled with white text + 2dp border
 * - Unselected: Transparent with light grey outline + dark text
 * - Category emoji + name for visual identification
 *
 * @param selectedCategory Currently selected category (null = none)
 * @param onCategoryToggle Callback when category is toggled
 * @param sortOption Current sort option (HOTTEST = All, NEWEST = Newest)
 * @param onAllClick Callback when All chip is pressed
 * @param onNewestToggle Callback when Newest chip is toggled
 * @param modifier Optional modifier for the chip row
 */
@Composable
private fun CategoryFilterChips(
    selectedCategory: DealCategory?,
    onCategoryToggle: (DealCategory) -> Unit,
    sortOption: SortOption,
    onAllClick: () -> Unit,
    onNewestToggle: () -> Unit,
    onArchiveClick: () -> Unit = {},  // ✅ SPRINT 6: Navigate to archive screen
    isAdmin: Boolean = false,  // ✅ NEW: Admin-only archive access
    modifier: Modifier = Modifier
) {
    // ✅ PRESERVED: Scroll state for horizontal scrolling
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),  // ✨ Increased vertical padding for breathing space
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ========================================
        // ✨ NEW: "ALL" CHIP (Sort by Hottest)
        // Selected when sortOption = HOTTEST
        // ========================================
        FilterChip(
            selected = sortOption == SortOption.HOTTEST,
            onClick = onAllClick,
            label = {
                Text(
                    text = "All",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF9C27B0),
                selectedLabelColor = Color.White,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = sortOption == SortOption.HOTTEST,
                borderColor = if (sortOption == SortOption.HOTTEST)
                    Color(0xFF9C27B0)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                selectedBorderColor = Color(0xFF9C27B0),
                borderWidth = 1.dp,
                selectedBorderWidth = 1.5.dp
            ),
            modifier = Modifier.height(32.dp)
        )

        // ========================================
        // ✨ NEW: "NEWEST" CHIP (Sort by Newest)
        // Mutually exclusive with "All"
        // ========================================
        FilterChip(
            selected = sortOption == SortOption.NEWEST,
            onClick = onNewestToggle,
            label = {
                Text(
                    text = "Newest",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF9C27B0),
                selectedLabelColor = Color.White,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = sortOption == SortOption.NEWEST,
                borderColor = if (sortOption == SortOption.NEWEST)
                    Color(0xFF9C27B0)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                selectedBorderColor = Color(0xFF9C27B0),
                borderWidth = 1.dp,
                selectedBorderWidth = 1.5.dp
            ),
            modifier = Modifier.height(32.dp)
        )

        // ========================================
        // ✨ UPDATED: CATEGORY CHIPS (Smaller, Toggleable)
        // One chip for each DealCategory enum value
        // ========================================
        DealCategory.values().forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategoryToggle(category) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category emoji (visual identifier)
                        Text(
                            text = category.emoji,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 14.sp
                            )
                        )
                        // Category name
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF9C27B0),
                    selectedLabelColor = Color.White,
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedCategory == category,
                    borderColor = if (selectedCategory == category)
                        Color(0xFF9C27B0)
                    else
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    selectedBorderColor = Color(0xFF9C27B0),
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp
                ),
                modifier = Modifier
                    .height(32.dp)
                    .animateContentSize()
            )
        }
        // ========================================
        // ✅ ADMIN-ONLY: "ARCHIVE" CHIP
        // Navigate to archive screen to view old deals (admins only)
        // ========================================
        if (isAdmin) {
            FilterChip(
                selected = false,  // Never selected (it's a navigation button)
                onClick = onArchiveClick,
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📦",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp)
                        )
                        Text(
                            text = "Archive",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF9C27B0),
                    selectedLabelColor = Color.White,
                    containerColor = Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    selectedBorderColor = Color(0xFF9C27B0),
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp
                ),
                modifier = Modifier
                    .height(32.dp)
                    .animateContentSize()
            )
        }

    }
}

/**
 * ========================================
 * ✨ FEED SCREEN - 2025 UPDATED
 * ========================================
 *
 * Updated: 2025-10-19 19:48:28 UTC by @Magdyz
 *
 * NEW FEATURES:
 * - ✨ Category filter chips below search bar
 * - ✨ Horizontal scrolling categories
 * - ✨ Filter deals by category
 * - ✨ Always sorted by hot votes (highest to lowest)
 *
 * PRESERVED FEATURES:
 * - ✅ Search functionality
 * - ✅ Pull-to-refresh
 * - ✅ Gradient FAB for posting
 * - ✅ Grid layout (2 columns)
 * - ✅ Loading states (no double spinner)
 * - ✅ Error handling with retry
 * - ✅ Voting system
 * - ✅ Optimistic UI updates
 *
 * FUNCTIONALITY:
 * - Grid layout with 2 columns for deals
 * - Search bar in top app bar
 * - Category filter chips below search
 * - Pull-to-refresh to reload deals
 * - FAB for posting new deals
 * - Smart loading states (single spinner on first load)
 *
 * @param onDealClick Callback when deal card is clicked
 * @param onPostClick Callback when FAB is clicked to post deal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onDealClick: (String) -> Unit = {},
    onPostClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},  // ✅ SPRINT 6: Navigate to archive screen
    onAccountClick: () -> Unit = {},  // ✅ SPRINT 5: Navigate to account/login
    onFeedbackClick: () -> Unit = {},  // ✅ NEW: Navigate to feedback screen (2025-11-22)
    onNotificationsClick: () -> Unit = {},  // ✅ NEW: Navigate to notifications screen (2025-11-25)

) {
    val context = LocalContext.current

    // ✅ PRESERVED: Create ViewModel with Context using proper Factory
    val viewModel: FeedViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FeedViewModel(context) as T
            }
        }
    )

    // ✅ PRESERVED: Collect state from ViewModel
    val deals by viewModel.deals.collectAsState()
    val state = viewModel.uiState
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()  // ✨ NEW: Category state
    val sortOption by viewModel.sortOption.collectAsState()  // ✨ NEW: Sort option state
    val isAdmin by viewModel.isAdmin.collectAsState()  // ✅ NEW: Admin detection for delete button

    // ✅ PRESERVED: Grid and pull-to-refresh states
    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

    // ========================================
    // 📊 ANALYTICS: Track Scroll Depth
    // Measures if users scroll to see "Cold" deals or only view top "Hot" deals
    // ========================================
    LaunchedEffect(gridState) {
        var lastTrackedPosition = 0

        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { currentPosition ->
                // Track the maximum scroll position reached
                if (currentPosition > lastTrackedPosition) {
                    lastTrackedPosition = currentPosition

                    // Track after user scrolls past 10 items
                    if (currentPosition >= 10 && currentPosition % 10 == 0) {
                        val totalDeals = deals.size
                        val scrollPercentage = if (totalDeals > 0) {
                            ((currentPosition.toFloat() / totalDeals) * 100).toInt().coerceIn(0, 100)
                        } else 0

                        AnalyticsManager.trackScrollDepth(
                            maxPosition = currentPosition,
                            totalDeals = totalDeals,
                            scrollPercentage = scrollPercentage
                        )
                    }
                }
            }
    }

// ========================================
    // ✅ FIX (1.3): Get a lifecycle-aware CoroutineScope
    // We will use this for the FAB's click animation.
    // =======================================

    val scope = rememberCoroutineScope()

    // ✅ NEW: Delete confirmation dialog state
    var dealToDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // ✅ PRESERVED: Search field in top bar
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("Search deals...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    )
                },
                actions = {
                    GradientMoreMenu(
                        onFeedbackClick = onFeedbackClick,
                        onRateAppClick = {
                            // Open Play Store for rating
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://play.google.com/store/apps/details?id=qa.deals.doha")
                                setPackage("com.android.vending")
                            }
                            context.startActivity(intent)
                        },
                        onNotificationsClick = onNotificationsClick
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },

        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // ✅ 2025 DESIGN: Box layout for dual FAB positioning
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Content in center
            Column(
                modifier = Modifier.fillMaxSize()
            ) {

            // ========================================
            // ✨ UPDATED: CATEGORY FILTER CHIPS (Toggleable)
            // Horizontal scrolling chips below search bar
            // All, Newest, Categories (3-4 fit on screen)
            // ========================================
            CategoryFilterChips(
                selectedCategory = selectedCategory,
                onCategoryToggle = { category ->
                    viewModel.toggleCategory(category)
                },
                sortOption = sortOption,
                onAllClick = {
                    viewModel.setSortToAll()
                },
                onNewestToggle = {
                    viewModel.toggleSortToNewest()
                },
                onArchiveClick = onArchiveClick,
                isAdmin = isAdmin,  // ✅ Pass admin status to show/hide Archive chip
                modifier = Modifier.fillMaxWidth()
            )


            when {
                // ========================================
                // ✨ NEW CASE 0: Filtering/Sorting in Progress
                // Show big centered purple spinner
                // ========================================
                state.isFilteringSorting -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp,
                            color = Color(0xFF9C27B0)  // ✨ Big purple spinner
                        )
                    }
                }

                // ========================================
                // CASE 1: Initial Loading State (Empty with Loading)
                // Show skeleton cards instead of spinner
                // ========================================

                state.loading && deals.isEmpty() && state.error == null -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 88.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Show 6 skeleton cards (3 rows x 2 columns)
                        items(6) {
                            SkeletonDealCard()
                        }
                    }
                }

                // ========================================
                // 🎯 CASE 2: Error State (Empty with Error)
                // ========================================
                state.error != null && deals.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // ✅ PRESERVED: Error emoji and message
                            Text(text = "😞", style = MaterialTheme.typography.displayLarge)
                            Text(text = "Error loading deals", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = state.error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // ✅ PRESERVED: Retry button
                            Button(
                                onClick = { viewModel.refreshDeals() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                // ========================================
                // 🎯 CASE 3: Success State (Has Deals)
                // Show pull-to-refresh wrapper ONLY here
                // ========================================
                else -> {
                    // ✅ PRESERVED: Pull-to-refresh ONLY wraps content when deals exist
                    PullToRefreshBox(
                        isRefreshing = state.loading && !state.isLoadingMore,
                        onRefresh = { viewModel.refreshDeals() },
                        state = pullToRefreshState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullToRefreshState,
                                isRefreshing = state.loading && !state.isLoadingMore,
                                color = MaterialTheme.colorScheme.primary,  // ✅ Purple
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        },
                                modifier = Modifier.fillMaxSize()
                    ) {
// ========================================
                        // ✅ NEW: Detect when user scrolls near bottom
                        // Triggers loadMoreDeals() automatically
                        // ========================================
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo }
                                .collect { layoutInfo ->
                                    val totalItems = layoutInfo.totalItemsCount
                                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

                                    // Load more when user is within 4 items from bottom
                                    if (totalItems > 0 && lastVisibleItem >= totalItems - 4) {
                                        viewModel.loadMoreDeals()
                                    }
                                }
                        }

                        // ✅ OPTIMIZED: Buttery smooth grid like Instagram

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 8.dp,
                                bottom = 88.dp  // ✅ Space for FAB
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                items = deals,
                                key = { it.id },
                                contentType = { "deal_card" }  // ✅ 2025: Helps Compose reuse compositions
                            ) { deal ->
                                // ✅ UPDATED: DealCard with unified voting (Instagram Pattern)
                                DealCard(
                                    deal = deal,
                                    onClick = { onDealClick(deal.id) },
                                    // ✅ NEW: Unified vote callback (debounced + instant local update)
                                    onVote = { voteType -> viewModel.onVoteClicked(deal.id, voteType) },
                                    hasVoted = viewModel.hasVoted(deal.id),
                                    userVoteType = viewModel.getVoteType(deal.id),
                                    // ✅ REMOVED: optimisticCounts - DB is now source of truth (zero lag)
                                    // ✅ NEW: Admin-only delete button
                                    showDeleteButton = isAdmin,
                                    onDelete = { dealToDelete = deal.id },

                                    // ✅ UPDATED: Removed animateItem() to prevent lag during category switches
                                    // Hardware acceleration still applied for smooth scrolling
                                    modifier = Modifier
                                        .graphicsLayer {
                                            // Hardware-accelerated rendering - prevents layout on scroll
                                        }
                                )
                            }

                            // ========================================
                            // ✅ NEW: Loading indicator at bottom when loading more
                            // Shows small spinner while fetching next page
                            // ========================================
                            if (state.isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(50.dp),
                                            strokeWidth = 3.dp,
                                            color = MaterialTheme.colorScheme.primary,  // ✅ Purple

                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }  // End of Column

        // ========================================
        // ✨ 2025 UBER EATS STYLE: Bottom Gradient Backdrop
        // Vertical gradient that fades from solid grey (bottom) to transparent (top)
        // Creates pleasing depth effect behind the FAB buttons
        // ========================================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp) // Reduced height for more compact gradient
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,                              // Top: fully transparent
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f), // Fade starts
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), // Mid fade
                            MaterialTheme.colorScheme.surface                // Bottom: solid grey (same as top bar)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // ========================================
        // ✅ 2025 DESIGN: Account FAB - Bottom Left
        // Now floating on top of gradient backdrop
        // ========================================
        var isAccountPressed by remember { mutableStateOf(false) }

        FloatingActionButton(
            onClick = {
                isAccountPressed = true
                onAccountClick()
                scope.launch {
                    delay(150)
                    isAccountPressed = false
                }
            },
            containerColor = Color.Transparent,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,  // Enhanced elevation for better depth
                pressedElevation = 12.dp,
                hoveredElevation = 10.dp
            ),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .size(if (isAccountPressed) 60.dp else 64.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE91E63),  // Pink first (reversed)
                                Color(0xFF9C27B0)   // Purple second
                            ),
                            start = Offset(0f, Float.POSITIVE_INFINITY),
                            end = Offset(Float.POSITIVE_INFINITY, 0f)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "My Account",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // ========================================
        // ✅ 2025 DESIGN: Post FAB - Bottom Right
        // Now floating on top of gradient backdrop
        // ========================================
        var isPostPressed by remember { mutableStateOf(false) }

        FloatingActionButton(
            onClick = {
                isPostPressed = true
                onPostClick()
                scope.launch {
                    delay(150)
                    isPostPressed = false
                }
            },
            containerColor = Color.Transparent,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,  // Enhanced elevation for better depth
                pressedElevation = 12.dp,
                hoveredElevation = 10.dp
            ),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(if (isPostPressed) 60.dp else 64.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF9C27B0),  // Purple first (original)
                                Color(0xFFE91E63)   // Pink second
                            ),
                            start = Offset(0f, Float.POSITIVE_INFINITY),
                            end = Offset(Float.POSITIVE_INFINITY, 0f)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Post a deal",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        }  // End of Box (this is correct placement)
    }  // End of Scaffold

    // ========================================
    // ✅ NEW: Delete Confirmation Dialog
    // ========================================

    dealToDelete?.let { dealId ->
        AlertDialog(
            onDismissRequest = { dealToDelete = null },
            title = { Text("Delete Deal Permanently") },
            text = {
                Text(
                    "This will permanently delete the deal and its image from the database. " +
                            "This action cannot be undone. Are you sure?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.permanentDeleteDeal(dealId)
                        dealToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)  // Red
                    )
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { dealToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ========================================
    // ✅ NEW: Vote Authentication Dialog
    // Shows when anonymous user tries to vote
    // ========================================

    state.pendingVote?.let { pendingVote ->
        if (state.showVoteAuthDialog) {
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
}  // End of FeedScreen
