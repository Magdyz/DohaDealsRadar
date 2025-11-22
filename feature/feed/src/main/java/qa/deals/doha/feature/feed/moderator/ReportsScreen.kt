package qa.deals.doha.feature.feed.moderator

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Screen showing submitted reports for moderators to review
 *
 * CREATED: 2025-11-22
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBackClick: () -> Unit,
    onDealClick: (String) -> Unit,  // Navigate to deal by dealId
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
    val reports by viewModel.reports.collectAsState()
    val listState = rememberLazyListState()

    var showActionDialog by remember { mutableStateOf(false) }
    var selectedReportId by remember { mutableStateOf<String?>(null) }

    // Get DeviceIdManager and set current user
    val deviceIdManager = remember {
        qa.deals.doha.datastore.DeviceIdManager.getInstance(context)
    }

    LaunchedEffect(Unit) {
        val userId = deviceIdManager.getUserId()
        if (userId != null) {
            viewModel.setCurrentUser(userId)
            // Request reports refresh (will wait for role to load if needed)
            viewModel.requestReportsRefresh()
        }
    }

    // Also refresh reports when role becomes moderator
    LaunchedEffect(uiState.isModerator) {
        if (uiState.isModerator && reports.isEmpty() && !uiState.isLoadingReports) {
            Log.d("ReportsScreen", "Role confirmed as moderator, refreshing reports")
            viewModel.refreshReports()
        }
    }

    Scaffold(
        topBar = {
            // Purple gradient header (matches moderator dashboard theme)
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
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "🚨 Submitted Reports",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "${reports.size} reports to review",
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoadingReports,
            onRefresh = { viewModel.refreshReports() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Error state
                uiState.error != null && !uiState.isLoadingReports -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Error loading reports",
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
                            Button(onClick = { viewModel.refreshReports() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                // Empty state
                reports.isEmpty() && !uiState.isLoadingReports -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF9FAFB)),
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
                                text = "No reports to review",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "All clear!",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }

                // List of reports
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF9FAFB)),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = reports,
                            key = { it.id ?: it.dealId ?: System.currentTimeMillis() }
                        ) { report ->
                            ReportCard(
                                report = report,
                                onViewDeal = {
                                    report.dealId?.let { dealId ->
                                        onDealClick(dealId)
                                    }
                                },
                                onDismiss = { reason ->
                                    report.id?.let { reportId ->
                                        viewModel.dismissReport(reportId, reason)
                                    }
                                },
                                onTakeAction = {
                                    selectedReportId = report.id
                                    showActionDialog = true
                                },
                                actionInProgress = uiState.actionInProgress
                            )
                        }

                        // Loading more indicator (only during pagination, not initial load)
                        if (uiState.isLoadingMoreReports && !uiState.isLoadingReports) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF9C27B0))
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
                            if (shouldLoadMore && uiState.reportsHasMorePages && !uiState.isLoadingMoreReports) {
                                viewModel.loadMoreReports()
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

    // Action Dialog
    if (showActionDialog && selectedReportId != null) {
        TakeActionDialog(
            onConfirm = { action, reason ->
                showActionDialog = false
                selectedReportId?.let { reportId ->
                    viewModel.resolveReport(reportId, action, reason)
                }
                selectedReportId = null
            },
            onDismiss = {
                showActionDialog = false
                selectedReportId = null
            }
        )
    }
}

/**
 * Dialog for taking action on a report
 */
@Composable
private fun TakeActionDialog(
    onConfirm: (action: String, reason: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAction by remember { mutableStateOf("delete_deal") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Take Action on Report") },
        text = {
            Column {
                Text("What action would you like to take?")
                Spacer(modifier = Modifier.height(16.dp))

                // Action selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedAction == "delete_deal",
                            onClick = { selectedAction = "delete_deal" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete the reported deal", fontSize = 14.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedAction == "warn_user",
                            onClick = { selectedAction = "warn_user" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Warn the user who posted", fontSize = 14.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedAction == "ban_user",
                            onClick = { selectedAction = "ban_user" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ban the user who posted", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reason field
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("E.g., Violates community guidelines") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedAction, reason.takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444)
                )
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
