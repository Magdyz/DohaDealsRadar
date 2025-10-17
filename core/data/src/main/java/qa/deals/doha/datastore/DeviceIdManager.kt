package qa.deals.doha.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ✅ DeviceIdManager — 2025 edition
 *
 * - Generates and stores a unique device ID.
 * - Tracks vote state per deal (hot / cold).
 * - Tracks report state per deal to prevent duplicate submissions.
 * - ✅ ENHANCED: Added daily report count tracking for rate limiting
 * - Persists everything in SharedPreferences (safe across restarts).
 *
 * 📌 Singleton: Use [DeviceIdManager.getInstance(context)] to access.
 */
class DeviceIdManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_FILE,
        Context.MODE_PRIVATE
    )

    // Generate device ID once and reuse forever
    private val deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        Log.d(TAG, "🆔 Generated new device ID: $it")
    }

    companion object {
        private const val PREFS_FILE = "device_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_VOTE_PREFIX = "vote_"
        private const val KEY_VOTE_TYPE_PREFIX = "vote_type_"
        private const val KEY_REPORT_PREFIX = "report_"
        private const val KEY_REPORT_COUNT_PREFIX = "report_count_"

        private const val TAG = "DeviceIdManager"

        @Volatile
        private var INSTANCE: DeviceIdManager? = null

        fun getInstance(context: Context): DeviceIdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceIdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /** 🔑 Get the unique device ID (used in all API calls) */
    fun getDeviceId(): String = deviceId

    // ---------------------------
    // 🗳️ Voting management
    // ---------------------------

    /** ✅ Check if this device has already voted on a specific deal */
    fun hasVoted(dealId: String): Boolean {
        val voted = prefs.getBoolean("$KEY_VOTE_PREFIX$dealId", false)
        Log.d(TAG, "🗳️ Has voted on $dealId: $voted")
        return voted
    }

    /** 📊 Get the vote type (hot/cold) if available */
    fun getVoteType(dealId: String): String? {
        return prefs.getString("$KEY_VOTE_TYPE_PREFIX$dealId", null)
    }

    /** 💾 Record a vote */
    fun recordVote(dealId: String, voteType: String) {
        prefs.edit()
            .putBoolean("$KEY_VOTE_PREFIX$dealId", true)
            .putString("$KEY_VOTE_TYPE_PREFIX$dealId", voteType)
            .apply()
        Log.d(TAG, "✅ Recorded $voteType vote for $dealId")
    }

    /** 🧪 Clear all vote history (for testing/dev only) */
    fun clearAllVotes() {
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(KEY_VOTE_PREFIX) || it.startsWith(KEY_VOTE_TYPE_PREFIX) }
                .forEach { remove(it) }
        }.apply()
        Log.d(TAG, "🗑️ Cleared all votes")
    }

    // ---------------------------
    // 🚨 Reporting management
    // ---------------------------

    /**
     * ✅ Check if user already reported a specific deal.
     * This value is persisted — if the user reported yesterday, it's still true today.
     */
    fun hasReported(dealId: String): Boolean {
        val reported = prefs.getBoolean("$KEY_REPORT_PREFIX$dealId", false)
        Log.d(TAG, "🚨 Has reported $dealId: $reported")
        return reported
    }

    /**
     * 💾 Mark a deal as reported.
     * ⚠️ This should be called **only after a successful API response**.
     * ✅ ALIAS: Same as recordReport() for consistency
     */
    fun recordReport(dealId: String) {
        prefs.edit()
            .putBoolean("$KEY_REPORT_PREFIX$dealId", true)
            .apply()
        Log.d(TAG, "✅ Recorded report for $dealId")
    }

    /**
     * 🚫 Optional safety: prevent sending a duplicate report **before network call**
     * Use this in your ViewModel:
     *
     * ```kotlin
     * if (deviceIdManager.preventDuplicateReport(dealId)) {
     *     uiState = uiState.copy(error = "🚫 Already reported this deal")
     *     return@launch
     * }
     * ```
     */
    fun preventDuplicateReport(dealId: String): Boolean {
        return if (hasReported(dealId)) {
            Log.w(TAG, "⚠️ Duplicate report blocked for $dealId")
            true
        } else false
    }

    // ---------------------------
    // 📅 Report rate limiting
    // ✅ ENHANCED: Added daily report tracking
    // ---------------------------

    /**
     * 📊 How many reports submitted today
     * ✅ ALIAS: Same as getTodayReportCount() for consistency
     */
    fun getTodayReportCount(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return prefs.getInt("$KEY_REPORT_COUNT_PREFIX$today", 0)
    }

    /**
     * 📈 Increment today's report count
     * ✅ ALIAS: Same as incrementTodayReportCount() for consistency
     */
    fun incrementTodayReportCount() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val newCount = getTodayReportCount() + 1
        prefs.edit().putInt("$KEY_REPORT_COUNT_PREFIX$today", newCount).apply()
        Log.d(TAG, "📊 Today's report count: $newCount")
    }
}