package qa.deals.doha.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ========================================
// ✅ NEW: Email Verification DTOs
// ========================================

/**
 * Request to send verification code
 */
data class SendCodeRequest(
    val email: String
)

/**
 * Response from send code
 */
data class SendCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val email: String? = null
)

/**
 * Request to verify code and get/create user
 */
data class VerifyCodeRequest(
    val email: String,
    val code: String,
    @SerializedName("device_id") val deviceId: String
)

/**
 * User info from verification
 */
data class UserInfo(
    val id: String,
    val email: String,
    val username: String,
    @SerializedName("is_new") val isNew: Boolean
)

/**
 * Response from verify code
 */
data class VerifyCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val user: UserInfo? = null
)

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
     */
    @POST("create_report")
    suspend fun reportDeal(
        @Body report: ReportRequest
    ): ApiEnvelope<List<ReportDto>>

    // ========================================
    // ✅ NEW: EMAIL VERIFICATION ENDPOINTS
    // ========================================

    /**
     * Send verification code to email
     */
    @POST("send-verification-code")
    suspend fun sendVerificationCode(
        @Body request: SendCodeRequest
    ): SendCodeResponse

    /**
     * Verify code and get/create user
     */
    @POST("verify-code-and-get-user")
    suspend fun verifyCodeAndGetUser(
        @Body request: VerifyCodeRequest
    ): VerifyCodeResponse

    // ========================================
    // ✨ EXISTING: USERNAME MANAGEMENT ENDPOINTS
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