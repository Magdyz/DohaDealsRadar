package qa.deals.doha.navigation

/**
 * Navigation routes for the app.
 * Centralized route definitions following 2025 best practices.
 */
object Routes {
    const val FEED = "feed"
    const val POST = "post"
    const val DETAILS = "details/{dealId}"
    const val REPORT = "report/{dealId}"

    const val ARCHIVE = "archive"  // ✅ SPRINT 6: Archive screen route
    const val ONBOARDING = "onboarding" // ✅ ADD THIS NEW ROUTE
    const val FEEDBACK = "feedback"  // ✅ NEW: Feedback screen route (2025-11-22)

    // SPRINT 5: Authentication routes
    const val LOGIN = "login"
    const val ACCOUNT = "account"

    // SPRINT 4: Moderator routes
    const val MODERATOR_DASHBOARD = "moderator/dashboard"
    const val PENDING_DEALS = "moderator/pending"
    const val REPORTS = "moderator/reports"  // ✅ NEW: Reports screen route (2025-11-22)
    const val ANALYTICS_DASHBOARD = "moderator/analytics"  // ✅ NEW: Analytics dashboard route (2025-11-24)
    const val USER_PROFILE = "profile/{userId}"

    /**
     * Helper function to create details route with dealId
     */
    fun details(dealId: String): String = "details/$dealId"

    /**
     * Helper function to create report route with dealId
     */
    fun report(dealId: String): String = "report/$dealId"
    /**
     * Helper function to create user profile route with userId
     */
    fun userProfile(userId: String): String = "profile/$userId"

}