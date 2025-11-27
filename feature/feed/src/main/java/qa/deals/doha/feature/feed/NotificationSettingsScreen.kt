package qa.deals.doha.feature.feed

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import qa.deals.domain.DealCategory
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.doha.manager.NotificationManager

/**
 * ========================================
 * ✨ NOTIFICATION SETTINGS SCREEN
 * Smart push notification management
 * ========================================
 *
 * Created: 2025-11-25 by @Magdyz
 * Location: feature/feed/src/main/java/qa/deals/doha/feature/feed/NotificationSettingsScreen.kt
 *
 * FEATURES:
 * - Authentication gating (login required)
 * - Global "All Deals" master switch
 * - Category-specific notification toggles
 * - FCM topic subscription management
 * - Purple-pink gradient theme (matches FeedbackScreen)
 * - Animated bell icon
 * - "Coming Soon" section for keyword alerts
 *
 * AUTHENTICATION:
 * - If user is anonymous → Show login required screen
 * - If user is authenticated → Show full settings UI
 *
 * FCM TOPICS:
 * - "all_deals" - Global notifications
 * - "cat_{categoryId}" - Category-specific (e.g., "cat_food_dining")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceIdManager = remember { DeviceIdManager.getInstance(context) }
    val notificationManager = remember { NotificationManager.getInstance(context) }

    // Check authentication status
    val isAuthenticated by deviceIdManager.userIdFlow.collectAsState()

    // Purple gradient matching app theme
    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF9C27B0),  // Purple
                Color(0xFFE91E63)   // Pink
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradientBrush)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Manage Alerts",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White
                        )
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
                    actions = {
                        // Animated bell icon
                        AnimatedBellIcon()
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        // Show login required or settings based on authentication
        if (isAuthenticated == null) {
            // User is not authenticated - show login required screen
            LoginRequiredContent(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onLoginClick = onLoginClick
            )
        } else {
            // User is authenticated - show full settings
            NotificationSettingsContent(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                notificationManager = notificationManager,
                context = context
            )
        }
    }
}

/**
 * ========================================
 * 🔒 LOGIN REQUIRED SCREEN
 * ========================================
 */
@Composable
private fun LoginRequiredContent(
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit
) {
    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF9C27B0),  // Purple
                Color(0xFFE91E63)   // Pink
            )
        )
    }

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lock icon with gradient background
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = gradientBrush,
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Login,
                contentDescription = "Login required",
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = "Login Required",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = "To manage your notification preferences, you'll need to create an account or log in.\n\nStay updated on the best deals in Doha!",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp
            ),
            color = Color(0xFF6B7280),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Login button with gradient
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp
            ),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = gradientBrush,
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log In / Sign Up",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

/**
 * ========================================
 * 🔔 NOTIFICATION SETTINGS CONTENT
 * ========================================
 */
@Composable
private fun NotificationSettingsContent(
    modifier: Modifier = Modifier,
    notificationManager: NotificationManager,
    context: Context
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // ========================================
    // ✅ PERMISSION HANDLING (Android 13+)
    // ========================================

    var showPermissionDialog by remember { mutableStateOf(false) }

    // Check if permission is granted
    val hasNotificationPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not needed on Android 12 and below
        }
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showPermissionDialog = true
        }
    }

    // Request permission if needed (Android 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // State for all deals toggle
    val allDealsEnabled by notificationManager.allDealsEnabledFlow.collectAsState()

    // ✅ FIX: Confirmation dialog state
    var showAllDealsConfirmDialog by remember { mutableStateOf(false) }

    // State for category toggles (✅ FIX: Using StateFlow for immediate updates)
    val categoryStates = remember {
        DealCategory.values().associateWith { category ->
            mutableStateOf(notificationManager.isCategoryEnabled(category))
        }
    }

    // Update category states when they change
    LaunchedEffect(Unit) {
        DealCategory.values().forEach { category ->
            notificationManager.getCategoryFlow(category).collect { enabled ->
                categoryStates[category]?.value = enabled
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ========================================
        // SECTION 1: GLOBAL "ALL DEALS" TOGGLE
        // ========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF3E5F5) // Light purple background
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔔 All New Deals",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color(0xFF7B1FA2)
                )

                Text(
                    text = "Get notified about every new deal posted in Doha. Stay ahead of the best offers!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    color = Color(0xFF4A148C)
                )

                HorizontalDivider(
                    color = Color(0xFF9C27B0).copy(alpha = 0.2f),
                    thickness = 1.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable All Deals",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        ),
                        color = Color(0xFF4A148C)
                    )

                    Switch(
                        checked = allDealsEnabled,
                        onCheckedChange = { enabled ->
                            // ✅ FIX: Show confirmation when turning OFF
                            if (!enabled && allDealsEnabled) {
                                showAllDealsConfirmDialog = true
                            } else {
                                // Turning ON - no confirmation needed
                                scope.launch {
                                    try {
                                        notificationManager.setAllDealsEnabled(enabled)
                                    } catch (e: Exception) {
                                        Log.e("NotificationSettings", "Error updating preference", e)
                                    }
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF9C27B0),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE0E0E0)
                        )
                    )
                }
            }
        }

        // ========================================
        // SECTION 2: CATEGORY-SPECIFIC TOGGLES
        // ========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF3E5F5) // Light purple background
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📂 Categories",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color(0xFF7B1FA2)
                )

                Text(
                    text = "Choose specific categories you want to receive notifications for.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    color = Color(0xFF4A148C)
                )

                HorizontalDivider(
                    color = Color(0xFF9C27B0).copy(alpha = 0.2f),
                    thickness = 1.dp
                )

                // Category switches
                DealCategory.values().forEach { category ->
                    val enabled by categoryStates[category]!!

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.emoji,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    ),
                                    color = Color(0xFF4A148C)
                                )
                            }

                            Switch(
                                checked = enabled,
                                onCheckedChange = { newEnabled ->
                                    // ✅ FIX: Update state IMMEDIATELY for instant feedback (no lag)
                                    categoryStates[category]?.value = newEnabled

                                    // Then update backend asynchronously
                                    scope.launch {
                                        try {
                                            notificationManager.setCategoryEnabled(category, newEnabled)
                                        } catch (e: Exception) {
                                            // Revert on error
                                            categoryStates[category]?.value = !newEnabled
                                            Log.e("NotificationSettings", "Error updating category", e)
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF9C27B0),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFE0E0E0)
                                )
                            )
                        }

                        // Add divider between categories (except last one)
                        if (category != DealCategory.values().last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = Color(0xFF9C27B0).copy(alpha = 0.1f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(16.dp))
    }

    // ========================================
    // ✅ CONFIRMATION DIALOG FOR "ALL DEALS" TOGGLE OFF
    // ========================================
    if (showAllDealsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAllDealsConfirmDialog = false },
            title = {
                Text(
                    text = "⚠️ Turn Off All Notifications?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = "You will no longer receive notifications for new deals. You can always turn them back on later.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // ✅ FIX: Close dialog IMMEDIATELY (no lag)
                        showAllDealsConfirmDialog = false

                        // Then do async work in background
                        scope.launch {
                            try {
                                notificationManager.setAllDealsEnabled(false)
                            } catch (e: Exception) {
                                Log.e("NotificationSettings", "Error disabling notifications", e)
                                // Note: Switch will revert automatically via Flow
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91E63)
                    )
                ) {
                    Text("Turn Off", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAllDealsConfirmDialog = false }
                ) {
                    Text("Cancel", color = Color(0xFF9C27B0))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

/**
 * ========================================
 * 🔔 ANIMATED BELL ICON
 * ========================================
 */
@Composable
private fun AnimatedBellIcon() {
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "bell")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Icon(
        imageVector = Icons.Default.Notifications,
        contentDescription = "Notifications",
        modifier = Modifier
            .padding(end = 12.dp)
            .size(28.dp)
            .scale(scale),
        tint = Color.White
    )
}
