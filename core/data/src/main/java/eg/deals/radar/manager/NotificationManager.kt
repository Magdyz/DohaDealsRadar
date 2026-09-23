package eg.deals.radar.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import eg.deals.domain.DealCategory
import eg.deals.radar.util.AppLanguage

/**
 * ========================================
 * ✨ NOTIFICATION MANAGER — 2025 Edition
 * Smart notification preferences and FCM subscription management
 * ========================================
 *
 * Created: 2025-11-25 by @Magdyz
 * Location: core/data/src/main/java/eg/deals/radar/manager/NotificationManager.kt
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
        private const val KEY_ALL_DEALS_CHOSEN = "notify_all_deals_chosen" // user made an explicit choice
        private const val KEY_CATEGORY_PREFIX = "notify_cat_"
        private const val KEY_USER_TOPIC = "user_topic"
        private const val TAG = "NotificationManager"

        // FCM topic names (English). Arabic users subscribe to the same topics
        // with an "_ar" suffix so the backend can send Arabic notification text.
        private const val TOPIC_ALL_DEALS = "all_deals"
        private const val TOPIC_CATEGORY_PREFIX = "cat_"
        private const val TOPIC_ARABIC_SUFFIX = "_ar"

        /** Topic name for the given base topic in the given language. */
        fun topicFor(baseTopic: String, language: String): String =
            if (language == AppLanguage.ARABIC) baseTopic + TOPIC_ARABIC_SUFFIX else baseTopic

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
            prefs.edit().putBoolean(KEY_ALL_DEALS_CHOSEN, true).apply()
            _allDealsEnabledFlow.value = enabled

            // Subscribe or unsubscribe to FCM topic
            if (enabled) {
                FirebaseMessaging.getInstance().subscribeToTopic(topicFor(TOPIC_ALL_DEALS, AppLanguage.current())).await()
                Log.d(TAG, "✅ Subscribed to topic: $TOPIC_ALL_DEALS")
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topicFor(TOPIC_ALL_DEALS, AppLanguage.current())).await()
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
            val topic = topicFor("$TOPIC_CATEGORY_PREFIX${category.id}", AppLanguage.current())

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

    /**
     * New-deal alerts are on by default once the phone allows notifications.
     * Skipped as soon as the user picks a side in notification settings, so an
     * opt-out is never overridden.
     */
    suspend fun applyDefaultSubscriptions(context: Context) {
        if (prefs.getBoolean(KEY_ALL_DEALS_CHOSEN, false)) return
        val allowed = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        runCatching {
            saveNotificationPreference(KEY_ALL_DEALS, true)
            _allDealsEnabledFlow.value = true
            FirebaseMessaging.getInstance()
                .subscribeToTopic(topicFor(TOPIC_ALL_DEALS, AppLanguage.current())).await()
            Log.d(TAG, "🔔 Subscribed to new-deal alerts by default")
        }.onFailure { Log.w(TAG, "Default subscription failed: ${it.javaClass.simpleName}") }
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
            for (language in listOf(AppLanguage.ENGLISH, AppLanguage.ARABIC)) {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topicFor(TOPIC_ALL_DEALS, language)).await()
            }

            DealCategory.values().forEach { category ->
                for (language in listOf(AppLanguage.ENGLISH, AppLanguage.ARABIC)) {
                    val topic = topicFor("$TOPIC_CATEGORY_PREFIX${category.id}", language)
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
                }
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

    // ========================================
    // 🌐 LANGUAGE SWITCH
    // ========================================

    /**
     * Move every enabled subscription from the old language topics to the new
     * ones (e.g. "all_deals" -> "all_deals_ar"). Fire-and-forget: FCM retries
     * pending topic operations on its own.
     */
    fun onLanguageChanged(oldLanguage: String, newLanguage: String) {
        val messaging = try {
            FirebaseMessaging.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase not available, cannot move topic subscriptions", e)
            return
        }

        val enabledTopics = buildList {
            if (isAllDealsEnabled()) add(TOPIC_ALL_DEALS)
            DealCategory.values()
                .filter { isCategoryEnabled(it) }
                .forEach { add("$TOPIC_CATEGORY_PREFIX${it.id}") }
            userTopicBase()?.let { add(it) }
        }

        enabledTopics.forEach { baseTopic ->
            messaging.unsubscribeFromTopic(topicFor(baseTopic, oldLanguage))
            messaging.subscribeToTopic(topicFor(baseTopic, newLanguage))
        }
        Log.d(TAG, "🌐 Moved ${enabledTopics.size} topic subscriptions: $oldLanguage -> $newLanguage")
    }

    // ========================================
    // 👤 PERSONAL UPDATES ("your deal is live / wasn't approved")
    // Topic: user_<profileId> (+ "_ar" for Arabic)
    // ========================================

    private fun userTopicBase(): String? = prefs.getString(KEY_USER_TOPIC, null)

    fun subscribeUserTopic(profileId: String) {
        val base = "user_$profileId"
        val old = userTopicBase()
        prefs.edit().putString(KEY_USER_TOPIC, base).apply()
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            if (old != null && old != base) {
                messaging.unsubscribeFromTopic(topicFor(old, AppLanguage.ENGLISH))
                messaging.unsubscribeFromTopic(topicFor(old, AppLanguage.ARABIC))
            }
            messaging.subscribeToTopic(topicFor(base, AppLanguage.current()))
        }.onFailure { Log.w(TAG, "subscribeUserTopic failed: ${it.javaClass.simpleName}") }
    }

    fun unsubscribeUserTopic() {
        val base = userTopicBase() ?: return
        prefs.edit().remove(KEY_USER_TOPIC).apply()
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.unsubscribeFromTopic(topicFor(base, AppLanguage.ENGLISH))
            messaging.unsubscribeFromTopic(topicFor(base, AppLanguage.ARABIC))
        }
    }
}
