package qa.deals.doha.feature.feed

import androidx.compose.animation.animateContentSize
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
                .animateContentSize()  // ✨ Smooth size transitions
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
    onPostClick: () -> Unit = {}
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

    // ✅ PRESERVED: Grid and pull-to-refresh states
    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

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
        floatingActionButton = {
            // ✅ PRESERVED: Gradient FAB with press animation
            var isPressed by remember { mutableStateOf(false) }

            FloatingActionButton(
                onClick = {
                    isPressed = true
                    onPostClick()
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(150)
                        isPressed = false
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
                    .size(64.dp)
                    .then(
                        if (isPressed) {
                            Modifier.size(60.dp)
                        } else {
                            Modifier.size(64.dp)
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF9C27B0),
                                    Color(0xFFE91E63)
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                modifier = Modifier.fillMaxWidth()
            )

            // ========================================
            // ✅ PRESERVED: Loading/Error/Success States
            // Smart state management prevents double spinner
            // ========================================
            when {
                // ========================================
                // 🎯 CASE 1: Initial Load (First Time)
                // Show ONLY center spinner, NO pull-to-refresh
                // ========================================
                state.loading && deals.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // ✅ PRESERVED: Modern large spinner (2025 style)
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )

                            // ✅ PRESERVED: Loading text
                            Text(
                                text = "Loading deals...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        isRefreshing = state.loading,
                        onRefresh = { viewModel.refreshDeals() },
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ✅ PRESERVED: Grid with deals (2 columns)
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

                        // ✅ PRESERVED: Grid with deals (2 columns)
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = deals,
                                key = { it.id }
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
                                    modifier = Modifier.animateItem()
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
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}