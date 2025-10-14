package qa.deals.doha.network

data class SubmitDealRequest(
    val title: String,
    val description: String? = null,
    val link: String?,
    val image_url: String,
    val location: String? = null

)