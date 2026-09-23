package eg.deals.radar.feature.feed.admin

import eg.deals.radar.feature.feed.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import eg.deals.radar.network.AdminUserDto

/**
 * ========================================
 * 👤 USER MANAGEMENT (admin only, English-only)
 * ========================================
 * Backed by admin_users + update_user_role. Search is debounced in the
 * ViewModel; filter chips refresh immediately.
 *
 * CREATED: 2025-11-27
 */
private val FILTERS = listOf(null, "moderators", "admins", "banned", "trusted")
private fun filterLabel(filter: String?): String = when (filter) {
    null -> "All"
    "moderators" -> "Moderators"
    "admins" -> "Admins"
    "banned" -> "Banned"
    "trusted" -> "Trusted"
    else -> filter.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onBackClick: () -> Unit,
    onViewUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: UserManagementViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return UserManagementViewModel() as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val items by viewModel.items.collectAsState()
    val listState = rememberLazyListState()

    val currentUserId = remember {
        eg.deals.radar.datastore.DeviceIdManager.getInstance(context).getUserId()
    }

    var selectedUser by remember { mutableStateOf<AdminUserDto?>(null) }

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
                            "👤 User Management",
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

                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = { Text("Search username or email", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FILTERS.forEach { filter ->
                        FilterChip(
                            selected = uiState.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filterLabel(filter), fontSize = 13.sp) },
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
                            Text("Error loading users", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
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
                            Text("🔍", fontSize = 48.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("No users found", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items = items, key = { it.id }) { user ->
                            UserRow(
                                user = user,
                                isSelf = user.id == currentUserId,
                                onClick = { selectedUser = user }
                            )
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

    selectedUser?.let { user ->
        // Keep the sheet's user data fresh as the list updates (role/ban changes)
        val liveUser = items.find { it.id == user.id } ?: user
        UserActionsSheet(
            user = liveUser,
            isSelf = liveUser.id == currentUserId,
            actionInProgress = uiState.actionInProgress,
            onDismiss = { selectedUser = null },
            onViewDeals = {
                selectedUser = null
                onViewUserClick(liveUser.id)
            },
            onChangeRole = { role -> viewModel.changeRole(liveUser.id, role) },
            onBan = { reason -> viewModel.banUser(liveUser.id, reason) },
            onUnban = { viewModel.unbanUser(liveUser.id, null) },
            onSetAutoApprove = { value -> viewModel.setAutoApprove(liveUser.id, value) },
            onResetStrikes = { viewModel.resetStrikes(liveUser.id) }
        )
    }
}

@Composable
private fun UserRow(
    user: AdminUserDto,
    isSelf: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (user.username ?: "Unknown") + if (isSelf) " (you)" else "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    modifier = Modifier.weight(1f)
                )
                RoleBadge(role = user.role)
            }

            user.email?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = Color(0xFF6B7280))
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.bannedAt != null) SmallBadge("BANNED", Color(0xFFEF4444))
                if (user.autoApprove) SmallBadge("TRUSTED", Color(0xFF10B981))
                if (user.strikes > 0) SmallBadge("${user.strikes} strikes", Color(0xFFF97316))
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "✅ ${user.approvedDealsCount}  ❌ ${user.rejectedDealsCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
                Text(
                    "Joined ${formatDate(user.createdAt)}",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val color = when (role) {
        "admin" -> Color(0xFF9C27B0)
        "moderator" -> Color(0xFF3B82F6)
        else -> Color(0xFF6B7280)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Text(
            text = role.uppercase(),
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SmallBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserActionsSheet(
    user: AdminUserDto,
    isSelf: Boolean,
    actionInProgress: Boolean,
    onDismiss: () -> Unit,
    onViewDeals: () -> Unit,
    onChangeRole: (String) -> Unit,
    onBan: (String?) -> Unit,
    onUnban: () -> Unit,
    onSetAutoApprove: (Boolean) -> Unit,
    onResetStrikes: () -> Unit
) {
    var showRoleConfirm by remember { mutableStateOf<String?>(null) }
    var showBanDialog by remember { mutableStateOf(false) }
    var showUnbanConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text(user.username ?: "Unknown", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            user.email?.let { Text(it, fontSize = 13.sp, color = Color(0xFF6B7280)) }
            if (isSelf) {
                Spacer(Modifier.height(4.dp))
                Text("This is your own account", fontSize = 12.sp, color = Color(0xFF9CA3AF))
            }

            Spacer(Modifier.height(16.dp))
            Text("Role", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("user", "moderator", "admin").forEach { role ->
                    FilterChip(
                        selected = user.role == role,
                        onClick = { if (user.role != role) showRoleConfirm = role },
                        label = { Text(role.replaceFirstChar { it.uppercase() }) },
                        enabled = !actionInProgress
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-approve (trusted)", fontSize = 14.sp)
                Switch(
                    checked = user.autoApprove,
                    onCheckedChange = { onSetAutoApprove(it) },
                    enabled = !actionInProgress
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Strikes: ${user.strikes}", fontSize = 14.sp)
                TextButton(onClick = { showResetConfirm = true }, enabled = !actionInProgress && user.strikes > 0) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onViewDeals,
                modifier = Modifier.fillMaxWidth()
            ) { Text("View deals") }

            Spacer(Modifier.height(8.dp))

            if (user.bannedAt != null) {
                Button(
                    onClick = { showUnbanConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) { Text("Unban user") }
            } else {
                Button(
                    onClick = { showBanDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actionInProgress && !isSelf && user.role != "admin",
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Ban user") }
            }

            if (!isSelf && user.role == "admin" && user.bannedAt == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Remove the admin role before banning this account.",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }

    showRoleConfirm?.let { role ->
        AlertDialog(
            onDismissRequest = { showRoleConfirm = null },
            title = { Text("Change role?") },
            text = { Text("Change ${user.username ?: "this user"}'s role from ${user.role} to $role?") },
            confirmButton = {
                Button(onClick = {
                    onChangeRole(role)
                    showRoleConfirm = null
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRoleConfirm = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showBanDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBanDialog = false },
            title = { Text("Ban ${user.username ?: "this user"}?") },
            text = {
                Column {
                    Text("They won't be able to sign in or post deals.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBan(reason.takeIf { it.isNotBlank() })
                        showBanDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Ban") }
            },
            dismissButton = {
                TextButton(onClick = { showBanDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showUnbanConfirm) {
        AlertDialog(
            onDismissRequest = { showUnbanConfirm = false },
            title = { Text("Unban ${user.username ?: "this user"}?") },
            confirmButton = {
                Button(onClick = {
                    onUnban()
                    showUnbanConfirm = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showUnbanConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset strikes?") },
            text = { Text("This resets ${user.username ?: "this user"}'s strike count to 0.") },
            confirmButton = {
                Button(onClick = {
                    onResetStrikes()
                    showResetConfirm = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

private fun formatDate(timestamp: String?): String {
    if (timestamp.isNullOrBlank()) return "-"
    return try {
        timestamp.take(10) // YYYY-MM-DD prefix of ISO timestamp
    } catch (e: Exception) {
        "-"
    }
}
