package eg.deals.radar.network

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

    // ✅ NEW: machine-readable error code (see ApiErrors). The server already
    // sends this on every failure envelope; capturing it lets callers build a
    // translated message instead of showing the raw `error` text.
    @SerializedName("code")
    val code: String? = null,

    @SerializedName("data")
    val data: DealDto? = null
)
