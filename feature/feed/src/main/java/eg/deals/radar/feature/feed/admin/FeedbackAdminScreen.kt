package eg.deals.radar.feature.feed.admin

import eg.deals.radar.feature.feed.R

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import eg.deals.radar.network.FeedbackAdminDto

/**
 * ========================================
 * 💬 ADMIN FEEDBACK INBOX (admin only, English-only)
 * ========================================
 * Backed by admin_feedback. Lists user feedback with status filters and
 * lets an admin set status/notes on each item.
 *
 * CREATED: 2025-11-27
 */
private val STATUSES = listOf(null, "pending", "reviewed", "resolved", "archived")
private fun statusLabel(status: String?): String = when (status) {
    null -> "All"
    "pending" -> "Pending"
    "reviewed" -> "Reviewed"
    "resolved" -> "Resolved"
    "archived" -> "Archived"
    else -> status.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackAdminScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: FeedbackAdminViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return FeedbackAdminViewModel() as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val items by viewModel.items.collectAsState()
    val listState = rememberLazyListState()

    var selectedFeedback by remember { mutableStateOf<FeedbackAdminDto?>(null) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9C27B0), Color(0xFF7B1FA2)),
                            start = Offset(0f, 0f),
                            end = Offset(1000f, 1000f)
                        )
                    )
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "💬 Feedback",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "${items.size} item${if (items.size == 1) "" else "s"} shown",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                // Status filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    STATUSES.forEach { status ->
                        FilterChip(
                            selected = uiState.statusFilter == status,
                            onClick = { viewModel.setStatusFilter(status) },
                            label = { Text(statusLabel(status), fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color(0xFF7B1FA2),
                                containerColor = Color.White.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.loading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.error != null && !uiState.loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Error loading feedback", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                            Spacer(Modifier.height(8.dp))
                            Text(uiState.error ?: "", fontSize = 14.sp, color = Color(0xFFEF4444))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.common_retry)) }
                        }
                    }
                }

                items.isEmpty() && !uiState.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📭", fontSize = 48.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("No feedback here", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                            Spacer(Modifier.height(8.dp))
                            Text("Nothing matches this filter", fontSize = 14.sp, color = Color(0xFF6B7280))
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items = items, key = { it.id }) { fb ->
                            FeedbackCard(feedback = fb, onClick = { selectedFeedback = fb })
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF9C27B0))
                                }
                            }
                        }
                    }

                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val layoutInfo = listState.layoutInfo
                            val total = layoutInfo.totalItemsCount
                            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= total - 3
                        }.collect { shouldLoadMore ->
                            if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore) {
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            }

            uiState.actionSuccess?.let { message ->
                Snackbar(modifier = Modifier.padding(16.dp), containerColor = Color(0xFF10B981)) {
                    Text(message, color = Color.White)
                }
            }
            uiState.actionError?.let { message ->
                Snackbar(modifier = Modifier.padding(16.dp), containerColor = Color(0xFFEF4444)) {
                    Text(message, color = Color.White)
                }
            }
        }
    }

    selectedFeedback?.let { fb ->
        UpdateFeedbackDialog(
            feedback = fb,
            actionInProgress = uiState.actionInProgress,
            onConfirm = { status, notes ->
                viewModel.updateStatus(fb.id, status, notes)
                selectedFeedback = null
            },
            onDismiss = { selectedFeedback = null }
        )
    }
}

@Composable
private fun FeedbackCard(
    feedback: FeedbackAdminDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showEmailMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = feedback.status)
                Text(
                    text = formatTimeAgo(feedback.createdAt),
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = feedback.feedbackText ?: "",
                fontSize = 14.sp,
                color = Color(0xFF1F2937)
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = feedback.username ?: "Anonymous",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF374151)
                )

                feedback.email?.takeIf { it.isNotBlank() }?.let { email ->
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showEmailMenu = true }
                        ) {
                            Icon(Icons.Default.Email, contentDescription = "Email", tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(email, fontSize = 12.sp, color = Color(0xFF2563EB))
                        }
                        DropdownMenu(expanded = showEmailMenu, onDismissRequest = { showEmailMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Copy email") },
                                onClick = {
                                    clipboard.setText(AnnotatedString(email))
                                    showEmailMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send email") },
                                onClick = {
                                    showEmailMenu = false
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            feedback.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3F4F6)
                ) {
                    Text(
                        text = "Notes: $notes",
                        fontSize = 12.sp,
                        color = Color(0xFF4B5563),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String?) {
    val color = when (status) {
        "pending" -> Color(0xFFFBBF24)
        "reviewed" -> Color(0xFF3B82F6)
        "resolved" -> Color(0xFF10B981)
        "archived" -> Color(0xFF6B7280)
        else -> Color(0xFF9CA3AF)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Text(
            text = statusLabel(status).uppercase(),
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UpdateFeedbackDialog(
    feedback: FeedbackAdminDto,
    actionInProgress: Boolean,
    onConfirm: (status: String, notes: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(feedback.status ?: "pending") }
    var notes by remember { mutableStateOf(feedback.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update feedback") },
        text = {
            Column {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("pending", "reviewed", "resolved", "archived").forEach { status ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(selected = selectedStatus == status, onClick = { selectedStatus = status })
                            Spacer(Modifier.width(8.dp))
                            Text(statusLabel(status), fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStatus, notes.takeIf { it.isNotBlank() }) },
                enabled = !actionInProgress
            ) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !actionInProgress) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Relative time similar to ReportCard.formatTimeAgo; local copy to keep files independent. */
private fun formatTimeAgo(timestamp: String?): String {
    if (timestamp == null) return "Recently"
    return try {
        val diff = eg.deals.radar.util.ServerTime.millisSince(timestamp) ?: return "Recently"
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    } catch (e: Exception) {
        "Recently"
    }
}
