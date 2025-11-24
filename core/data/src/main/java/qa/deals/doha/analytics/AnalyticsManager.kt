package qa.deals.doha.analytics

import android.util.Log
import com.posthog.PostHog

/**
 * ========================================
 * ✨ ANALYTICS MANAGER
 * Centralized wrapper for PostHog analytics
 * ========================================
 *
 * Purpose:
 * - Provides type-safe methods for tracking user behavior
 * - Abstracts PostHog SDK implementation details
 * - Makes analytics code maintainable and testable
 * - Gracefully handles SDK initialization failures
 *
 * Features:
 * - Deal interactions (views, clicks, submissions, votes)
 * - User identification and properties
 * - Feature flag support
 * - Screen view tracking
 * - Custom event tracking
 *
 * Usage:
 * ```kotlin
 * // Track a deal click
 * AnalyticsManager.trackDealClicked(
 *     dealId = "123",
 *     category = "Electronics",
 *     priceQar = 499.99
 * )
 *
 * // Identify user
 * AnalyticsManager.identifyUser(
 *     userId = "user_123",
 *     email = "user@example.com"
 * )
 *
 * // Check feature flag
 * val isEnabled = AnalyticsManager.isFeatureEnabled("new_ui_design", false)
 * ```
 *
 * Created: 2025-11-24
 * Updated: 2025-11-24
 *
 * Note: Uses PostHog singleton after PostHogAndroid.setup() is called in Application class
 */
object AnalyticsManager {

    private const val TAG = "AnalyticsManager"

    /**
     * Safely execute PostHog operations with error handling
     * Returns true if operation succeeded, false otherwise
     */
    private inline fun safePostHogOperation(operation: () -> Unit): Boolean {
        return try {
            operation()
            true
        } catch (e: Exception) {
            Log.w(TAG, "PostHog operation failed: ${e.message}")
            false
        }
    }

    // ========================================
    // DEAL TRACKING
    // ========================================

    /**
     * Track when a user clicks on a deal card to view details
     *
     * @param dealId Unique identifier for the deal
     * @param category Deal category (e.g., "Electronics", "Fashion")
     * @param priceQar Original price in QAR
     * @param discountedPriceQar Discounted price in QAR (null if no discount)
     * @param position Position of the deal in the feed (for engagement analysis)
     */
    fun trackDealClicked(
        dealId: String,
        category: String,
        priceQar: Double?,
        discountedPriceQar: Double? = null,
        position: Int? = null
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "deal_clicked",
                properties = buildMap {
                    put("deal_id", dealId)
                    put("category", category)
                    priceQar?.let { put("price_qar", it) }
                    discountedPriceQar?.let { put("discounted_price_qar", it) }
                    position?.let { put("position", it) }
                }
            )
        }
    }

    /**
     * Track when a user submits a new deal
     *
     * @param category Deal category
     * @param hasPromoCode Whether the deal includes a promo code
     * @param hasImages Whether the user uploaded images
     * @param imageCount Number of images uploaded
     */
    fun trackDealSubmitted(
        category: String,
        hasPromoCode: Boolean,
        hasImages: Boolean,
        imageCount: Int = 0
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "deal_submitted",
                properties = mapOf(
                    "category" to category,
                    "has_promo_code" to hasPromoCode,
                    "has_images" to hasImages,
                    "image_count" to imageCount
                )
            )
        }
    }

    /**
     * Track when a user votes on a deal (upvote/downvote)
     *
     * @param dealId Unique identifier for the deal
     * @param voteType Either "upvote" or "downvote"
     * @param previousVote Previous vote if user is changing their vote (null for first vote)
     */
    fun trackDealVoted(
        dealId: String,
        voteType: String,
        previousVote: String? = null
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "deal_voted",
                properties = buildMap {
                    put("deal_id", dealId)
                    put("vote_type", voteType)
                    previousVote?.let { put("previous_vote", it) }
                }
            )
        }
    }

    /**
     * Track when a user reports a deal
     *
     * @param dealId Unique identifier for the deal
     * @param reason Report reason (e.g., "expired", "spam", "incorrect_info")
     */
    fun trackDealReported(
        dealId: String,
        reason: String
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "deal_reported",
                properties = mapOf(
                    "deal_id" to dealId,
                    "reason" to reason
                )
            )
        }
    }

    // ========================================
    // 🎯 CORE VALUE ACTIONS (2025 Best Practices)
    // Track meaningful user actions, not button clicks
    // ========================================

    /**
     * 📱 Track when a user VIEWS a deal (Happy Path Step 1)
     *
     * This is different from deal_clicked - it tracks when the deal appears on screen
     * Use this to measure:
     * - Which categories get the most views
     * - Price sensitivity (do low/high prices get more views?)
     * - Position impact (do users scroll to see "Cold" deals?)
     *
     * @param dealId Unique identifier for the deal
     * @param category Deal category (e.g., "Food", "Electronics")
     * @param priceLevel "Low" (<100 QAR), "Medium" (100-500), "High" (>500)
     * @param temperature "Hot" (high votes), "Warm", "Cold" (low votes)
     * @param position Position in feed (0 = top)
     */
    fun trackDealViewed(
        dealId: String,
        category: String,
        priceLevel: String,
        temperature: String,
        position: Int
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "Deal_Viewed",  // Capitalized for emphasis in analytics
                properties = mapOf(
                    "deal_id" to dealId,
                    "category" to category,
                    "price_level" to priceLevel,
                    "temperature" to temperature,
                    "position" to position
                )
            )
        }
    }

    /**
     * 🎯 Track when a user CLICKS the deal link (Happy Path Step 2 - THE CONVERSION)
     *
     * This is THE most important metric - it measures actual conversions.
     * If 100 people view a deal but only 2 click, you have a problem.
     *
     * Key Insights:
     * - Conversion rate by category
     * - Conversion rate by price level
     * - Conversion rate by position (do bottom deals get clicks?)
     * - Time from view to click (user hesitation)
     *
     * @param dealId Unique identifier for the deal
     * @param category Deal category
     * @param priceLevel Price level of the deal
     * @param timeSpentViewing Seconds user spent viewing before clicking
     */
    fun trackDealLinkClicked(
        dealId: String,
        category: String,
        priceLevel: String,
        timeSpentViewing: Int? = null
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "Deal_Link_Clicked",  // THE CONVERSION EVENT
                properties = buildMap {
                    put("deal_id", dealId)
                    put("category", category)
                    put("price_level", priceLevel)
                    timeSpentViewing?.let { put("time_spent_viewing_seconds", it) }
                }
            )
        }
    }

    /**
     * 🚀 Track when a user SHARES a deal (Happy Path Step 3 - VIRAL LOOP)
     *
     * Sharing is gold - it's free marketing and shows high engagement.
     *
     * Key Insights:
     * - Which categories get shared most?
     * - Do "Hot" deals get shared more than "Cold"?
     * - Share rate by platform (WhatsApp, Telegram, etc.)
     *
     * @param dealId Unique identifier for the deal
     * @param category Deal category
     * @param shareMethod "WhatsApp", "Telegram", "Copy Link", etc.
     */
    fun trackDealShared(
        dealId: String,
        category: String,
        shareMethod: String
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "Deal_Shared",  // VIRAL LOOP
                properties = mapOf(
                    "deal_id" to dealId,
                    "category" to category,
                    "share_method" to shareMethod
                )
            )
        }
    }

    /**
     * 📊 Track scroll depth (User Focus)
     *
     * Measure if users scroll to see "Cold" deals or only view top "Hot" deals.
     *
     * @param maxPosition Furthest position user scrolled to (0-based)
     * @param totalDeals Total number of deals in feed
     * @param scrollPercentage Percentage of feed scrolled (0-100)
     */
    fun trackScrollDepth(
        maxPosition: Int,
        totalDeals: Int,
        scrollPercentage: Int
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "Scroll_Depth",
                properties = mapOf(
                    "max_position" to maxPosition,
                    "total_deals" to totalDeals,
                    "scroll_percentage" to scrollPercentage
                )
            )
        }
    }

    /**
     * 🔥 Track when user votes (Feature Adoption)
     *
     * Enhanced version with adoption tracking
     *
     * @param dealId Unique identifier for the deal
     * @param voteType "fire" or "ice"
     * @param isFirstVote Is this the user's first ever vote?
     */
    fun trackVoteCast(
        dealId: String,
        voteType: String,
        isFirstVote: Boolean = false
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "Vote_Cast",
                properties = mapOf(
                    "deal_id" to dealId,
                    "vote_type" to voteType,
                    "is_first_vote" to isFirstVote  // Track feature adoption
                )
            )
        }
    }

    /**
     * 🔍 Track search attempts (Understand what users can't find)
     *
     * Critical for understanding unmet needs.
     *
     * @param searchQuery What the user searched for
     * @param resultsFound Number of results returned
     * @param resultClicked Did user click a result?
     */
    fun trackSearch(
        searchQuery: String,
        resultsFound: Int,
        resultClicked: Boolean
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = "Search_Performed",
                properties = mapOf(
                    "search_query" to searchQuery,
                    "results_found" to resultsFound,
                    "result_clicked" to resultClicked
                )
            )
        }
    }

    // ========================================
    // HELPER FUNCTIONS
    // ========================================

    /**
     * Calculate price level from QAR price
     */
    fun calculatePriceLevel(priceQar: Double?): String {
        return when {
            priceQar == null -> "Unknown"
            priceQar < 100 -> "Low"
            priceQar < 500 -> "Medium"
            else -> "High"
        }
    }

    /**
     * Calculate deal temperature based on vote counts
     */
    fun calculateTemperature(hotVotes: Int, coldVotes: Int): String {
        val totalVotes = hotVotes + coldVotes
        return when {
            totalVotes == 0 -> "New"
            hotVotes > coldVotes * 2 -> "Hot"
            coldVotes > hotVotes * 2 -> "Cold"
            else -> "Warm"
        }
    }

    // ========================================
    // USER IDENTIFICATION
    // ========================================

    /**
     * Identify a user with their unique ID and properties
     *
     * Call this when:
     * - User signs up
     * - User logs in
     * - User profile is loaded
     *
     * @param userId Unique identifier for the user
     * @param email User's email (optional)
     * @param name User's name (optional)
     * @param properties Additional user properties
     */
    fun identifyUser(
        userId: String,
        email: String? = null,
        name: String? = null,
        properties: Map<String, Any> = emptyMap()
    ) {
        safePostHogOperation {
            val userProperties = buildMap {
                email?.let { put("email", it) }
                name?.let { put("name", it) }
                putAll(properties)
            }

            PostHog.identify(
                distinctId = userId,
                userProperties = userProperties
            )
        }
    }

    /**
     * Reset user identity (call on logout)
     */
    fun resetUser() {
        safePostHogOperation {
            PostHog.reset()
        }
    }

    // ========================================
    // SCREEN TRACKING
    // ========================================

    /**
     * Track screen view
     *
     * Note: PostHog automatically tracks screen views if captureScreenViews is enabled.
     * Use this method only if you need to track additional properties with the screen view.
     *
     * @param screenName Name of the screen (e.g., "Feed", "DealDetails", "PostDeal")
     * @param properties Additional properties about the screen view
     */
    fun trackScreenView(
        screenName: String,
        properties: Map<String, Any> = emptyMap()
    ) {
        safePostHogOperation {
            PostHog.screen(
                screenTitle = screenName,
                properties = properties
            )
        }
    }

    // ========================================
    // FEATURE FLAGS
    // ========================================

    /**
     * Check if a feature flag is enabled
     *
     * @param flagKey Feature flag key (e.g., "new_ui_design", "enable_dark_mode")
     * @param defaultValue Default value if flag is not found or PostHog is not initialized
     * @return Boolean value of the feature flag
     */
    fun isFeatureEnabled(
        flagKey: String,
        defaultValue: Boolean = false
    ): Boolean {
        return try {
            PostHog.isFeatureEnabled(flagKey) ?: defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check feature flag: ${e.message}")
            defaultValue
        }
    }

    /**
     * Get feature flag payload (for A/B testing with variants)
     *
     * @param flagKey Feature flag key
     * @return Feature flag payload (can be string, number, boolean, or JSON)
     */
    fun getFeatureFlagPayload(flagKey: String): Any? {
        return try {
            PostHog.getFeatureFlagPayload(flagKey)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get feature flag payload: ${e.message}")
            null
        }
    }

    // ========================================
    // CUSTOM EVENTS
    // ========================================

    /**
     * Track a custom event with properties
     *
     * Use this for any event not covered by the specific tracking methods above.
     *
     * @param eventName Name of the event (e.g., "promo_code_copied", "share_clicked")
     * @param properties Event properties
     */
    fun trackEvent(
        eventName: String,
        properties: Map<String, Any> = emptyMap()
    ) {
        safePostHogOperation {
            PostHog.capture(
                event = eventName,
                properties = properties
            )
        }
    }

    // ========================================
    // USER PROPERTIES
    // ========================================

    /**
     * Update user properties without identifying
     *
     * Use this to update user properties after initial identification.
     *
     * @param properties Properties to update
     */
    fun setUserProperties(properties: Map<String, Any>) {
        safePostHogOperation {
            PostHog.capture(
                event = "\$set",
                properties = properties
            )
        }
    }

    // ========================================
    // ENGAGEMENT TRACKING
    // ========================================

    /**
     * Track when user pulls to refresh
     */
    fun trackPullToRefresh() {
        trackEvent("pull_to_refresh")
    }

    /**
     * Track when user submits feedback
     *
     * @param feedbackType Type of feedback (e.g., "bug_report", "feature_request", "general")
     * @param hasScreenshot Whether screenshot was attached
     */
    fun trackFeedbackSubmitted(
        feedbackType: String,
        hasScreenshot: Boolean = false
    ) {
        trackEvent(
            "feedback_submitted",
            mapOf(
                "type" to feedbackType,
                "has_screenshot" to hasScreenshot
            )
        )
    }

    /**
     * Track when user views onboarding
     *
     * @param step Onboarding step number
     * @param totalSteps Total number of onboarding steps
     */
    fun trackOnboardingStep(
        step: Int,
        totalSteps: Int
    ) {
        trackEvent(
            "onboarding_step_viewed",
            mapOf(
                "step" to step,
                "total_steps" to totalSteps
            )
        )
    }

    /**
     * Track when user completes onboarding
     */
    fun trackOnboardingCompleted() {
        trackEvent("onboarding_completed")
    }
}
