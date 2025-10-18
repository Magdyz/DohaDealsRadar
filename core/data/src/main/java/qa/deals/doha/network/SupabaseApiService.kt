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

    @POST("update-deal-image")
    suspend fun updateDealImage(
        @Body request: UpdateImageRequest
    ): ApiEnvelope<DealDto>

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

    // ========================================
    // ✨ NEW: USERNAME MANAGEMENT ENDPOINTS
    // Added: 2025-10-18 by @Magdyz
    // ========================================

    /**
     * ✨ Get username for device
     *
     * Checks if device already has a registered username.
     * Returns username if exists, or indicates device needs to register.
     *
     * @param request UsernameRequest with action="get_username" and device_id
     * @return UsernameResponse with exists=true/false and username if found
     */
    @POST("manage_username")
    suspend fun getUsernameForDevice(
        @Body request: UsernameRequest
    ): UsernameResponse

    /**
     * ✨ Check username availability
     *
     * Validates if a username is available for registration.
     * Also performs format validation (3-20 chars, alphanumeric + underscore).
     *
     * @param request UsernameRequest with action="check_availability" and username
     * @return UsernameResponse with available=true/false
     */
    @POST("manage_username")
    suspend fun checkUsernameAvailability(
        @Body request: UsernameRequest
    ): UsernameResponse

    /**
     * ✨ Register new username
     *
     * Associates a username with a device ID.
     * Username must be unique and pass validation.
     *
     * @param request UsernameRequest with action="register_username", device_id, and username
     * @return UsernameResponse with success status and registered username
     */
    @POST("manage_username")
    suspend fun registerUsername(
        @Body request: UsernameRequest
    ): UsernameResponse
}