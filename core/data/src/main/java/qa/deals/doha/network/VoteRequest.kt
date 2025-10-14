package qa.deals.doha.network

/**
 * Request body for casting a vote
 */
data class VoteRequest(
    val deal_id: String,
    val vote_type: String,  // "hot" or "cold"
    val device_id: String
)