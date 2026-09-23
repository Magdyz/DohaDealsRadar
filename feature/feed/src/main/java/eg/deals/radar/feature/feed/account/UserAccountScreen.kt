package eg.deals.radar.feature.feed.account



import eg.deals.core.design.components.PrivacyPolicyDialog
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import eg.deals.radar.feature.feed.R
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.AccountCircle

import androidx.compose.material.icons.filled.ExitToApp

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.lifecycle.ViewModelProvider

import androidx.lifecycle.viewmodel.compose.viewModel

import com.google.accompanist.swiperefresh.SwipeRefresh

import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

import eg.deals.radar.db.DealEntity

/**
 * User Account Screen
 * Displays user profile, statistics, and their submitted deals
 *
 * @param onBackClick Navigate back
 * @param onLogout User logged out, navigate to feed
 * @param onDealClick Navigate to deal details
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAccountScreen(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    onDealClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: UserAccountViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return UserAccountViewModel(context) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val userDeals by viewModel.userDeals.collectAsState()

    val swipeRefreshState = rememberSwipeRefreshState(
        isRefreshing = uiState.loading
    )

    // Mark that user has seen account screen (for moderator first-time experience)
    LaunchedEffect(Unit) {
        eg.deals.radar.datastore.DeviceIdManager.getInstance(context).setHasSeenAccountScreen()
    }
    // ---- privacy actions state ----
    var showPrivacy by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var privacyBusy by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        privacyBusy = true
        viewModel.exportMyData { result ->
            privacyBusy = false
            result.onSuccess { json ->
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                    .onSuccess { Toast.makeText(context, context.getString(R.string.account_export_done), Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(context, context.getString(R.string.account_export_failed), Toast.LENGTH_LONG).show() }
            }.onFailure { Toast.makeText(context, it.message ?: context.getString(R.string.account_export_failed), Toast.LENGTH_LONG).show() }
        }
    }
    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!privacyBusy) showDeleteConfirm = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        privacyBusy = true
                        viewModel.deleteAccount { ok, message ->
                            privacyBusy = false
                            showDeleteConfirm = false
                            if (ok) {
                                Toast.makeText(context, context.getString(R.string.account_delete_done), Toast.LENGTH_LONG).show()
                                onLogout()
                            } else {
                                Toast.makeText(context, message ?: context.getString(R.string.account_export_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !privacyBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text(stringResource(R.string.account_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !privacyBusy) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_my_account)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    // Logout button
                    IconButton(
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = stringResource(R.string.account_logout),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF9C27B0),  // App purple (matches selected category)
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refresh() },
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error message
                if (uiState.error != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.error ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearError() }) {
                                    Text(stringResource(R.string.common_dismiss))
                                }
                            }
                        }
                    }
                }

                // User Profile Section
                item {
                    if (uiState.loading && uiState.user == null) {

                        // Show loading indicator while fetching user data
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFFFFF)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(50.dp),
                                    color = Color(0xFF9C27B0),  // Purple like feed
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = stringResource(R.string.account_loading),
                                    fontSize = 14.sp,
                                    color = Color(0xFF6B7280),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        UserProfileCard(user = uiState.user)
                    }
                }

                // Statistics Section

                item {
                    StatisticsCard(stats = uiState.stats)
                }



                // My Deals Section Header

                item {

                    Text(

                        text = stringResource(R.string.account_my_deals, userDeals.size),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // User's Deals List

                if (userDeals.isEmpty() && !uiState.loading) {

                    item {

                        Card(

                            modifier = Modifier.fillMaxWidth(),

                            colors = CardDefaults.cardColors(

                                containerColor = Color(0xFFF3F4F6)

                            )

                        ) {

                            Box(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .padding(32.dp),

                                contentAlignment = Alignment.Center

                            ) {

                                Text(

                                    text = stringResource(R.string.account_no_deals),

                                    color = Color(0xFF6B7280),

                                    fontSize = 14.sp

                                )

                            }

                        }

                    }

                } else {

                    items(userDeals) { deal ->

                        DealCard(

                            deal = deal,

                            onClick = { onDealClick(deal.id) }

                        )

                    }



                    // Load more button

                    if (uiState.hasMorePages) {

                        item {

                            Button(

                                onClick = { viewModel.loadMoreDeals() },

                                modifier = Modifier.fillMaxWidth(),

                                enabled = !uiState.isLoadingMore

                            ) {

                                if (uiState.isLoadingMore) {

                                    CircularProgressIndicator(

                                        modifier = Modifier.size(20.dp),

                                        color = MaterialTheme.colorScheme.onPrimary

                                    )

                                } else {

                                    Text(stringResource(R.string.common_load_more))

                                }

                            }

                        }

                    }

                }

                // 🔐 Privacy & your data (download / delete / policy)
                if (uiState.user != null) {
                    item {
                        PrivacyDataCard(
                            onExport = { exportLauncher.launch("egyptdealradar-my-data.json") },
                            onDelete = { showDeleteConfirm = true },
                            onPrivacy = { showPrivacy = true },
                            busy = privacyBusy
                        )
                    }
                }

            }

        }

    }

}



/**

 * User Profile Card

 */

@Composable

private fun UserProfileCard(

    user: eg.deals.radar.network.UserDto?,

    modifier: Modifier = Modifier

) {

    Card(

        modifier = modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(

            containerColor = Color(0xFFFFFFFF)

        ),

        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)

    ) {

        Column(

            modifier = Modifier

                .fillMaxWidth()

                .padding(20.dp),

            horizontalAlignment = Alignment.CenterHorizontally,

            verticalArrangement = Arrangement.spacedBy(12.dp)

        ) {

            // Avatar

            Box(

                modifier = Modifier

                    .size(80.dp)

                    .clip(CircleShape)

                    .background(MaterialTheme.colorScheme.primaryContainer),

                contentAlignment = Alignment.Center

            ) {

                Icon(

                    imageVector = Icons.Default.AccountCircle,

                    contentDescription = stringResource(R.string.account_avatar),

                    modifier = Modifier.size(60.dp),

                    tint = MaterialTheme.colorScheme.onPrimaryContainer

                )

            }



            // Username

            Text(

                text = user?.username ?: stringResource(R.string.common_anonymous),

                fontSize = 24.sp,

                fontWeight = FontWeight.Bold,

                color = Color(0xFF1F2937)

            )



            // Email

            Text(

                text = user?.email ?: stringResource(R.string.account_no_email),

                fontSize = 14.sp,

                color = Color(0xFF6B7280)

            )



            // Role Badge

            Surface(

                color = when (user?.role) {

                    "admin" -> Color(0xFFDC2626)

                    "moderator" -> Color(0xFF2563EB)

                    else -> Color(0xFF059669)

                },

                shape = RoundedCornerShape(16.dp)

            ) {

                Text(

                    text = roleLabel(user?.role),

                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),

                    color = Color.White,
                    fontSize = 12.sp,

                    fontWeight = FontWeight.Bold

                )

            }

        }

    }

}



/**

 * Statistics Card

 */

@Composable

private fun StatisticsCard(

    stats: UserStats?,

    modifier: Modifier = Modifier

) {

    Card(

        modifier = modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(

            containerColor = Color(0xFFFFFFFF)

        ),

        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)

    ) {

        Column(

            modifier = Modifier

                .fillMaxWidth()

                .padding(20.dp),

            verticalArrangement = Arrangement.spacedBy(12.dp)

        ) {

            Text(

                text = stringResource(R.string.account_statistics),

                fontSize = 18.sp,

                fontWeight = FontWeight.Bold,

                color = Color(0xFF1F2937)

            )



            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceEvenly

            ) {

                StatItem(

                    label = stringResource(R.string.account_stat_total),

                    value = stats?.totalDeals,

                    color = Color(0xFF6B7280)

                )

                StatItem(

                    label = stringResource(R.string.status_approved),

                    value = stats?.approvedDeals,

                    color = Color(0xFF059669)

                )

                StatItem(

                    label = stringResource(R.string.status_pending),

                    value = stats?.pendingDeals,

                    color = Color(0xFFF59E0B)

                )

                StatItem(

                    label = stringResource(R.string.status_rejected),

                    value = stats?.rejectedDeals,

                    color = Color(0xFFDC2626)

                )

            }

        }

    }

}



/**

 * Individual Stat Item

 */

@Composable

private fun StatItem(

    label: String,

    value: Int?,

    color: Color,

    modifier: Modifier = Modifier

) {

    Column(

        modifier = modifier,

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.spacedBy(4.dp)

    ) {

        if (value == null) {
            StatValueSkeleton()
        } else {
            Text(

                text = value.toString(),

                fontSize = 28.sp,

                fontWeight = FontWeight.Bold,

                color = color

            )
        }

        Text(

            text = label,

            fontSize = 12.sp,

            color = Color(0xFF6B7280)

        )

    }

}



/**

 * Pulsing placeholder for a stat number while stats are still loading.
 * Mirrors the shimmer approach used by [eg.deals.radar.feature.feed.components.SkeletonDealCard].

 */

@Composable

private fun StatValueSkeleton() {

    val infiniteTransition = rememberInfiniteTransition(label = "stat_shimmer")

    val shimmerAlpha by infiniteTransition.animateFloat(

        initialValue = 0.3f,

        targetValue = 0.6f,

        animationSpec = infiniteRepeatable(

            animation = tween(1000, easing = LinearEasing),

            repeatMode = RepeatMode.Reverse

        ),

        label = "stat_shimmer_alpha"

    )

    val baseColor = Color(0xFFE0E0E0)

    Box(

        modifier = Modifier

            .height(24.dp)

            .width(36.dp)

            .clip(RoundedCornerShape(6.dp))

            .background(baseColor.copy(alpha = shimmerAlpha))

    )

}



/**

 * Deal Card for User's Submitted Deals

 */

@OptIn(ExperimentalMaterial3Api::class)

@Composable

private fun DealCard(

    deal: DealEntity,

    onClick: () -> Unit,

    modifier: Modifier = Modifier

) {

    Card(

        onClick = onClick,

        modifier = modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(

            containerColor = Color(0xFFFFFFFF)

        ),

        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)

    ) {

        Row(

            modifier = Modifier

                .fillMaxWidth()

                .padding(16.dp),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            Column(

                modifier = Modifier.weight(1f),

                verticalArrangement = Arrangement.spacedBy(4.dp)

            ) {
                Text(
                    text = deal.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937),
                    maxLines = 2
                )
                Text(
                    text = deal.location?.takeIf { it.isNotBlank() } ?: deal.store ?: if (deal.link.isNullOrBlank()) stringResource(R.string.account_no_location) else stringResource(R.string.account_online),
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
                // ✅ NEW: Show rejection reason if deal is rejected
                if (deal.status == "rejected" && !deal.rejectionReason.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.account_rejection_reason, deal.rejectionReason ?: ""),
                        fontSize = 13.sp,
                        color = Color(0xFFDC2626),  // Red color to match rejected badge
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }



            // Status Badge

            Surface(

                color = when (deal.status) {

                    "approved" -> Color(0xFF059669)

                    "pending" -> Color(0xFFF59E0B)

                    "rejected" -> Color(0xFFDC2626)

                    else -> Color(0xFF6B7280)

                },

                shape = RoundedCornerShape(12.dp)

            ) {
                Text(
                    text = statusLabel(deal.status),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

    }

}

/** Localized role name shown on the account header. */
@Composable
private fun roleLabel(role: String?): String = when (role) {
    "admin" -> stringResource(R.string.role_admin)
    "moderator" -> stringResource(R.string.role_moderator)
    else -> stringResource(R.string.role_user)
}

/** Localized deal status badge (status values from the backend stay English). */
@Composable
private fun statusLabel(status: String?): String = when (status) {
    "approved" -> stringResource(R.string.status_approved)
    "pending" -> stringResource(R.string.status_pending)
    "rejected" -> stringResource(R.string.status_rejected)
    else -> stringResource(R.string.status_unknown)
}

/** Privacy & your data: download, delete, read the policy. */
@Composable
private fun PrivacyDataCard(onExport: () -> Unit, onDelete: () -> Unit, onPrivacy: () -> Unit, busy: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.account_privacy_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                stringResource(R.string.account_privacy_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onExport, enabled = !busy) { Text(stringResource(R.string.account_export)) }
            TextButton(onClick = onPrivacy) { Text(stringResource(R.string.account_privacy_policy)) }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(stringResource(R.string.account_delete), color = Color(0xFFDC2626))
            }
        }
    }
}
