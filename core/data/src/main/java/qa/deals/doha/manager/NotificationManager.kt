package qa.deals.doha.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import qa.deals.domain.DealCategory

/**
 * ========================================
 * ✨ NOTIFICATION MANAGER — 2025 Edition
 * Smart notification preferences and FCM subscription management
 * ========================================
 *
 * Created: 2025-11-25 by @Magdyz
 * Location: core/data/src/main/java/qa/deals/doha/manager/NotificationManager.kt
 *
 * Responsibilities:
 * 1. Store notification preferences (global + category-specific)
 * 2. Subscribe/unsubscribe to FCM topics
 * 3. Reactive state management with Kotlin Flow
 * 4. Battery-efficient topic-based messaging
 *
 * FCM Topics:
 * - "all_deals" - Global subscription for all new deals
 * - "cat_{categoryId}" - Category-specific subscriptions (e.g., "cat_food_dining")
 *
 * Persistence:
 * - SharedPreferences (consistent with DeviceIdManager pattern)
 * - Memory cache for reactive UI updates
 * - Thread-safe operations
 *
 * 📌 Singleton: Use NotificationManager.getInstance(context)
 */
class NotificationManager private constructor(context: Context) {

    // ========================================
    // ✨ SHARED PREFERENCES
    // ========================================

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_FILE,
        Context.MODE_PRIVATE
    )

    // ========================================
    // ✨ REACTIVE STATE FLOWS
    // ========================================

    // Global "All Deals" notification state
    private val _allDealsEnabledFlow = MutableStateFlow(getNotificationPreference(KEY_ALL_DEALS))
    val allDealsEnabledFlow: StateFlow<Boolean> = _allDealsEnabledFlow.asStateFlow()

    // Category-specific notification states (cached for performance)
    private val categoryFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()

    init {
        // Initialize flow with current preference
        _allDealsEnabledFlow.value = getNotificationPreference(KEY_ALL_DEALS)
        Log.d(TAG, "✅ NotificationManager initialized")
    }

    companion object {
        private const val PREFS_FILE = "notification_prefs"
        private const val KEY_ALL_DEALS = "notify_all_deals"
        private const val KEY_CATEGORY_PREFIX = "notify_cat_"
        private const val TAG = "NotificationManager"

        // FCM topic names
        private const val TOPIC_ALL_DEALS = "all_deals"
        private const val TOPIC_CATEGORY_PREFIX = "cat_"

        @Volatile
        private var INSTANCE: NotificationManager? = null

        /**
         * ========================================
         * ✨ GET SINGLETON INSTANCE
         * Thread-safe singleton pattern
         * ========================================
         */
        fun getInstance(context: Context): NotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationManager(context.applicationContext).also {
                    INSTANCE = it
                    Log.d(TAG, "✅ NotificationManager instance created")
                }
            }
        }
    }

    // ========================================
    // 🔔 GLOBAL NOTIFICATION MANAGEMENT
    // ========================================

    /**
     * Check if "All Deals" notifications are enabled
     */
    fun isAllDealsEnabled(): Boolean {
        return getNotificationPreference(KEY_ALL_DEALS)
    }

    /**
     * Enable or disable "All Deals" notifications
     * Automatically subscribes/unsubscribes to FCM topic
     */
    suspend fun setAllDealsEnabled(enabled: Boolean) {
        try {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🔔 Setting All Deals notifications: $enabled")

            // Save preference first (optimistic update)
            saveNotificationPreference(KEY_ALL_DEALS, enabled)
            _allDealsEnabledFlow.value = enabled

            // Subscribe or unsubscribe to FCM topic
            if (enabled) {
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL_DEALS).await()
                Log.d(TAG, "✅ Subscribed to topic: $TOPIC_ALL_DEALS")
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_ALL_DEALS).await()
                Log.d(TAG, "✅ Unsubscribed from topic: $TOPIC_ALL_DEALS")
            }

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating All Deals subscription", e)
            // Revert optimistic update on failure
            val currentValue = getNotificationPreference(KEY_ALL_DEALS)
            _allDealsEnabledFlow.value = currentValue
            throw e
        }
    }

    // ========================================
    // 📂 CATEGORY NOTIFICATION MANAGEMENT
    // ========================================

    /**
     * Check if notifications are enabled for a specific category
     */
    fun isCategoryEnabled(category: DealCategory): Boolean {
        return getNotificationPreference("$KEY_CATEGORY_PREFIX${category.id}")
    }

    /**
     * Get reactive flow for category notification state
     */
    fun getCategoryFlow(category: DealCategory): StateFlow<Boolean> {
        val key = "$KEY_CATEGORY_PREFIX${category.id}"
        return categoryFlows.getOrPut(key) {
            MutableStateFlow(getNotificationPreference(key))
        }.asStateFlow()
    }

    /**
     * Enable or disable notifications for a specific category
     * Automatically subscribes/unsubscribes to FCM topic
     */
    suspend fun setCategoryEnabled(category: DealCategory, enabled: Boolean) {
        try {
            val key = "$KEY_CATEGORY_PREFIX${category.id}"
            val topic = "$TOPIC_CATEGORY_PREFIX${category.id}"

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🔔 Setting ${category.displayName} notifications: $enabled")

            // Save preference first (optimistic update)
            saveNotificationPreference(key, enabled)

            // Update flow if exists
            categoryFlows[key]?.value = enabled

            // Subscribe or unsubscribe to FCM topic
            if (enabled) {
                FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
                Log.d(TAG, "✅ Subscribed to topic: $topic")
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
                Log.d(TAG, "✅ Unsubscribed from topic: $topic")
            }

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating ${category.displayName} subscription", e)
            // Revert optimistic update on failure
            val key = "$KEY_CATEGORY_PREFIX${category.id}"
            val currentValue = getNotificationPreference(key)
            categoryFlows[key]?.value = currentValue
            throw e
        }
    }

    /**
     * Get notification states for all categories
     * Useful for initializing UI
     */
    fun getAllCategoryStates(): Map<DealCategory, Boolean> {
        return DealCategory.values().associateWith { category ->
            isCategoryEnabled(category)
        }
    }

    // ========================================
    // 💾 PRIVATE HELPER METHODS
    // ========================================

    /**
     * Save notification preference to SharedPreferences
     */
    private fun saveNotificationPreference(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
        Log.d(TAG, "💾 Saved preference: $key = $enabled")
    }

    /**
     * Get notification preference from SharedPreferences
     * Defaults to false (opt-in model for notifications)
     */
    private fun getNotificationPreference(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }

    // ========================================
    // 🔍 DEBUGGING & TESTING
    // ========================================

    /**
     * Get all notification preferences (for debugging)
     */
    fun getAllPreferences(): Map<String, Boolean> {
        val preferences = mutableMapOf<String, Boolean>()

        // Add global preference
        preferences["all_deals"] = isAllDealsEnabled()

        // Add category preferences
        DealCategory.values().forEach { category ->
            preferences[category.displayName] = isCategoryEnabled(category)
        }

        return preferences
    }

    /**
     * Clear all notification preferences (for testing only)
     * WARNING: This will unsubscribe from all topics
     */
    suspend fun clearAllPreferences() {
        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.w(TAG, "⚠️  CLEARING ALL NOTIFICATION PREFERENCES")

        try {
            // Unsubscribe from all topics
            FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_ALL_DEALS).await()

            DealCategory.values().forEach { category ->
                val topic = "$TOPIC_CATEGORY_PREFIX${category.id}"
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
            }

            // Clear all preferences
            prefs.edit().clear().apply()

            // Reset flows
            _allDealsEnabledFlow.value = false
            categoryFlows.values.forEach { it.value = false }

            Log.w(TAG, "✅ All preferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing preferences", e)
        }

        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}
