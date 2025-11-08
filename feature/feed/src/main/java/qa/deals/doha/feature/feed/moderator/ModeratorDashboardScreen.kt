package qa.deals.doha.feature.feed.moderator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Moderator Dashboard - Main hub for moderation actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorDashboardScreen(
    onBackClick: () -> Unit,
    onPendingDealsClick: () -> Unit,
    onUserManagementClick: () -> Unit = {},
    onAuditLogClick: () -> Unit = {},
    onLogout: () -> Unit = {},
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

    // Get DeviceIdManager and set current user
    val deviceIdManager = remember {
        qa.deals.doha.datastore.DeviceIdManager.getInstance(context)
    }

    LaunchedEffect(Unit) {
        val userId = deviceIdManager.getUserId()
        if (userId != null) {
            viewModel.setCurrentUser(userId)
        } else {
            android.util.Log.w("ModeratorDashboard", "⚠️ No userId found in DeviceIdManager")
        }
    }


    Scaffold(
        topBar = {
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
                            Text("🛡️ Admin Dashboard", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("ADMIN", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    } },
                    actions = { // Logout button
                        IconButton(
                            onClick = {
                                deviceIdManager.clearUserId()
                                onLogout()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF9FAFB))
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Pending Deals Card
            DashboardCard(
                title = "Pending Deals",
                subtitle = "${pendingDeals.size} deals awaiting review",
                icon = Icons.Default.CheckCircle,
                iconColor = Color(0xFFEAB308),
                onClick = onPendingDealsClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // User Management (Admin only)
            if (uiState.isAdmin) {
                DashboardCard(
                    title = "User Management",
                    subtitle = "Manage user roles and permissions",
                    icon = Icons.Default.Person,
                    iconColor = Color(0xFF2563EB),
                    onClick = onUserManagementClick
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Audit Log (Admin only)
            if (uiState.isAdmin) {
                DashboardCard(
                    title = "Audit Log",
                    subtitle = "View moderation history",
                    icon = Icons.Default.List,
                    iconColor = Color(0xFF6B7280),
                    onClick = onAuditLogClick
                )
            }
        }
    }
}

/**
 * Dashboard card component
 */
@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconColor.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }

            // Arrow
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Open",
                tint = Color(0xFF9CA3AF)
            )
        }
    }
}
