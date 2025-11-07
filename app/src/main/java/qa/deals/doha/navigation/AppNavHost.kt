package qa.deals.doha.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import qa.deals.doha.feature.details.DetailsScreen
import qa.deals.doha.feature.feed.FeedScreen
import qa.deals.doha.feature.post.PostScreen
import qa.deals.doha.feature.report.ReportScreen
import qa.deals.doha.feature.archive.ArchiveScreen
import qa.deals.onboarding.OnboardingScreen
// SPRINT 4: Import moderator and profile screens
import qa.deals.doha.feature.feed.moderator.ModeratorDashboardScreen
import qa.deals.doha.feature.feed.moderator.PendingDealsScreen
import qa.deals.doha.feature.feed.profile.UserProfileScreen
// SPRINT 5: Import authentication and account screens
import qa.deals.doha.feature.post.LoginScreen
import qa.deals.doha.feature.feed.account.UserAccountScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import qa.deals.doha.feature.feed.FeedViewModel
import androidx.lifecycle.ViewModel

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

            val deviceIdManager = remember { qa.deals.doha.datastore.DeviceIdManager.getInstance(context) }

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
                }
            )
        }

        // ========================================
        // ✅ SPRINT 6: Archive Screen - Show archived deals
        // Shows deals older than 10 days
        // ========================================
        composable(Routes.ARCHIVE) {
            ArchiveScreen(
                onBackClick = { navController.popBackStack() },
                onDealClick = { dealId ->
                    navController.navigate(Routes.details(dealId))
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
                onSuccess = { navController.popBackStack() }
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
            DetailsScreen(
                dealId = dealId,
                onBackClick = { navController.popBackStack() },
                onReportClick = {
                    navController.navigate(Routes.report(dealId))
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
        // ========================================

        // Moderator Dashboard - Main hub for moderators
        composable(Routes.MODERATOR_DASHBOARD) {
            ModeratorDashboardScreen(
                onBackClick = { navController.popBackStack() },
                onPendingDealsClick = {
                    navController.navigate(Routes.PENDING_DEALS)
                },
                onUserManagementClick = {
                    // TODO: Sprint 6 - User management screen
                    // This will navigate to admin panel in future sprint
                },
                onAuditLogClick = {
                    // TODO: Sprint 9 - Audit log screen
                    // This will show moderation history in future sprint
                },
                onLogout = {
                    // Return to feed after logout
                    navController.navigate(Routes.FEED) {
                        popUpTo(Routes.FEED) { inclusive = true }
                    }
                }
            )
        }

        // Pending Deals Screen - Review deals awaiting approval
        composable(Routes.PENDING_DEALS) {
            PendingDealsScreen(
                onBackClick = { navController.popBackStack() },
                onDealClick = { deal ->
                    navController.navigate(Routes.details(deal.id))
                }
            )
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
