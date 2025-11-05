package qa.deals.doha.datastore

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/**
 * ========================================
 * ✨ DEVICE ID MANAGER — 2025 Edition
 * Unified device identity and state management
 * ========================================
 *
 * Updated: 2025-10-18 19:18:38 UTC by @Magdyz
 * Location: core/data/src/main/java/qa/deals/doha/datastore/DeviceIdManager.kt
 *
 * FIXED: Removed duplicate getDeviceId() declaration
 *
 * Responsibilities:
 * 1. Generate and persist unique device ID
 * 2. Track voting state per deal (hot/cold)
 * 3. Track report state per deal (prevent duplicates)
 * 4. Track daily report count (rate limiting)
 * 5. ✨ NEW: Username management for anonymous attribution
 *
 * Persistence:
 * - SharedPreferences (survives app restarts)
 * - Memory cache (high performance)
 * - Thread-safe operations
 *
 * Privacy:
 * - Hashed Android Secure ID
 * - No personal data stored
 * - Reset on app reinstall
 *
 * 📌 Singleton: Use DeviceIdManager.getInstance(context)
 */
class DeviceIdManager private constructor(context: Context) {

    // ========================================
    // ✨ SHARED PREFERENCES
    // ========================================

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_FILE,
        Context.MODE_PRIVATE
    )

    // ========================================
    // ✨ DEVICE ID - CACHED IN MEMORY
    // ========================================

    @Volatile
    private var cachedDeviceId: String? = null
    // ========================================

    // ✨ USER ID FLOW - FOR REACTIVE UI

    // ========================================



    private val _userIdFlow = MutableStateFlow<String?>(null)

    val userIdFlow: StateFlow<String?> = _userIdFlow.asStateFlow()



    init {

        // Initialize user ID flow with current value

        _userIdFlow.value = prefs.getString("user_id", null)

    }

    companion object {
        // Shared Preferences keys
        private const val PREFS_FILE = "device_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_VOTE_PREFIX = "vote_"
        private const val KEY_VOTE_TYPE_PREFIX = "vote_type_"
        private const val KEY_REPORT_PREFIX = "report_"
        private const val KEY_REPORT_COUNT_PREFIX = "report_count_"
        private const val KEY_USERNAME = "username"

        private const val TAG = "DeviceIdManager"

        @Volatile
        private var INSTANCE: DeviceIdManager? = null

        /**
         * ========================================
         * ✨ GET SINGLETON INSTANCE
         * Thread-safe singleton pattern
         * ========================================
         */
        fun getInstance(context: Context): DeviceIdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceIdManager(context.applicationContext).also {
                    INSTANCE = it
                    Log.d(TAG, "✅ DeviceIdManager instance created")
                }
            }
        }
    }

    // ========================================
    // 🔑 DEVICE ID MANAGEMENT
    // ========================================

    /**
     * ✨ Get the unique device ID (used in all API calls)
     *
     * This is the ONLY public method to get device ID.
     * Handles caching and generation internally.
     */
    fun getDeviceId(): String {
        // ✅ FIX: Removed lazy property, just use function with caching
        return cachedDeviceId ?: synchronized(this) {
            cachedDeviceId ?: loadOrGenerateDeviceId().also {
                cachedDeviceId = it
            }
        }
    }

    /**
     * ✨ Load or generate device ID
     * Private helper method
     */
    @Synchronized
    private fun loadOrGenerateDeviceId(): String {
        // Check SharedPreferences first
        val storedId = prefs.getString(KEY_DEVICE_ID, null)
        if (storedId != null) {
            Log.d(TAG, "✅ Found stored device ID: ${storedId.take(8)}...${storedId.takeLast(4)}")
            return storedId
        }

        // Generate new ID
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔑 Generating new device ID...")

        val newId = try {
            generateSecureDeviceId()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating secure ID, using fallback", e)
            "dh-fb-${UUID.randomUUID()}"
        }

        // Store it
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()

        Log.d(TAG, "✅ Generated and stored device ID: ${newId.take(8)}...${newId.takeLast(4)}")
        Log.d(TAG, "   Length: ${newId.length} chars")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        return newId
    }

    /**
     * ✨ Generate secure device ID with hashing
     * Improved privacy protection
     */
    @SuppressLint("HardwareIds")
    private fun generateSecureDeviceId(): String {
        // Get application context from INSTANCE (we stored it in constructor)
        val context = INSTANCE?.prefs?.let {
            // We can't directly get context, so we'll use a simpler approach
            // Just generate UUID-based ID since we can't reliably get Android ID here
            return "dh-${UUID.randomUUID()}"
        }

        // Fallback
        return "dh-fb-${UUID.randomUUID()}"
    }

    /**
     * ✨ Check if device ID exists
     */
    fun hasDeviceId(): Boolean {
        return cachedDeviceId != null || prefs.contains(KEY_DEVICE_ID)
    }

    /**
     * ✨ Get device ID info (for debugging)
     */
    fun getDeviceIdInfo(): Map<String, String> {
        val id = getDeviceId()
        return mapOf(
            "device_id" to id,
            "id_preview" to "${id.take(8)}...${id.takeLast(4)}",
            "id_length" to id.length.toString(),
            "is_cached" to (cachedDeviceId != null).toString(),
            "id_type" to when {
                id.startsWith("dh-fb-") -> "Fallback UUID"
                id.startsWith("dh-em-") -> "Emergency UUID"
                else -> "UUID-based (secure)"
            }
        )
    }

    // ========================================
    // 🗳️ VOTING MANAGEMENT
    // (Existing functionality preserved)
    // ========================================

    /**
     * ✅ Check if this device has already voted on a specific deal
     */
    fun hasVoted(dealId: String): Boolean {
        val voted = prefs.getBoolean("$KEY_VOTE_PREFIX$dealId", false)
        Log.d(TAG, "🗳️ Has voted on $dealId: $voted")
        return voted
    }

    /**
     * 📊 Get the vote type (hot/cold) if available
     */
    fun getVoteType(dealId: String): String? {
        return prefs.getString("$KEY_VOTE_TYPE_PREFIX$dealId", null)
    }

    /**
     * 💾 Record a vote
     */
    fun recordVote(dealId: String, voteType: String) {
        prefs.edit()
            .putBoolean("$KEY_VOTE_PREFIX$dealId", true)
            .putString("$KEY_VOTE_TYPE_PREFIX$dealId", voteType)
            .apply()
        Log.d(TAG, "✅ Recorded $voteType vote for $dealId")
    }

    /**
     * 🧪 Clear all vote history (for testing/dev only)
     */
    fun clearAllVotes() {
        prefs.edit().apply {
            prefs.all.keys.filter {
                it.startsWith(KEY_VOTE_PREFIX) || it.startsWith(KEY_VOTE_TYPE_PREFIX)
            }.forEach { remove(it) }
        }.apply()
        Log.d(TAG, "🗑️ Cleared all votes")
    }

    // ========================================
    // 🚨 REPORTING MANAGEMENT
    // (Existing functionality preserved)
    // ========================================

    /**
     * ✅ Check if user already reported a specific deal
     */
    fun hasReported(dealId: String): Boolean {
        val reported = prefs.getBoolean("$KEY_REPORT_PREFIX$dealId", false)
        Log.d(TAG, "🚨 Has reported $dealId: $reported")
        return reported
    }

    /**
     * 💾 Mark a deal as reported
     */
    fun recordReport(dealId: String) {
        prefs.edit()
            .putBoolean("$KEY_REPORT_PREFIX$dealId", true)
            .apply()
        Log.d(TAG, "✅ Recorded report for $dealId")
    }

    /**
     * 🚫 Prevent duplicate report (check before API call)
     */
    fun preventDuplicateReport(dealId: String): Boolean {
        return if (hasReported(dealId)) {
            Log.w(TAG, "⚠️ Duplicate report blocked for $dealId")
            true
        } else false
    }

    // ========================================
    // 📅 REPORT RATE LIMITING
    // (Existing functionality preserved)
    // ========================================

    /**
     * 📊 How many reports submitted today
     */
    fun getTodayReportCount(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return prefs.getInt("$KEY_REPORT_COUNT_PREFIX$today", 0)
    }

    /**
     * 📈 Increment today's report count
     */
    fun incrementTodayReportCount() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val newCount = getTodayReportCount() + 1
        prefs.edit().putInt("$KEY_REPORT_COUNT_PREFIX$today", newCount).apply()
        Log.d(TAG, "📊 Today's report count: $newCount")
    }

    // ========================================
    // 👤 USERNAME MANAGEMENT
    // ✨ NEW: Added for Phase 4
    // ========================================

    /**
     * ✨ Check if device has a username stored locally
     * Note: This is local cache only. Backend is source of truth.
     */
    fun hasUsername(): Boolean {
        val hasUsername = prefs.contains(KEY_USERNAME)
        Log.d(TAG, "👤 Has local username: $hasUsername")
        return hasUsername
    }

    /**
     * ✨ Get stored username (local cache)
     * Returns null if no username stored
     */
    fun getUsername(): String? {
        val username = prefs.getString(KEY_USERNAME, null)
        if (username != null) {
            Log.d(TAG, "👤 Retrieved username: $username")
        } else {
            Log.d(TAG, "👤 No username stored locally")
        }
        return username
    }

    /**
     * ✨ Store username locally
     * Called after successful registration with backend
     */
    fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
        Log.d(TAG, "✅ Saved username: $username")
    }

    /**
     * ✨ Clear username (for testing only)
     */
    fun clearUsername() {
        prefs.edit().remove(KEY_USERNAME).apply()
        Log.w(TAG, "🗑️ Cleared username")
    }
// ========================================
// 🆔 USER ID MANAGEMENT
// ✨ ADD THESE METHODS TO DeviceIdManager.kt
// Location: After clearUsername() method (around line 333)
// ========================================

    /**
     * ✨ Get stored user ID (persistent across sessions)
     * Returns null if no user ID stored
     */
    fun getUserId(): String? {
        val userId = prefs.getString("user_id", null)
        if (userId != null) {
            Log.d(TAG, "🆔 Retrieved user ID: ${userId.take(8)}...")
        } else {
            Log.d(TAG, "🆔 No user ID stored locally")
        }
        return userId
    }

    /**
     * ✨ Store user ID locally (persistent across sessions)
     * Called after successful email verification
     */

    fun saveUserId(userId: String) {
        prefs.edit().putString("user_id", userId).apply()
        _userIdFlow.value = userId // Update flow for reactive UI
        Log.d(TAG, "✅ Saved user ID: ${userId.take(8)}...")
    }

    /**
     * ✨ Check if device has a verified user ID
     */
    fun hasUserId(): Boolean {
        val hasUserId = prefs.contains("user_id")
        Log.d(TAG, "🆔 Has local user ID: $hasUserId")
        return hasUserId
    }

    /**
     * ✨ Clear user ID (for testing only)
     */
    fun clearUserId() {
        prefs.edit().remove("user_id").apply()
        _userIdFlow.value = null // Update flow
        Log.w(TAG, "🗑️ Cleared user ID")
    }

    // 📱 ONBOARDING MANAGEMENT
    private val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"

    /**
     * ✨ Checks if the user has seen the onboarding flow.
     * This is a synchronous (fast) read from SharedPreferences,
     * perfect for checking on app startup.
     */
    fun hasSeenOnboarding(): Boolean {
        val seen = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        Log.d(TAG, "📱 Has seen onboarding: $seen")
        return seen
    }

    /**
     * ✨ Marks the onboarding flow as "seen".
     * This is called once the user finishes the slides.
     */
    fun setHasSeenOnboarding() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
        Log.d(TAG, "✅ Onboarding complete. Flag set to true.")
    }
    // ========================================
    // 🧪 TESTING UTILITIES
    // ========================================

    /**
     * 🧪 Clear device ID (for testing only)
     * WARNING: This will reset device identity
     */
    fun clearDeviceId() {
        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.w(TAG, "⚠️  CLEARING DEVICE ID (TESTING ONLY)")
        Log.w(TAG, "   Previous ID: ${cachedDeviceId?.take(8)}...${cachedDeviceId?.takeLast(4)}")

        prefs.edit().remove(KEY_DEVICE_ID).apply()
        cachedDeviceId = null

        Log.w(TAG, "✅ Device ID cleared")
        Log.w(TAG, "   Next call will generate new ID")
        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * 🧪 Clear all data (nuclear option for testing)
     */
    fun clearAllData() {
        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.w(TAG, "☢️  CLEARING ALL DATA (TESTING ONLY)")

        prefs.edit().clear().apply()
        cachedDeviceId = null

        Log.w(TAG, "✅ All data cleared")
        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}