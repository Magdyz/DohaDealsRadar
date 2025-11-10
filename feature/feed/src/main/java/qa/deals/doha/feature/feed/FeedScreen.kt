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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.rememberCoroutineScope // ✅ NEW: Import
import kotlinx.coroutines.delay // ✅ NEW: Import (was previously used by GlobalScope)
// ========================================
// ✅ NEW IMPORTS (for animateItem)
// ========================================
import androidx.compose.foundation.ExperimentalFoundationApi // ✅ NEW: Required for animateItem
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.graphics.graphicsLayer


/**
 * ========================================
 * ✨ CATEGORY FILTER CHIPS - 2025 MODERN
 * ========================================
 *
 * Created: 2025-10-19 19:48:28 UTC by @Magdyz
 *
 * FEATURES:
 * - Horizontal scrolling category chips
 * - "All" + individual category filters
 * - Selected state with purple filled background
 * - Unselected state with light grey outline
 * - Smooth animations on selection
 * - Modern pill-shaped design (matches Vinted style)
 *
 * DESIGN:
 * - Selected: Purple (#9C27B0) filled with white text + 2dp border
 * - Unselected: Transparent with light grey outline + dark text
 * - Category emoji + name for visual identification
 * - Auto-scroll to selected chip (future enhancement)
 *
 * @param selectedCategory Currently selected category (null = "All")
 * @param onCategorySelected Callback when category is selected
 * @param modifier Optional modifier for the chip row
 */
@Composable
private fun CategoryFilterChips(
    selectedCategory: DealCategory?,
    onCategorySelected: (DealCategory?) -> Unit,
    onArchiveClick: () -> Unit = {},  // ✅ SPRINT 6: Navigate to archive screen
    onModeratorClick: () -> Unit = {},  // SPRINT 5: Navigate to moderator dashboard
    modifier: Modifier = Modifier
) {
    // ✅ PRESERVED: Scroll state for horizontal scrolling
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ========================================
        // ✨ NEW: "All" CHIP (Always First)
        // Shows all categories when selected
        // ========================================
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = {
                Text(
                    text = "All",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF9C27B0),  // ✨ Purple (brand color)
                selectedLabelColor = Color.White,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedCategory == null,
                borderColor = if (selectedCategory == null)
                    Color(0xFF9C27B0)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                selectedBorderColor = Color(0xFF9C27B0),
                borderWidth = 1.5.dp,
                selectedBorderWidth = 2.dp
            ),
            modifier = Modifier
                .height(40.dp)
        )

        // ========================================
        // ✨ NEW: CATEGORY CHIPS
        // One chip for each DealCategory enum value
        // ========================================
        DealCategory.values().forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category emoji (visual identifier)
                        Text(
                            text = category.emoji,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp
                            )
                        )
                        // Category name
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF9C27B0),  // ✨ Purple
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
                    borderWidth = 1.5.dp,
                    selectedBorderWidth = 2.dp
                ),
                modifier = Modifier
                    .height(40.dp)
                    .animateContentSize()  // ✨ Smooth size transitions
            )
        }
        // ========================================
        // ✅ SPRINT 6: "ARCHIVE" CHIP
        // Navigate to archive screen to view old deals
        // ========================================
        FilterChip(
            selected = false,  // Never selected (it's a navigation button)
            onClick = onArchiveClick,
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📦",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                    )
                    Text(
                        text = "Archive",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
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
                borderWidth = 1.5.dp,
                selectedBorderWidth = 2.dp
            ),
            modifier = Modifier
                .height(40.dp)
                .animateContentSize()
        )

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    onDealClick: (String) -> Unit = {},
    onPostClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},  // ✅ SPRINT 6: Navigate to archive screen
    onAccountClick: () -> Unit = {},  // ✅ SPRINT 5: Navigate to account/login

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
    val isAdmin by viewModel.isAdmin.collectAsState()  // ✅ NEW: Admin detection for delete button

    // ✅ PRESERVED: Grid and pull-to-refresh states
    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

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
            // ✨ NEW: CATEGORY FILTER CHIPS
            // Horizontal scrolling chips below search bar
            // Allows filtering by category
            // ========================================
            CategoryFilterChips(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    viewModel.filterByCategory(category)
                },
                onArchiveClick = onArchiveClick,  // ✅ SPRINT 6: Pass archive click handler
                modifier = Modifier.fillMaxWidth()
            )


            when {

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
                                // ✅ PRESERVED: DealCard with all voting functionality
                                DealCard(
                                    deal = deal,
                                    onClick = { onDealClick(deal.id) },
                                    onVoteHot = { viewModel.voteHot(deal.id) },
                                    onVoteCold = { viewModel.voteCold(deal.id) },
                                    hasVoted = viewModel.hasVoted(deal.id),
                                    userVoteType = viewModel.getVoteType(deal.id),
                                    optimisticHotCount = viewModel.getOptimisticHotCount(deal.id),
                                    optimisticColdCount = viewModel.getOptimisticColdCount(deal.id),
                                    // ✅ NEW: Admin-only delete button
                                    showDeleteButton = isAdmin,
                                    onDelete = { dealToDelete = deal.id },

                                    // ✅ 2025: Hardware acceleration for buttery smooth 60fps
                                    modifier = Modifier
                                        .animateItem()
                                        .graphicsLayer {
                                            // Hardware-accelerated rendering - prevents layout on scroll
                                            // This is THE key to Instagram-like smoothness
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
        // ✅ 2025 DESIGN: Account FAB - Bottom Left
        // Reversed gradient (pink/purple) for visual distinction
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
                defaultElevation = 6.dp,
                pressedElevation = 10.dp,
                hoveredElevation = 8.dp
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
        // Original gradient (purple/pink)
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
                defaultElevation = 6.dp,
                pressedElevation = 10.dp,
                hoveredElevation = 8.dp
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
}  // End of FeedScreen
