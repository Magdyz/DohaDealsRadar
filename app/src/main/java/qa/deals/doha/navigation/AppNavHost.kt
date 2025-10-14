package qa.deals.doha.navigation

import qa.deals.doha.feature.report.ReportScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import qa.deals.doha.feature.feed.FeedScreen
import qa.deals.doha.feature.post.PostScreen
import qa.deals.doha.feature.details.DetailsScreen

/**
 * Main navigation host for the app.
 * Manages navigation between all screens.
 */
@Composable
fun AppNavHost(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Routes.FEED
    ) {
        // Feed Screen - Home screen showing list of deals
        composable(Routes.FEED) {
            FeedScreen(
                onDealClick = { dealId ->
                    navController.navigate(Routes.details(dealId))
                },
                onPostClick = {
                    navController.navigate(Routes.POST)
                }
            )
        }

        // Post Screen - Submit a new deal
        composable(Routes.POST) {
            PostScreen(
                onBackClick = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() }
            )
        }

        // Details Screen - Full deal details
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
                    navController.navigate(Routes.report(dealId))  // ✅ FIX: Navigate to report
                }
            )
        }

        // Report Screen - Report inappropriate content
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