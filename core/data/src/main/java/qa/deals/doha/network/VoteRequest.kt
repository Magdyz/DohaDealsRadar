package qa.deals.doha.network

/**
 * Request body for casting a vote
 * Updated: 2025-11-19 - Changed from device_id to user_id for authenticated voting
 */
data class VoteRequest(
    val deal_id: String,
    val vote_type: String,  // "hot" or "cold"
    val user_id: String     // User ID (requires authentication)
)