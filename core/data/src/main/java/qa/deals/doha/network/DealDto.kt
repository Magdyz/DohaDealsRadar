package qa.deals.doha.network

import com.google.gson.annotations.SerializedName
import qa.deals.doha.db.DealEntity

/**
 * Mirrors the JSON row returned from submit_deal (and future feeds).
 * ✨ UPDATED: Added category and promo_code fields
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
    val category: String? = "other",        // ✨ CATEGORY CHANGE: Added category field
    @SerializedName("promo_code") val promoCode: String? = null  // ✨ CATEGORY CHANGE: Added promo_code field
)

/**
 * ✅ Extension function to convert DealDto → DealEntity
 * ✨ UPDATED: Now includes category mapping
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
        category = this.category ?: "other"  // ✨ CATEGORY CHANGE: Map category to entity
    )
}