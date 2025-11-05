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
import qa.deals.doha.feature.archive.ArchiveScreen  // ✅ SPRINT 6: Import ArchiveScreen
// ✅ 1. ADD THIS IMPORT for the new screen
import qa.deals.onboarding.OnboardingScreen

/**
 * Main navigation host for the app.
 * Manages navigation between all screens.
 * ✅ OPTIMIZED: Instant navigation, no animation delay
 * ✅ SPRINT 6: Added Archive screen navigation
 *
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
    }
}