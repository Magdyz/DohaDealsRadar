package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to approve a pending deal
 */
data class ApproveDealRequest(
    @SerializedName("moderator_user_id")
    val userId: String,

    @SerializedName("deal_id")
    val dealId: String
)
