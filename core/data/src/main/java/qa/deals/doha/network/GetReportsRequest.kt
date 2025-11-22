package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to get all submitted reports with pagination
 *
 * CREATED: 2025-11-22
 */
data class GetReportsRequest(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("limit")
    val limit: Int = 20
)
