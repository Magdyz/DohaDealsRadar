package eg.deals.radar.network

import com.google.gson.annotations.SerializedName

/**
 * Standard backend response.
 * On errors: success=false, `code` = machine-readable error (see ApiErrors),
 * `error` = English fallback text, plus optional details.
 */
data class ApiEnvelope<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val code: String? = null,
    val field: String? = null,
    @SerializedName("retry_after") val retryAfter: Int? = null,
    val limit: Int? = null,
    val data: T? = null,
    val pagination: PaginationMeta? = null,
    // get_user_deals page 1
    val stats: UserDealStatsDto? = null,
    // cast_vote: the caller's resulting vote ("hot" | "cold" | null)
    @SerializedName("user_vote") val userVote: String? = null,
    // submit_deal: "approved" | "pending"
    val status: String? = null,
    // DUPLICATE_DEAL / POSSIBLE_DUPLICATE details
    val existing: SimilarDealDto? = null,
    val similar: List<SimilarDealDto>? = null,
    // check_duplicate
    val duplicates: List<SimilarDealDto>? = null,
    val blocking: Boolean? = null,
    // mark_expired
    val archived: Boolean? = null,
    @SerializedName("expired_votes") val expiredVotes: Int? = null,
    @SerializedName("http_status") val httpStatus: Int? = null
)

data class PaginationMeta(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int? = null,
    val totalPages: Int? = null,
    val hasMore: Boolean = false,
    @SerializedName("next_cursor") val nextCursor: String? = null
)

data class UserDealStatsDto(
    val total: Int = 0,
    val approved: Int = 0,
    val pending: Int = 0,
    val rejected: Int = 0
)

/** A deal that looks like the one being posted. */
data class SimilarDealDto(
    val id: String,
    val title: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("match_type") val matchType: String?,
    val score: Double? = null
)
