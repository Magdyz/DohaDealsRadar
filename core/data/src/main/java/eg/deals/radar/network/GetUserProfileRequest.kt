package eg.deals.radar.network

import com.google.gson.annotations.SerializedName

/**
 * Request to get user profile
 */
data class GetUserProfileRequest(
    @SerializedName("user_id")
    val userId: String
)
