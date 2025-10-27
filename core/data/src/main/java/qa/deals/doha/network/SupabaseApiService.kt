package qa.deals.doha.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ========================================
// ✅ Email Verification DTOs
// ========================================

data class SendCodeRequest(
    val email: String
)

data class SendCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val email: String? = null
)

data class VerifyCodeRequest(
    val email: String,
    val code: String,
    @SerializedName("device_id") val deviceId: String
)

data class UserInfo(
    val id: String,
    val email: String,
    val username: String,
    @SerializedName("is_new") val isNew: Boolean
)

data class VerifyCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val user: UserInfo? = null
)

data class UploadImageRequest(
    @SerializedName("filename") val filename: String,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("bytes_base64") val bytesBase64: String
)

data class ImageUploadResponse(
    @SerializedName("url") val url: String
)

/**
 * ========================================
 * ✅ UPDATED: Retrofit API service with pagination
 * ========================================
 */
interface SupabaseApiService {

    /**
     * Get all approved deals with pagination
     * ✅ UPDATED: Added pagination support (2025-10-24)
     * @param page Page number (default: 1)
     * @param limit Items per page (default: 20, max: 50)
     */
    @GET("get_deals")
    suspend fun getDeals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiEnvelope<List<DealDto>>

    /**
     * ========================================
     * ✅ SPRINT 2: Get archived deals with pagination
     * Returns deals that are older than 10 days (auto-archived by backend)
     * ========================================
     * @param page Page number (default: 1)
     * @param limit Items per page (default: 20, max: 50)
     * @return List of archived deals wrapped in ApiEnvelope
     */
    @GET("get_archived_deals")
    suspend fun getArchivedDeals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiEnvelope<List<DealDto>>

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
     */
    @POST("create_report")
    suspend fun reportDeal(
        @Body report: ReportRequest
    ): ApiEnvelope<List<ReportDto>>

    // ========================================
    // ✅ EMAIL VERIFICATION ENDPOINTS
    // ========================================

    @POST("send-verification-code")
    suspend fun sendVerificationCode(
        @Body request: SendCodeRequest
    ): SendCodeResponse

    @POST("verify-code-and-get-user")
    suspend fun verifyCodeAndGetUser(
        @Body request: VerifyCodeRequest
    ): VerifyCodeResponse

    // ========================================
    // ✅ USERNAME MANAGEMENT ENDPOINTS
    // ========================================

    @POST("manage_username")
    suspend fun getUsernameForDevice(
        @Body request: UsernameRequest
    ): UsernameResponse

    @POST("manage_username")
    suspend fun checkUsernameAvailability(
        @Body request: UsernameRequest
    ): UsernameResponse

    @POST("manage_username")
    suspend fun registerUsername(
        @Body request: UsernameRequest
    ): UsernameResponse
}