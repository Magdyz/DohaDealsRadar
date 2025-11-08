package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to get deals by a specific user
 */
data class GetUserDealsRequest(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("target_user_id")
    val targetUserId: String? = null,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("limit")
    val limit: Int = 20
)
