package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to approve a pending deal
 */
data class ApproveDealRequest(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("deal_id")
    val dealId: String
)
