package qa.deals.doha.network

import com.google.gson.annotations.SerializedName
import qa.deals.doha.db.DealEntity

/**
 * ========================================
 * ✨ DEAL DATA TRANSFER OBJECT
 * Mirrors the JSON returned from Supabase API
 * ========================================
 *
 * UPDATED: 2025-10-18
 * - Added category field
 * - Added promoCode field
 * - Added postedBy field for username attribution
 *
 * ✅ UPDATED: 2025-10-23
 * - Added autoApproved field for trust system
 */
data class DealDto(
    val id: String?,
    val title: String?,
    val link: String?,
    val description: String? = null,
    @SerializedName("image_url") val imageUrl: String?,
    val status: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("hot_count") val hotCount: Int?,
    @SerializedName("cold_count") val coldCount: Int?,
    val location: String? = null,
    val category: String? = "other",
    @SerializedName("promo_code") val promoCode: String? = null,
    @SerializedName("posted_by") val postedBy: String? = "Anonymous",

    // ========================================
    // ✅ NEW: Added field for trust system
    // ========================================
    @SerializedName("auto_approved") val autoApproved: Boolean? = false,

    // ========================================
    // ✅ SPRINT 5: User tracking fields
    // ========================================

    @SerializedName("submitted_by_user_id") val submittedByUserId: String? = null,
    @SerializedName("approved_by") val approvedBy: String? = null,
    @SerializedName("approved_at") val approvedAt: String? = null,
    @SerializedName("report_count") val reportCount: Int? = 0,
    @SerializedName("deleted_at") val deletedAt: String? = null,
    @SerializedName("deleted_by") val deletedBy: String? = null,
    @SerializedName("deletion_reason") val deletionReason: String? = null,
    // ========================================
    // ✅ NEW: Rejection fields (separate from deletion)
    // ========================================
    @SerializedName("rejection_reason") val rejectionReason: String? = null,
    @SerializedName("rejected_at") val rejectedAt: String? = null,
    @SerializedName("rejected_by") val rejectedBy: String? = null,
    // ========================================
    @SerializedName("is_archived") val isArchived: Boolean? = false
)

/**
 * ========================================
 * ✨ EXTENSION: Convert DTO to Entity
 * Maps API response to local database model
 * ========================================
 *
 * UPDATED: 2025-10-18
 * - Now includes postedBy mapping
 *
 * ✅ UPDATED: 2025-10-23
 * - Now includes autoApproved mapping
 *
 * ⚠️ WARNING: This requires `DealEntity` to also
 * have an `autoApproved` field.
 */
fun DealDto.toEntity(): DealEntity {
    return DealEntity(
        id = this.id ?: "",
        title = this.title ?: "",
        link = this.link ?: "",
        imageUrl = this.imageUrl,
        status = this.status,
        createdAt = this.createdAt,
        expiresAt = this.expiresAt,
        hotCount = this.hotCount ?: 0,
        coldCount = this.coldCount ?: 0,
        description = this.description,
        location = this.location,
        category = this.category ?: "other",
        postedBy = this.postedBy ?: "Anonymous",
        autoApproved = this.autoApproved ?: false,
        promoCode = this.promoCode,

        // ========================================
        // ✅ SPRINT 2: Map isArchived from API to Entity
        // If backend doesn't send it, default to false (active)
        // ========================================

        isArchived = this.isArchived ?: false,

        // ========================================
        // ✅ SPRINT 5: Map user tracking fields
        // ========================================
        submittedByUserId = this.submittedByUserId,
        approvedBy = this.approvedBy,
        approvedAt = this.approvedAt,
        reportCount = this.reportCount ?: 0,
        deletedAt = this.deletedAt,
        deletedBy = this.deletedBy,
        deletionReason = this.deletionReason,
        // ✅ REUSE deletion_reason for rejection display
        // Since we're using the same DB field for both rejections and deletions,
        // map it to rejectionReason for UI display
        rejectionReason = this.deletionReason,
        rejectedAt = this.rejectedAt,
        rejectedBy = this.rejectedBy
    )
}