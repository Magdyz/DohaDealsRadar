package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to return an archived deal back to feed
 * Admin-only endpoint that:
 * - Sets isArchived = false
 * - Extends expiresAt by 10 days from now
 * - Keeps original createdAt (real age)
 */

data class ReturnToFeedRequest(
    @SerializedName("admin_user_id")
    val userId: String,

    @SerializedName("deal_id")
    val dealId: String
)