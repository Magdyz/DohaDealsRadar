package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * ========================================
 * ✅ UPDATED: Vote Request (User-Authenticated Voting)
 * ========================================
 *
 * Migration from device-based to user-based voting.
 *
 * PRIORITY:
 * 1. user_id (authenticated user - PREFERRED)
 * 2. user_email (alternative, backend looks up user_id)
 * 3. device_id (legacy support only)
 *
 * BACKWARD COMPATIBILITY:
 * - Old clients can still send device_id only
 * - New clients MUST send user_id for authenticated votes
 * - Backend prioritizes user_id over device_id
 *
 * Created: 2025-11-19
 */
data class VoteRequest(
    val deal_id: String,
    val vote_type: String,            // "hot" or "cold"
    val user_id: String? = null,      // ✅ NEW: Authenticated user UUID (PREFERRED)
    val user_email: String? = null,   // ✅ NEW: Alternative to user_id
    val device_id: String? = null     // ✅ UPDATED: Now optional (legacy support)
)