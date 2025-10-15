package qa.deals.doha.network

/**
 * Request body for updating a deal's image URL.
 * Used in the second stage of the two-stage upload process.
 */
data class UpdateImageRequest(
    val deal_id: String,
    val image_url: String
)