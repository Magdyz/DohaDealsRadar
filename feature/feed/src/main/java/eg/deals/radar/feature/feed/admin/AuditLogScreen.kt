package eg.deals.radar.feature.feed.admin

import eg.deals.radar.feature.feed.R

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import eg.deals.radar.network.AdminAuditLogEntryDto

/**
 * ========================================
 * 📜 AUDIT LOG (admin only, English-only)
 * ========================================
 * Backed by admin_audit_log. Read-only moderation history.
 *
 * CREATED: 2025-11-27
 */
private val CATEGORIES = listOf(null, "security", "deals", "reports")
private fun categoryLabel(category: String?): String = when (category) {
    null -> "All"
    "security" -> "Security"
    "deals" -> "Deals"
    "reports" -> "Reports"
    else -> category.replaceFirstChar { it.uppercase() }
}

/** action_type -> a human-readable label. Unknown types are shown raw. */
private val SECURITY_ACTION_TYPES = setOf(
    "user_role_changed", "user_banned", "user_unbanned", "auto_approve_changed",
    "strikes_reset", "account_deleted", "report_ban_user"
)

private fun actionLabel(actionType: String?): String = when (actionType) {
    "user_role_changed" -> "Changed role"
    "user_banned" -> "Banned user"
    "user_unbanned" -> "Unbanned user"
    "auto_approve_changed" -> "Changed auto-approve"
    "strikes_reset" -> "Reset strikes"
    "account_deleted" -> "Account deleted"
    "deal_approved" -> "Approved deal"
    "deal_restored" -> "Restored deal"
    "deal_rejected" -> "Rejected deal"
    "deal_deleted" -> "Deleted deal"
    "deal_permanently_deleted" -> "Permanently deleted deal"
    "deal_returned_to_feed" -> "Returned deal to feed"
    "deal_marked_ended" -> "Deal marked ended (archived)"
    "report_dismissed" -> "Dismissed report"
    "report_delete_deal" -> "Report action: deleted deal"
    "report_warn_user" -> "Report action: warned user"
    "report_ban_user" -> "Report action: banned user"
    null -> "Unknown action"
    else -> actionType
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AuditLogViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AuditLogViewModel() as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val items by viewModel.items.collectAsState()
    val listState = rememberLazyListState()

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
                        Text(
                            "📜 Audit Log",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = uiState.category == category,
                            onClick = { viewModel.setCategory(category) },
                            label = { Text(categoryLabel(category), fontSize = 13.sp) },
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error loading audit log", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
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
                            Text("📜", fontSize = 48.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("No activity yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items = items, key = { it.id }) { entry ->
                            AuditLogRow(entry = entry)
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
        }
    }
}

@Composable
private fun AuditLogRow(entry: AdminAuditLogEntryDto, modifier: Modifier = Modifier) {
    val isSecurity = entry.actionType in SECURITY_ACTION_TYPES

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            if (isSecurity) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = "Security event",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        actionLabel(entry.actionType),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSecurity) MaterialTheme.colorScheme.error else Color(0xFF1F2937)
                    )
                    Text(formatTimeAgoAudit(entry.createdAt), fontSize = 11.sp, color = Color(0xFF9CA3AF))
                }

                Spacer(Modifier.height(4.dp))

                entry.actorUsername?.let {
                    Text("By $it", fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                entry.targetUsername?.let {
                    Text("Target: $it", fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                entry.dealTitle?.let {
                    Text("Deal: $it", fontSize = 12.sp, color = Color(0xFF6B7280))
                }
                if (entry.oldValue != null || entry.newValue != null) {
                    Text(
                        "${entry.oldValue ?: "-"} → ${entry.newValue ?: "-"}",
                        fontSize = 12.sp,
                        color = Color(0xFF374151),
                        fontWeight = FontWeight.Medium
                    )
                }
                entry.reason?.takeIf { it.isNotBlank() }?.let {
                    Text("Reason: $it", fontSize = 12.sp, color = Color(0xFF6B7280))
                }
            }
        }
    }
}

private fun formatTimeAgoAudit(timestamp: String?): String {
    if (timestamp == null) return "Recently"
    return try {
        val diff = eg.deals.radar.util.ServerTime.millisSince(timestamp) ?: return "Recently"
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 2_592_000_000 -> "${diff / 86_400_000}d ago"
            else -> "${diff / 2_592_000_000}mo ago"
        }
    } catch (e: Exception) {
        "Recently"
    }
}
