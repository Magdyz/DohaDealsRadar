package qa.deals.doha.network

data class SubmitDealRequest(
    val title: String,
    val description: String? = null,
    val link: String?,
    val image_url: String,
    val location: String? = null,
    val category: String = "other",      // ✨ CATEGORY CHANGE: Added category field
    val promo_code: String? = null       // ✨ CATEGORY CHANGE: Added promo_code field


)