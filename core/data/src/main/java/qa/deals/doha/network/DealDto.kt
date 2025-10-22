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
    @SerializedName("hot_count") val hotCount: Int?,
    @SerializedName("cold_count") val coldCount: Int?,
    val location: String? = null,
    val category: String? = "other",
    @SerializedName("promo_code") val promoCode: String? = null,
    @SerializedName("posted_by") val postedBy: String? = "Anonymous",

    // ========================================
    // ✅ NEW: Added field for trust system
    // ========================================
    @SerializedName("auto_approved") val autoApproved: Boolean? = false
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
        hotCount = this.hotCount ?: 0,
        coldCount = this.coldCount ?: 0,
        description = this.description,
        location = this.location,
        category = this.category ?: "other",
        postedBy = this.postedBy ?: "Anonymous",
        autoApproved = this.autoApproved ?: false // ✅ NEW: Map autoApproved to entity
    )
}