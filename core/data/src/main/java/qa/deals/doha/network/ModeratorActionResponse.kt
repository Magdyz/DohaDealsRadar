package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Generic response for moderator actions (approve, reject, delete)
 */
data class ModeratorActionResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("data")
    val data: DealDto? = null
)
