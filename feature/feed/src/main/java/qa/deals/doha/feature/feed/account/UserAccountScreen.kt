package qa.deals.doha.feature.feed.account



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

import qa.deals.doha.db.DealEntity



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



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("My Account") },

                navigationIcon = {

                    IconButton(onClick = onBackClick) {

                        Icon(

                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,

                            contentDescription = "Back"

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

                            contentDescription = "Logout",

                            tint = MaterialTheme.colorScheme.error

                        )

                    }

                },

                colors = TopAppBarDefaults.topAppBarColors(

                    containerColor = MaterialTheme.colorScheme.primaryContainer,

                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer

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

                                    Text("Dismiss")

                                }

                            }

                        }

                    }

                }



                // User Profile Section

                item {

                    UserProfileCard(user = uiState.user)

                }



                // Statistics Section

                item {

                    StatisticsCard(stats = uiState.stats)

                }



                // My Deals Section Header

                item {

                    Text(

                        text = "My Deals (${userDeals.size})",

                        fontSize = 20.sp,

                        fontWeight = FontWeight.Bold,

                        color = Color(0xFF1F2937)

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

                                    text = "No deals submitted yet",

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

                                    Text("Load More")

                                }

                            }

                        }

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

    user: qa.deals.doha.network.UserDto?,

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

                    contentDescription = "User Avatar",

                    modifier = Modifier.size(60.dp),

                    tint = MaterialTheme.colorScheme.onPrimaryContainer

                )

            }



            // Username

            Text(

                text = user?.username ?: "Anonymous",

                fontSize = 24.sp,

                fontWeight = FontWeight.Bold,

                color = Color(0xFF1F2937)

            )



            // Email

            Text(

                text = user?.email ?: "No email",

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

                    text = user?.role?.uppercase() ?: "USER",

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

    stats: UserStats,

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

                text = "Statistics",

                fontSize = 18.sp,

                fontWeight = FontWeight.Bold,

                color = Color(0xFF1F2937)

            )



            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.SpaceEvenly

            ) {

                StatItem(

                    label = "Total",

                    value = stats.totalDeals,

                    color = Color(0xFF6B7280)

                )

                StatItem(

                    label = "Approved",

                    value = stats.approvedDeals,

                    color = Color(0xFF059669)

                )

                StatItem(

                    label = "Pending",

                    value = stats.pendingDeals,

                    color = Color(0xFFF59E0B)

                )

                StatItem(

                    label = "Rejected",

                    value = stats.rejectedDeals,

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

    value: Int,

    color: Color,

    modifier: Modifier = Modifier

) {

    Column(

        modifier = modifier,

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.spacedBy(4.dp)

    ) {

        Text(

            text = value.toString(),

            fontSize = 28.sp,

            fontWeight = FontWeight.Bold,

            color = color

        )

        Text(

            text = label,

            fontSize = 12.sp,

            color = Color(0xFF6B7280)

        )

    }

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
                    text = deal.location ?: "No location",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
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
                    text = deal.status?.uppercase() ?: "UNKNOWN",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

    }

}

