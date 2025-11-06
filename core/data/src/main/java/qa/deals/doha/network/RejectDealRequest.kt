package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to reject a pending deal
 */
data class RejectDealRequest(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("deal_id")
    val dealId: String,

    @SerializedName("reason")
    val reason: String? = null
)
