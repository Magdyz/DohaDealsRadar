package eg.deals.radar.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ========================================
// ✅ Email Verification DTOs
// ========================================

data class GoogleSignInRequest(
    @SerializedName("id_token") val idToken: String,
    /** Raw nonce; Google carries its SHA-256 inside the token. */
    val nonce: String?,
    @SerializedName("device_id") val deviceId: String,
    /** User accepted the privacy policy (required to create a new account). */
    val consent: Boolean = false
)

data class UserInfo(
    val id: String,
    /** Null for Google accounts: we deliberately don't store the email. */
    val email: String? = null,
    val username: String,
    @SerializedName("is_new") val isNew: Boolean,
    val role: String? = "user",
    @SerializedName("auto_approve") val autoApprove: Boolean? = false
)

data class SessionDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_at") val expiresAt: Long? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null
)

/** Response of sign_in_with_google: session + app profile. */
data class VerifyCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val user: UserInfo? = null,
    val session: SessionDto? = null,
    val code: String? = null,
    val field: String? = null,
    @SerializedName("retry_after") val retryAfter: Int? = null
)

// ========================================
// ✨ Egypt 2.0 DTOs
// ========================================

data class UploadUrlRequest(@SerializedName("content_type") val contentType: String = "image/jpeg")

data class UploadUrlResponse(
    val success: Boolean? = null,
    val code: String? = null,
    val error: String? = null,
    @SerializedName("upload_url") val uploadUrl: String? = null,
    @SerializedName("public_url") val publicUrl: String? = null,
    @SerializedName("content_type") val contentType: String? = null,
    @SerializedName("retry_after") val retryAfter: Int? = null
)

data class DuplicateCheckRequest(
    val link: String?,
    val title: String?,
    @SerializedName("image_hash") val imageHash: String?
)

data class LinkPreviewRequest(val link: String)

data class LinkPreviewDto(
    val title: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val price: Double? = null,
    val store: String? = null,
    @SerializedName("canonical_url") val canonicalUrl: String? = null
)

data class LinkPreviewResponse(
    val success: Boolean? = null,
    val preview: LinkPreviewDto? = null,
    val code: String? = null
)

data class DealIdRequest(@SerializedName("deal_id") val dealId: String)

data class DeleteAccountRequest(val confirm: String = "DELETE")

data class NamedCountDto(val name: String, val count: Int)

data class StatsDto(
    @SerializedName("live_deals") val liveDeals: Int = 0,
    @SerializedName("pending_review") val pendingReview: Int = 0,
    @SerializedName("hidden_by_reports") val hiddenByReports: Int = 0,
    @SerializedName("posted_24h") val posted24h: Int = 0,
    @SerializedName("posted_7d") val posted7d: Int = 0,
    @SerializedName("approved_7d") val approved7d: Int = 0,
    @SerializedName("rejected_7d") val rejected7d: Int = 0,
    @SerializedName("users_total") val usersTotal: Int = 0,
    @SerializedName("new_users_7d") val newUsers7d: Int = 0,
    @SerializedName("votes_7d") val votes7d: Int = 0,
    @SerializedName("open_reports") val openReports: Int = 0,
    @SerializedName("top_categories_7d") val topCategories7d: List<NamedCountDto> = emptyList(),
    @SerializedName("top_governorates_7d") val topGovernorates7d: List<NamedCountDto> = emptyList(),
    @SerializedName("generated_at") val generatedAt: String? = null
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
        @Query("category") category: String? = null,
        @Query("governorate") governorate: String? = null,
        @Query("q") query: String? = null,
        @Query("cursor") cursor: String? = null
    ): ApiEnvelope<List<DealDto>>

    // ========================================
    // ✨ Egypt 2.0 endpoints
    // ========================================

    @POST("create_upload_url")
    suspend fun createUploadUrl(@Body request: UploadUrlRequest): UploadUrlResponse

    @POST("check_duplicate")
    suspend fun checkDuplicate(@Body request: DuplicateCheckRequest): ApiEnvelope<Unit>

    @POST("link_preview")
    suspend fun linkPreview(@Body request: LinkPreviewRequest): LinkPreviewResponse

    @POST("mark_expired")
    suspend fun markExpired(@Body request: DealIdRequest): ApiEnvelope<Unit>

    @POST("delete_account")
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): ApiEnvelope<Unit>

    @POST("export_my_data")
    suspend fun exportMyData(@Body body: Map<String, String>): ApiEnvelope<Map<String, Any?>>

    @POST("get_stats")
    suspend fun getStats(@Body body: Map<String, String>): ApiEnvelope<StatsDto>

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
    // 🔐 SIGN-IN ENDPOINT (Google)
    // ========================================

    @POST("sign_in_with_google")
    suspend fun signInWithGoogle(
        @Body request: GoogleSignInRequest
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