package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Response from report submission
 */
data class ReportDto(
    val id: String?,
    @SerializedName("deal_id") val dealId: String?,
    @SerializedName("device_id") val deviceId: String?,
    val reason: String?,
    val note: String?,
    @SerializedName("created_at") val createdAt: String?
)