package qa.deals.doha.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ---------- ADD: Upload request/response ----------
data class UploadImageRequest(
    @SerializedName("filename") val filename: String,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("bytes_base64") val bytesBase64: String
)

data class ImageUploadResponse(
    @SerializedName("url") val url: String
)

/**
 * Retrofit API service for all deal-related endpoints
 */
interface SupabaseApiService {

    /**
     * Get all approved deals
     */
    @GET("get_deals")
    suspend fun getDeals(): ApiEnvelope<List<DealDto>>

    /**
     * Submit a new deal
     */
    @POST("submit_deal")
    suspend fun submitDeal(
        @Body deal: SubmitDealRequest
    ): ApiEnvelope<List<DealDto>>

    /**
     * Cast a vote on a deal
     */
    @POST("cast_vote")
    suspend fun castVote(
        @Body vote: VoteRequest
    ): ApiEnvelope<DealDto>

    /**
     * Report a deal
     * Backend returns array even for single report
     */
    @POST("create_report")
    suspend fun reportDeal(
        @Body report: ReportRequest
    ): ApiEnvelope<List<ReportDto>>  // ✅ Changed from Unit to List<ReportDto>
}