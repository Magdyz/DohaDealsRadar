package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * ========================================
 * ✨ REPORT WITH DETAILS DATA TRANSFER OBJECT
 * Extended report model with deal and user information
 * ========================================
 *
 * Used by moderators to view reports with full context
 * Includes data from reports, deals, and users tables joined together
 *
 * CREATED: 2025-11-22
 */
data class ReportWithDetailsDto(
    // ========================================
    // Report fields
    // ========================================
    val id: String?,
    @SerializedName("deal_id") val dealId: String?,
    @SerializedName("device_id") val deviceId: String?,
    val reason: String?,
    val note: String?,
    @SerializedName("created_at") val createdAt: String?,

    // ========================================
    // Deal information (joined from deals table)
    // ========================================
    @SerializedName("deal_title") val dealTitle: String?,
    @SerializedName("deal_image") val dealImage: String?,
    @SerializedName("deal_category") val dealCategory: String?,
    @SerializedName("deal_status") val dealStatus: String?,
    @SerializedName("deal_posted_by") val dealPostedBy: String?,

    // ========================================
    // Reporter information (joined from users table)
    // ========================================
    @SerializedName("reporter_username") val reporterUsername: String?,
    @SerializedName("reporter_email") val reporterEmail: String?,
    @SerializedName("reporter_role") val reporterRole: String?,
    @SerializedName("reporter_approved_deals_count") val reporterApprovedDealsCount: Int?
)
