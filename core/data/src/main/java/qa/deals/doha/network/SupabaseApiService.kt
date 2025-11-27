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
     * Get all approved deals with pagination, sorting, and category filtering
     * ✅ UPDATED: Added pagination support (2025-10-24)
     * ✅ UPDATED: Added sorting support (2025-11-26)
     * ✅ UPDATED: Added category filtering support (2025-11-27)
     * @param page Page number (default: 1)
     * @param limit Items per page (default: 20, max: 50)
     * @param sortBy Sort option: "hottest" (default) or "newest"
     * @param category Category filter (optional): "food_dining", "shopping_fashion", etc.
     *                 - If null/empty → returns all categories
     *                 - If specific category → filters to that category only
     */
    @GET("get_deals")
    suspend fun getDeals(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sort_by") sortBy: String = "hottest",
        @Query("category") category: String? = null
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



    // ========================================

    // ✅ MODERATOR & ADMIN ENDPOINTS

    // ========================================



    /**

     * Get pending deals (moderator/admin only)

     * @param request Contains user_id, page, limit

     * @return List of pending deals with pagination

     */

    @POST("get_pending_deals")

    suspend fun getPendingDeals(

        @Body request: GetPendingDealsRequest

    ): ApiEnvelope<List<DealDto>>



    /**

     * Approve a pending deal (moderator/admin only)

     * @param request Contains moderator_user_id and deal_id

     * @return Updated deal with approved status

     */

    @POST("approve_deal")

    suspend fun approveDeal(

        @Body request: ApproveDealRequest

    ): ModeratorActionResponse



    /**

     * Soft delete a deal (moderator/admin can delete any, users can delete own)

     * @param request Contains moderator_user_id, deal_id, reason

     * @return Updated deal with deleted_at timestamp

     */

    @POST("delete_deal")

    suspend fun deleteDeal(

        @Body request: DeleteDealRequest

    ): ModeratorActionResponse



    /**

     * Reject a pending deal (moderator/admin only)

     * @param request Contains moderator_user_id, deal_id, reason

     * @return Updated deal with rejected status

     */

    @POST("reject_deal")

    suspend fun rejectDeal(

        @Body request: RejectDealRequest

    ): ModeratorActionResponse



    /**

     * Get all deals by a specific user

     * @param request Contains user_id (caller), target_user_id (optional), page, limit

     * @return List of deals submitted by the user

     */

    @POST("get_user_deals")

    suspend fun getUserDeals(

        @Body request: GetUserDealsRequest

    ): ApiEnvelope<List<DealDto>>



    /**

     * Get user profile by ID

     * @param request Contains user_id

     * @return User profile data

     */

    @POST("get_user_profile")

    suspend fun getUserProfile(

        @Body request: GetUserProfileRequest

    ): ApiEnvelope<UserDto>

    /**
     * Return an archived deal back to feed (admin only)
     * - Un-archives the deal (isArchived = false)
     * - Extends expiry by 10 days from now
     * - Keeps original createdAt (for real age display)
     *
     * @param request Contains admin_user_id and deal_id
     * @return Updated deal
     */

    @POST("return_to_feed")
    suspend fun returnDealToFeed(
        @Body request: ReturnToFeedRequest
    ): ModeratorActionResponse

    /**
     * Permanently delete a deal and its image from database (admin only)
     * - Deletes the deal record from database
     * - Deletes the image file from Supabase storage
     * - Cannot be undone
     *
     * @param request Contains admin_user_id and deal_id
     * @return Success response
     */

    @POST("permanent_delete_deal")
    suspend fun permanentDeleteDeal(
        @Body request: PermanentDeleteDealRequest
    ): ModeratorActionResponse

    /**
     * Get all submitted reports with details (moderator/admin only)
     * Returns reports with joined deal and user information
     *
     * CREATED: 2025-11-22
     * @param request Contains user_id, page, limit
     * @return List of reports with full context
     */
    @POST("get_reports")
    suspend fun getReports(
        @Body request: GetReportsRequest
    ): ApiEnvelope<List<ReportWithDetailsDto>>

    /**
     * Dismiss a report without taking action (moderator/admin only)
     * Marks the report as reviewed but no action needed
     *
     * CREATED: 2025-11-22
     * @param request Contains report_id, user_id, reason
     * @return Success response
     */
    @POST("dismiss_report")
    suspend fun dismissReport(
        @Body request: DismissReportRequest
    ): ModeratorActionResponse

    /**
     * Resolve a report with action (moderator/admin only)
     * Takes action on a report (e.g., delete deal, warn user)
     *
     * CREATED: 2025-11-22
     * @param request Contains report_id, user_id, action, reason
     * @return Success response
     */
    @POST("resolve_report")
    suspend fun resolveReport(
        @Body request: ResolveReportRequest
    ): ModeratorActionResponse

    /**
     * Submit user feedback
     * Allows users to submit feedback and suggestions
     *
     * CREATED: 2025-11-22
     * @param request Contains device_id, feedback_text, user_id (optional)
     * @return Success response with feedback ID
     */
    @POST("submit_feedback")
    suspend fun submitFeedback(
        @Body request: SubmitFeedbackRequest
    ): ApiEnvelope<FeedbackData>
}