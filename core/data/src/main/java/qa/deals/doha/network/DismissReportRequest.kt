package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to dismiss a report without taking action
 * Used when moderator determines the report is invalid or doesn't require action
 *
 * CREATED: 2025-11-22
 */
data class DismissReportRequest(
    @SerializedName("report_id")
    val reportId: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("reason")
    val reason: String? = null
)
