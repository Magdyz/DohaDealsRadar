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


    /**
     * Helper function to create details route with dealId
     */
    fun details(dealId: String): String = "details/$dealId"

    /**
     * Helper function to create report route with dealId
     */
    fun report(dealId: String): String = "report/$dealId"
}