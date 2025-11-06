package qa.deals.doha.network



import com.google.gson.annotations.SerializedName



// ========================================

// ✅ MODERATOR & ADMIN REQUEST/RESPONSE MODELS

// ========================================



/**

 * Request to get pending deals (moderator/admin only)

 */

data class GetPendingDealsRequest(

    @SerializedName("user_id") val userId: String,

    @SerializedName("page") val page: Int = 1,

    @SerializedName("limit") val limit: Int = 20

)



/**

 * Request to approve a pending deal (moderator/admin only)

 */

data class ApproveDealRequest(

    @SerializedName("moderator_user_id") val userId: String,

    @SerializedName("deal_id") val dealId: String

)



/**

 * Request to reject a pending deal (moderator/admin only)

 */

data class RejectDealRequest(

    @SerializedName("moderator_user_id") val userId: String,

    @SerializedName("deal_id") val dealId: String,

    @SerializedName("reason") val reason: String? = null

)



/**

 * Request to soft delete a deal

 */

data class DeleteDealRequest(

    @SerializedName("moderator_user_id") val userId: String,

    @SerializedName("deal_id") val dealId: String,

    @SerializedName("reason") val reason: String? = null

)



/**

 * Request to get deals by a specific user

 */

data class GetUserDealsRequest(

    @SerializedName("user_id") val userId: String,

    @SerializedName("target_user_id") val targetUserId: String? = null,

    @SerializedName("page") val page: Int = 1,

    @SerializedName("limit") val limit: Int = 20

)



/**

 * Request to get user profile

 */

data class GetUserProfileRequest(

    @SerializedName("user_id") val userId: String

)



/**

 * Response for moderator actions (approve, reject, delete)

 * Contains the updated deal data

 */

data class ModeratorActionResponse(

    @SerializedName("success") val success: Boolean,

    @SerializedName("message") val message: String? = null,

    @SerializedName("data") val data: DealDto? = null,

    @SerializedName("error") val error: String? = null

)



/**

 * User DTO for profile data

 */

data class UserDto(

    @SerializedName("id") val id: String,

    @SerializedName("email") val email: String? = null,

    @SerializedName("username") val username: String? = null,

    @SerializedName("role") val role: String = "user",

    @SerializedName("auto_approve") val autoApprove: Boolean = false,

    @SerializedName("approved_deals_count") val approvedDealsCount: Int = 0,

    @SerializedName("created_at") val createdAt: String? = null

)

