package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to soft delete a deal
 */
data class DeleteDealRequest(
    @SerializedName("moderator_user_id")
    val userId: String,

    @SerializedName("deal_id")
    val dealId: String,

    @SerializedName("reason")
    val reason: String? = null
)
