package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to resolve a report with action
 * Used when moderator takes action on a report (e.g., delete deal, warn user)
 *
 * CREATED: 2025-11-22
 */
data class ResolveReportRequest(
    @SerializedName("report_id")
    val reportId: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("action")
    val action: String,  // "delete_deal", "warn_user", etc.

    @SerializedName("reason")
    val reason: String? = null
)
