package eg.deals.radar.network

/** get_deals?bundle=categories: page 1 of every category in one response (instant tabs). */
data class FeedBundleResponse(
    val success: Boolean? = null,
    val code: String? = null,
    val error: String? = null,
    val bundle: List<CategoryPageDto>? = null
)

data class CategoryPageDto(
    val category: String,
    val data: List<DealDto>? = null,
    val pagination: PaginationMeta? = null
)
