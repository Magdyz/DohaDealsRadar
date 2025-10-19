package qa.deals.doha.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import qa.deals.doha.feature.feed.components.DealCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
// Add this import
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.launch

/**
 * Feed Screen - Grid layout with 2 columns
 * ✅ Proper ViewModel creation with Context
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onDealClick: (String) -> Unit = {},
    onPostClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // ✅ FIXED: Create ViewModel with Context using proper Factory
    val viewModel: FeedViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FeedViewModel(context) as T
            }
        }
    )

    val deals by viewModel.deals.collectAsState()
    val state = viewModel.uiState

    val gridState = rememberLazyGridState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // ✨ NEW: Pull-to-refresh state
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
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
            // ✅ PRESERVED: Gradient FAB (no changes)
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

        // ========================================
        // ✨ FIX: Smart loading state management
        // Prevents double spinners
        // ========================================

        when {
            // ========================================
            // 🎯 CASE 1: Initial Load (First Time)
            // Show ONLY center spinner, NO pull-to-refresh
            // ========================================
            state.loading && deals.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ✨ Modern 2025: Larger, more visible spinner
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),  // ✨ LARGER: 56dp (was 40dp default)
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp  // ✨ THICKER: More visible
                        )

                        // ✨ Optional: Loading text (modern apps show this)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = "😞", style = MaterialTheme.typography.displayLarge)
                        Text(text = "Error loading deals", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                // ✨ FIX: Pull-to-refresh ONLY wraps content when deals exist
                PullToRefreshBox(
                    isRefreshing = state.loading,  // ✅ Now only shows top indicator during refresh
                    onRefresh = { viewModel.refreshDeals() },
                    state = pullToRefreshState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // ✅ Grid with deals
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 88.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = deals,
                            key = { it.id }
                        ) { deal ->
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
                    }
                }
            }
        }
    }
}