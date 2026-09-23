package eg.deals.radar.network

import com.google.gson.annotations.SerializedName

/**
 * ========================================
 * ✨ ADMIN-ONLY DTOs (Feedback / User Management / Audit Log)
 * ========================================
 * Backed by the admin_feedback, admin_users, admin_audit_log and
 * update_user_role Edge Functions. Admin UI is English-only.
 *
 * CREATED: 2025-11-27
 */

// ========================================
// 💬 FEEDBACK
// ========================================

data class FeedbackAdminDto(
    val id: String,
    @SerializedName("user_id") val userId: String? = null,
    val username: String? = null,
    @SerializedName("feedback_text") val feedbackText: String? = null,
    val email: String? = null,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("reviewed_by") val reviewedBy: String? = null,
    val notes: String? = null
)

data class AdminFeedbackListRequest(
    val action: String = "list",
    val status: String? = null,
    val page: Int = 1
)

data class AdminFeedbackUpdateRequest(
    val action: String = "update",
    @SerializedName("feedback_id") val feedbackId: String,
    val status: String,
    val notes: String? = null
)

// ========================================
// 👤 USER MANAGEMENT
// ========================================

data class AdminUserDto(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    val role: String = "user",
    @SerializedName("trust_level") val trustLevel: String? = null,
    @SerializedName("auto_approve") val autoApprove: Boolean = false,
    val strikes: Int = 0,
    @SerializedName("approved_deals_count") val approvedDealsCount: Int = 0,
    @SerializedName("rejected_deals_count") val rejectedDealsCount: Int = 0,
    @SerializedName("banned_at") val bannedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_login_at") val lastLoginAt: String? = null
)

data class AdminUsersListRequest(
    val action: String = "list",
    val q: String? = null,
    val filter: String? = null,
    val page: Int = 1
)

/**
 * Shared request body for admin_users actions other than "list":
 * ban, unban, set_auto_approve, reset_strikes. Unused fields are omitted
 * from the JSON body (Gson skips nulls by default).
 */
data class AdminUserActionRequest(
    val action: String,
    @SerializedName("target_user_id") val targetUserId: String,
    val reason: String? = null,
    val value: Boolean? = null
)

// ========================================
// 🛡️ ROLE CHANGE (update_user_role)
// ========================================

data class UpdateUserRoleRequest(
    @SerializedName("target_user_id") val targetUserId: String,
    @SerializedName("new_role") val newRole: String
)

/** update_user_role does not return a `data` row, just old/new role strings. */
data class UpdateUserRoleResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val code: String? = null,
    @SerializedName("old_role") val oldRole: String? = null,
    @SerializedName("new_role") val newRole: String? = null
)

// ========================================
// 📜 AUDIT LOG
// ========================================

data class AdminAuditLogEntryDto(
    val id: String,
    @SerializedName("action_type") val actionType: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("actor_username") val actorUsername: String? = null,
    @SerializedName("target_user_id") val targetUserId: String? = null,
    @SerializedName("target_username") val targetUsername: String? = null,
    @SerializedName("deal_id") val dealId: String? = null,
    @SerializedName("deal_title") val dealTitle: String? = null,
    @SerializedName("old_value") val oldValue: String? = null,
    @SerializedName("new_value") val newValue: String? = null,
    val reason: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AdminAuditLogRequest(
    val category: String? = null,
    val page: Int = 1
)
