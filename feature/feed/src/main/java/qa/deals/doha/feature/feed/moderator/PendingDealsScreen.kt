package qa.deals.doha.feature.feed.moderator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import qa.deals.doha.db.DealEntity

/**
 * Screen showing pending deals queue for moderators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingDealsScreen(
    onBackClick: () -> Unit,
    onDealClick: (DealEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ModeratorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ModeratorViewModel(context) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val pendingDeals by viewModel.pendingDeals.collectAsState()
    val listState = rememberLazyListState()

    // Get DeviceIdManager and set current user
    val deviceIdManager = remember {
        qa.deals.doha.datastore.DeviceIdManager.getInstance(context)
    }

    LaunchedEffect(Unit) {
        val userId = deviceIdManager.getUserId()
        if (userId != null) {
            viewModel.setCurrentUser(userId)
        } else {
            android.util.Log.w("PendingDeals", "⚠️ No userId found in DeviceIdManager")
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pending Deals",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${pendingDeals.size} deals awaiting review",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1F2937)
                )
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.loading,
            onRefresh = { viewModel.refreshPendingDeals() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Error state
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Error loading pending deals",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6B7280)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "",
                                fontSize = 14.sp,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refreshPendingDeals() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                // Empty state
                pendingDeals.isEmpty() && !uiState.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎉",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No pending deals",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All caught up!",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }

                // List of pending deals
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = pendingDeals,
                            key = { it.id }
                        ) { deal ->
                            DealApprovalCard(
                                deal = deal,
                                onApprove = { viewModel.approveDeal(deal.id) },
                                onReject = { viewModel.rejectDeal(deal.id) },
                                onDelete = { viewModel.deleteDeal(deal.id) },
                                onClick = { onDealClick(deal) },
                                actionInProgress = uiState.actionInProgress
                            )
                        }

                        // Loading more indicator
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }

                    // Detect scroll to bottom for pagination
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisibleItem >= totalItems - 3
                        }.collect { shouldLoadMore ->
                            if (shouldLoadMore && uiState.hasMorePages && !uiState.isLoadingMore) {
                                viewModel.loadMorePendingDeals()
                            }
                        }
                    }
                }
            }

            // Success/Error Snackbar
            uiState.actionSuccess?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = Color(0xFF10B981)
                ) {
                    Text(message, color = Color.White)
                }
            }

            uiState.actionError?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = Color(0xFFEF4444)
                ) {
                    Text(message, color = Color.White)
                }
            }
        }
    }
}
