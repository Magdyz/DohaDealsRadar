package eg.deals.radar.feature.feed.profile

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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
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
import eg.deals.radar.feature.feed.components.DealCard
import eg.deals.radar.feature.feed.components.RoleBadge

/**
 * User Profile Screen
 * Shows user info, role, stats, and their submitted deals
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onBackClick: () -> Unit,
    onDealClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: UserProfileViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return UserProfileViewModel(context) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val userDeals by viewModel.userDeals.collectAsState()
    val userStats by viewModel.userStats.collectAsState()
    val roleDisplayName by viewModel.roleDisplayName.collectAsState()

    // Load user profile
    LaunchedEffect(userId) {
        viewModel.loadUserProfile(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (uiState.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.user != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFF9FAFB))
            ) {
                // User Info Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF9C27B0),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Username
                            Text(
                                text = uiState.user!!.username ?: stringResource(R.string.common_anonymous),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Role Badge
                            RoleBadge(
                                role = uiState.user!!.role,
                                autoApprove = uiState.user!!.autoApprove
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Email
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.user!!.email ?: stringResource(R.string.account_no_email),
                                    fontSize = 14.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }
                }

                // Stats Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                value = userStats.totalDeals,
                                label = "Total Deals",
                                color = Color(0xFF6B7280)
                            )
                            StatItem(
                                value = userStats.approvedDeals,
                                label = "Approved",
                                color = Color(0xFF10B981)
                            )
                            StatItem(
                                value = userStats.pendingDeals,
                                label = "Pending",
                                color = Color(0xFFEAB308)
                            )
                        }
                    }
                }

                // Submitted Deals Header
                item {
                    Text(
                        text = stringResource(R.string.profile_submitted_deals),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // User's Deals
                if (userDeals.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.profile_no_deals),
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                } else {
                    items(userDeals) { deal ->
                        DealCard(
                            deal = deal,
                            onClick = { onDealClick(deal.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: stringResource(R.string.profile_user_not_found),
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}

/**
 * Stat item component
 */
@Composable
private fun StatItem(
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF6B7280)
        )
    }
}
