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
    @SerializedName("posted_by") val postedBy: String? = "Anonymous"  // ✨ NEW: Username attribution
)

/**
 * ========================================
 * ✨ EXTENSION: Convert DTO to Entity
 * Maps API response to local database model
 * ========================================
 *
 * UPDATED: 2025-10-18
 * - Now includes postedBy mapping
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
        postedBy = this.postedBy ?: "Anonymous"  // ✨ NEW: Map username to entity
    )
}