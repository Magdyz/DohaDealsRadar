package eg.deals.radar.navigation

import eg.deals.core.design.theme.EnglishOnlyLayout
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import eg.deals.radar.feature.details.DetailsScreen
import eg.deals.radar.feature.feed.FeedScreen
import eg.deals.radar.feature.feed.FeedbackScreen
import eg.deals.radar.feature.feed.NotificationSettingsScreen
import eg.deals.radar.feature.post.PostScreen
import eg.deals.radar.feature.report.ReportScreen
import eg.deals.radar.feature.archive.ArchiveScreen
import eg.deals.onboarding.OnboardingScreen
// SPRINT 4: Import moderator and profile screens
import eg.deals.radar.feature.feed.moderator.ModeratorDashboardScreen
import eg.deals.radar.feature.feed.moderator.PendingDealsScreen
import eg.deals.radar.feature.feed.moderator.ReportsScreen  // ✅ NEW: Reports screen (2025-11-22)
import eg.deals.radar.feature.feed.moderator.AnalyticsDashboardScreen  // ✅ NEW: Analytics dashboard (2025-11-24)
import eg.deals.radar.feature.feed.admin.UserManagementScreen  // ✅ NEW: Admin user management (2025-11-27)
import eg.deals.radar.feature.feed.admin.AuditLogScreen  // ✅ NEW: Admin audit log (2025-11-27)
import eg.deals.radar.feature.feed.admin.FeedbackAdminScreen  // ✅ NEW: Admin feedback inbox (2025-11-27)
import eg.deals.radar.feature.feed.profile.UserProfileScreen
// SPRINT 5: Import authentication and account screens
import eg.deals.radar.feature.post.LoginScreen
import eg.deals.radar.feature.feed.account.UserAccountScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import eg.deals.radar.feature.feed.FeedViewModel
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.remember

/**
 * Main navigation host for the app.
 * Manages navigation between all screens.
 *
 * ✅ OPTIMIZED: Instant navigation, no animation delay
 * ✅ SPRINT 6: Added Archive screen navigation
 * ✅ SPRINT 4: Added Moderator dashboard, pending deals, and user profile screens
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Routes.FEED
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // ========================================
        // ✅ INSTANT NAVIGATION: No animation delay
        // ========================================
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        // ========================================
        // Onboarding Screen - First-time user introduction
        // ========================================
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    // Navigate to feed and remove onboarding from backstack
                    navController.navigate(Routes.FEED) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ========================================
        // Feed Screen - Home screen showing list of deals
        // ========================================
        composable(Routes.FEED) {
            val context = LocalContext.current
            val feedViewModel: FeedViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return FeedViewModel(context) as T
                    }
                }
            )
            val isAuthenticated by feedViewModel.isAuthenticated.collectAsState()
            val currentUserRole by feedViewModel.currentUserRole.collectAsState()

            // Access DeviceIdManager for first-time account screen check

            val deviceIdManager = remember { eg.deals.radar.datastore.DeviceIdManager.getInstance(context) }

            FeedScreen(
                onDealClick = { dealId ->
                    navController.navigate(Routes.details(dealId))
                },
                onPostClick = {
                    navController.navigate(Routes.POST)
                },
                // ✅ SPRINT 6: Navigate to archive screen
                onArchiveClick = {
                    navController.navigate(Routes.ARCHIVE)
                },
                // ✅ SPRINT 5: Navigate based on authentication and role
                onAccountClick = {
                    when {
                        !isAuthenticated -> {
                            // Not logged in → Login screen
                            navController.navigate(Routes.LOGIN)
                        }
                        currentUserRole == "admin" || currentUserRole == "moderator" -> {
                            // Moderator/Admin → Check if first time
                            if (!deviceIdManager.hasSeenAccountScreen()) {
                                // First time → Show account screen
                                navController.navigate(Routes.ACCOUNT)
                            } else {
                                // Subsequent times → Show dashboard
                                navController.navigate(Routes.MODERATOR_DASHBOARD)
                            }
                        }
                        else -> {

                            // Regular user → Account screen
                            navController.navigate(Routes.ACCOUNT)
                        }
                    }
                },
                // ✅ NEW: Navigate to feedback screen (2025-11-22)
                onFeedbackClick = {
                    navController.navigate(Routes.FEEDBACK)
                },
                // ✅ NEW: Navigate to notifications screen (2025-11-25)
                onNotificationsClick = {
                    navController.navigate(Routes.NOTIFICATIONS)
                }
            )
        }

        // ========================================
        // ✅ SPRINT 6: Archive Screen - Show archived deals
        // Shows deals older than 10 days
        // ========================================
        // Admin-only screen: English, left-to-right even when the app is in Arabic
        composable(Routes.ARCHIVE) {
            EnglishOnlyLayout {
                ArchiveScreen(
                    onBackClick = { navController.popBackStack() },
                    onDealClick = { dealId ->
                        navController.navigate(Routes.details(dealId))
                    }
                )
            }
        }

        // ========================================
        // ✅ NEW: Feedback Screen - User feedback submission
        // Allows users to submit feedback and suggestions
        // ========================================
        composable(Routes.FEEDBACK) {
            FeedbackScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ========================================
        // ✅ NEW: Notifications Settings Screen (2025-11-25)
        // Smart push notification preferences management
        // ========================================
        composable(Routes.NOTIFICATIONS) {
            NotificationSettingsScreen(
                onBackClick = { navController.popBackStack() },
                onLoginClick = {
                    // Navigate to login screen if user is not authenticated
                    navController.navigate(Routes.LOGIN)
                }
            )
        }

        // ========================================
        // ✅ SPRINT 5: LOGIN SCREEN
        // Email verification flow for user authentication
        // ========================================

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { userId, username, email, role ->
                    // Navigate based on role after login
                    when (role) {
                        "admin", "moderator" -> {
                            navController.navigate(Routes.MODERATOR_DASHBOARD) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                        else -> {
                            navController.navigate(Routes.ACCOUNT) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        // ========================================
        // ✅ SPRINT 5: ACCOUNT SCREEN
        // User account with profile, stats, and deals
        // ========================================

        composable(Routes.ACCOUNT) {
            UserAccountScreen(
                onBackClick = { navController.popBackStack() },
                onLogout = {
                    // Return to feed after logout
                    navController.navigate(Routes.FEED) {
                        popUpTo(Routes.FEED) { inclusive = true }
                    }
                },

                onDealClick = { dealId ->
                    navController.navigate(Routes.details(dealId))
                }
            )
        }



        // ========================================
        // Post Screen - Submit a new deal
        // ========================================
        composable(Routes.POST) {
            PostScreen(
                onBackClick = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() },
                onOpenDeal = { dealId ->
                    navController.navigate(Routes.details(dealId)) { popUpTo(Routes.POST) { inclusive = true } }
                }
            )
        }

        // ========================================
        // Details Screen - Full deal details
        // ========================================
        composable(
            route = Routes.DETAILS,
            arguments = listOf(
                navArgument("dealId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val dealId = backStackEntry.arguments?.getString("dealId") ?: ""

            // ✅ FIX: Use DeviceIdManager and UserRepository directly to avoid cache conflict
            // Creating FeedViewModel triggers refreshDeals() which replaces cache,
            // removing pending deals that moderators are trying to view
            val context = LocalContext.current
            val deviceIdManager = remember { eg.deals.radar.datastore.DeviceIdManager.getInstance(context) }
            val userRepo = remember { eg.deals.radar.repository.UserRepository() }

            // Get current user ID and role reactively
            val userId = deviceIdManager.getUserId()
            val userRole by (userId?.let { userRepo.getCachedUserRoleFlow(it) }
                ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)

            DetailsScreen(
                dealId = dealId,
                onBackClick = { navController.popBackStack() },
                onReportClick = {
                    navController.navigate(Routes.report(dealId))
                },
                // ✅ FIX: Check authentication state directly without creating FeedViewModel
                onAccountClick = {
                    val currentRole = userRole ?: "user"

                    when {
                        userId == null -> {
                            // Not logged in → Login screen
                            navController.navigate(Routes.LOGIN)
                        }
                        currentRole == "admin" || currentRole == "moderator" -> {
                            // Moderator/Admin → Check if first time
                            if (!deviceIdManager.hasSeenAccountScreen()) {
                                // First time → Show account screen
                                navController.navigate(Routes.ACCOUNT)
                            } else {
                                // Subsequent times → Show dashboard
                                navController.navigate(Routes.MODERATOR_DASHBOARD)
                            }
                        }
                        else -> {
                            // Regular user → Account screen
                            navController.navigate(Routes.ACCOUNT)
                        }
                    }
                }
            )
        }

        // ========================================
        // Report Screen - Report inappropriate content
        // ========================================
        composable(
            route = Routes.REPORT,
            arguments = listOf(
                navArgument("dealId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val dealId = backStackEntry.arguments?.getString("dealId") ?: ""
            ReportScreen(
                dealId = dealId,
                onClose = { navController.popBackStack() }
            )
        }

        // ========================================
        // ✅ SPRINT 4: MODERATOR SCREENS
        // Admin/moderator UI is English-only: wrapped in EnglishOnlyLayout so it
        // stays left-to-right even when the app language is Arabic.
        // ========================================

        // Moderator Dashboard - Main hub for moderators
        composable(Routes.MODERATOR_DASHBOARD) {
            EnglishOnlyLayout {
                ModeratorDashboardScreen(
                    onBackClick = { navController.popBackStack() },
                    onPendingDealsClick = {
                        navController.navigate(Routes.PENDING_DEALS)
                    },
                    onReportsClick = {  // ✅ NEW: Navigate to reports screen (2025-11-22)
                        navController.navigate(Routes.REPORTS)
                    },
                    onAnalyticsClick = {  // ✅ NEW: Navigate to analytics dashboard (2025-11-24)
                        navController.navigate(Routes.ANALYTICS_DASHBOARD)
                    },
                    onUserManagementClick = {
                        navController.navigate(Routes.USER_MANAGEMENT)
                    },
                    onAuditLogClick = {
                        navController.navigate(Routes.AUDIT_LOG)
                    },
                    onFeedbackClick = {
                        navController.navigate(Routes.ADMIN_FEEDBACK)
                    },
                    onLogout = {
                        // Return to feed after logout
                        navController.navigate(Routes.FEED) {
                            popUpTo(Routes.FEED) { inclusive = true }
                        }
                    }
                )
            }
        }

        // Pending Deals Screen - Review deals awaiting approval
        composable(Routes.PENDING_DEALS) {
            EnglishOnlyLayout {
                PendingDealsScreen(
                    onBackClick = { navController.popBackStack() },
                    onDealClick = { deal ->
                        navController.navigate(Routes.details(deal.id))
                    }
                )
            }
        }

        // ✅ NEW: Reports Screen - Review user-submitted reports (2025-11-22)
        composable(Routes.REPORTS) {
            EnglishOnlyLayout {
                ReportsScreen(
                    onBackClick = { navController.popBackStack() },
                    onDealClick = { dealId ->
                        navController.navigate(Routes.details(dealId))
                    }
                )
            }
        }

        // Stats dashboard: anonymous aggregates from our own database (no tracking SDKs)
        composable(Routes.ANALYTICS_DASHBOARD) {
            EnglishOnlyLayout {
                AnalyticsDashboardScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // ✅ NEW: User Management - Admin-only role/ban/trust management (2025-11-27)
        composable(Routes.USER_MANAGEMENT) {
            EnglishOnlyLayout {
                UserManagementScreen(
                    onBackClick = { navController.popBackStack() },
                    onViewUserClick = { userId ->
                        navController.navigate(Routes.userProfile(userId))
                    }
                )
            }
        }

        // ✅ NEW: Audit Log - Admin-only moderation history (2025-11-27)
        composable(Routes.AUDIT_LOG) {
            EnglishOnlyLayout {
                AuditLogScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // ✅ NEW: Admin Feedback Inbox (2025-11-27)
        composable(Routes.ADMIN_FEEDBACK) {
            EnglishOnlyLayout {
                FeedbackAdminScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // User Profile Screen - View any user's profile and deals
        composable(
            route = Routes.USER_PROFILE,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            UserProfileScreen(
                userId = userId,
                onBackClick = { navController.popBackStack() },
                onDealClick = { dealId ->
                    navController.navigate(Routes.details(dealId))
                }
            )
        }
    }
}
