package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * ========================================
 * ✨ SUBMIT DEAL REQUEST DTO
 * Request body for creating new deals
 * ========================================
 *
 * UPDATED: 2025-10-18
 * - Added category field
 * - Added promo_code field
 * - Added posted_by field for username attribution
 *
 * @param title Deal title (required)
 * @param description Deal description (optional)
 * @param link Deal URL (required for online deals)
 * @param imageUrl Deal image URL (required)
 * @param location Physical location (required for physical deals)
 * @param category Deal category (defaults to "other")
 * @param promoCode Promo/coupon code (optional, for online deals)
 * @param postedBy Username of person posting (defaults to "Anonymous")
 */
data class SubmitDealRequest(
    val title: String,
    val description: String? = null,
    val link: String?,
    @SerializedName("image_url") val imageUrl: String,
    val location: String? = null,
    val category: String = "other",
    @SerializedName("promo_code") val promoCode: String? = null,
    @SerializedName("posted_by") val postedBy: String = "Anonymous"  // ✨ NEW: Username attribution
)