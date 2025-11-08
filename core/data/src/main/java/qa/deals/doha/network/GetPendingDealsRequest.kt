package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to get pending deals
 */
data class GetPendingDealsRequest(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("limit")
    val limit: Int = 20
)
