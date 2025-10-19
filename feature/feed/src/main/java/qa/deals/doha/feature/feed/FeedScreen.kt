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
            // ✨ Animation state for press effect
            var isPressed by remember { mutableStateOf(false) }

            FloatingActionButton(
                onClick = {
                    isPressed = true
                    onPostClick()
                    // Reset animation after a short delay
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(150)
                        isPressed = false
                    }
                },
                containerColor = Color.Transparent,  // ✅ Transparent to show gradient
                contentColor = Color.White,          // ✅ Icon color
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,         // ✅ Subtle shadow at rest
                    pressedElevation = 10.dp,        // ✅ INCREASED: Stronger shadow when pressed (was 8dp)
                    hoveredElevation = 8.dp          // ✅ INCREASED: Medium shadow on hover
                ),
                shape = CircleShape,                 // ✅ Perfect circle
                modifier = Modifier
                    .size(64.dp)                     // ✨ CHANGED: 64dp (was 56dp) - Modern large FAB
                    .then(
                        if (isPressed) {
                            Modifier.size(60.dp)     // ✨ CHANGED: Shrink to 60dp when pressed (4dp reduction)
                        } else {
                            Modifier.size(64.dp)     // ✅ Normal size
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()               // ✅ Fill the FAB circle
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF9C27B0),  // ✅ Purple (Material Purple 500)
                                    Color(0xFFE91E63)   // ✅ Pink (Material Pink 500)
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
                        modifier = Modifier.size(32.dp)  // ✨ INCREASED: 32dp icon (was 28dp) - Scales with larger FAB
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // ✨ NEW: Wrap content with PullToRefreshBox for swipe-to-refresh
        PullToRefreshBox(
            isRefreshing = state.loading,  // ✅ Use existing loading state
            onRefresh = {
                viewModel.refreshDeals()  // ✅ Use existing refresh function
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ✅ PRESERVED: All existing UI states maintained
            when {
                // Loading state (initial load only)
                state.loading && deals.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Error state
                state.error != null && deals.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "😞", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Error loading deals", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(24.dp))
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

                // Success - Grid layout
                else -> {
                    // ✅ PRESERVED: LazyVerticalGrid completely unchanged
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
                        // ✅ REMOVED: Loading indicator during refresh
                        // Pull-to-refresh shows its own indicator at the top
                        // This prevents duplicate loading indicators

                        // ✅ PRESERVED: Deal cards with all optimistic voting
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