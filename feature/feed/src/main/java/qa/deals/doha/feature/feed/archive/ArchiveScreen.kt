package qa.deals.doha.feature.archive

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import qa.deals.domain.DealCategory
import qa.deals.doha.feature.feed.components.DealCard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.ExperimentalFoundationApi // For animateItem

/**
 * ========================================
 * ✅ SPRINT 5: ARCHIVE SCREEN
 * Shows archived deals (deals older than 10 days)
 * ========================================
 *
 * Created: 2025-10-27 (Sprint 5: Archive Feature)
 * - Mirrors FeedScreen pattern for consistency
 * - Search and category filtering
 * - Pull-to-refresh and lazy loading
 * - 2-column grid layout
 * - Back button navigation
 *
 * FEATURES:
 * - View archived deals only (isArchived = true)
 * - Search archived deals by title/description
 * - Filter by category
 * - Pagination support (load more)
 * - Pull-to-refresh
 * - Grid layout (2 columns)
 *
 * @param onBackClick Callback when back button is pressed
 * @param onDealClick Callback when a deal card is clicked
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    onBackClick: () -> Unit,
    onDealClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // ✅ ViewModel with factory
    val context = LocalContext.current
    val viewModel: ArchiveViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ArchiveViewModel(context) as T
            }
        }
    )

    // ✅ Collect state from ViewModel
    val archivedDeals by viewModel.archivedDeals.collectAsState()
    val state = viewModel.uiState
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    // ✅ Grid state for lazy loading
    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

    // ✅ Search bar state
    var searchActive by remember { mutableStateOf(false) }

    // ========================================
    // ✅ CATEGORY FILTER CHIPS
    // Horizontal scrolling chips for category filtering
    // ========================================
    @Composable
    fun CategoryFilterChips(
        selectedCategory: DealCategory?,
        onCategorySelected: (DealCategory?) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val scrollState = rememberScrollState()

        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All" chip
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
                    selectedContainerColor = Color(0xFF9C27B0),
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
                    .animateContentSize()
            )

            // Category chips
            DealCategory.values().forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.emoji,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                            )
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
                        borderWidth = 1.5.dp,
                        selectedBorderWidth = 2.dp
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .animateContentSize()
                )
            }
        }
    }

    // ========================================
    // ✅ MAIN SCAFFOLD
    // ========================================
    Scaffold(
        topBar = {
            // ========================================
            // ✅ TOP BAR with gradient background
            // Shows title, deal count, and back button
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF9C27B0),  // Purple
                                Color(0xFF7B1FA2)   // Darker purple
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(1000f, 1000f)
                        )
                    )
            ) {
                // Top bar with back button and title
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "📦 Archive",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "${archivedDeals.size} archived deals",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Search icon
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                // Search bar (expandable)
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = {
                            Text(
                                "Search archived deals...",
                                color = Color(0xFF9E9E9E) // Light grey placeholder for contrast on white
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF757575) // Medium grey icon
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium, // Rounded corners for modern look
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = Color(0xFF424242), // Dark grey text when typing
                            unfocusedTextColor = Color(0xFF424242), // Dark grey text
                            cursorColor = Color(0xFF9C27B0), // Purple cursor matching app theme
                            focusedBorderColor = Color(0xFF9C27B0), // Purple border when focused
                            unfocusedBorderColor = Color.White.copy(alpha = 0.85f)
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp
                        )
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ========================================
            // ✅ CATEGORY FILTER CHIPS
            // ========================================
            CategoryFilterChips(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    viewModel.filterByCategory(category)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ========================================
            // ✅ LOADING/ERROR/SUCCESS STATES
            // ========================================
            when {

                // CASE 2: Error State
                state.error != null && archivedDeals.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = "😞", style = MaterialTheme.typography.displayLarge)
                            Text(text = "Error loading archive", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = state.error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.refreshArchivedDeals() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                // CASE 3: Empty State
                archivedDeals.isEmpty() && !state.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "📦",
                                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp)
                            )
                            Text(
                                text = "No Archived Deals",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Deals older than 10 days are automatically archived.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // CASE 4: Success State (Has Deals)
                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.loading,
                        onRefresh = { viewModel.refreshArchivedDeals() },
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ✅ Detect when user scrolls near bottom
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo }
                                .collect { layoutInfo ->
                                    val totalItems = layoutInfo.totalItemsCount
                                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

                                    // Load more when user is within 4 items from bottom
                                    if (totalItems > 0 && lastVisibleItem >= totalItems - 4) {
                                        viewModel.loadMoreArchivedDeals()
                                    }
                                }
                        }

                        // ✅ Grid with archived deals (2 columns)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 8.dp,
                                bottom = 16.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = archivedDeals,
                                key = { it.id }
                            ) { deal ->
                                DealCard(
                                    deal = deal,
                                    onClick = { onDealClick(deal.id) },
                                    onVoteHot = { /* Archived deals can't be voted */ },
                                    onVoteCold = { /* Archived deals can't be voted */ },
                                    hasVoted = true, // Disable voting for archived deals
                                    userVoteType = null,
                                    optimisticHotCount = null,
                                    optimisticColdCount = null,
                                    modifier = Modifier.animateItem()
                                )
                            }

                            // ✅ Loading indicator at bottom when loading more
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