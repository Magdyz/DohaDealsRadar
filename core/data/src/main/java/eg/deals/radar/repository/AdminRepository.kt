package eg.deals.radar.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eg.deals.radar.network.*

/**
 * ========================================
 * 🛡️ ADMIN REPOSITORY
 * ========================================
 * Feedback inbox, user management, role changes and audit log.
 * Admin-only: every call relies on the server rejecting non-admins with
 * FORBIDDEN (no local caching, always live data).
 *
 * CREATED: 2025-11-27
 */

/** Generic page result: items for this page + pagination info. */
data class AdminPage<T>(
    val items: List<T>,
    val pagination: PaginationMeta?
)

class AdminRepository {

    private val api: SupabaseApiService = NetworkModule.api

    // ========================================
    // 💬 FEEDBACK
    // ========================================

    /**
     * List submitted feedback, optionally filtered by status.
     * @param status "pending" | "reviewed" | "resolved" | "archived" | null (all)
     */
    suspend fun listFeedback(
        status: String?,
        page: Int = 1
    ): Result<AdminPage<FeedbackAdminDto>> = withContext(Dispatchers.IO) {
        try {
            Log.d("AdminRepository", "💬 Listing feedback (status=$status, page=$page)")
            val response = api.adminFeedbackList(
                AdminFeedbackListRequest(status = status, page = page)
            )
            if (response.success == true && response.data != null) {
                Result.success(AdminPage(response.data, response.pagination))
            } else {
                Log.e("AdminRepository", "❌ Failed to list feedback: ${response.error}")
                Result.failure(Exception(ApiErrors.message(response)))
            }
        } catch (e: Exception) {
            Log.e("AdminRepository", "💥 Error listing feedback", e)
            Result.failure(Exception(ApiErrors.message(e), e))
        }
    }

    /** Update a feedback item's status and/or notes. Returns the updated row. */
    suspend fun updateFeedback(
        feedbackId: String,
        status: String,
        notes: String? = null
    ): Result<FeedbackAdminDto> = withContext(Dispatchers.IO) {
        try {
            Log.d("AdminRepository", "💬 Updating feedback $feedbackId -> $status")
            val response = api.adminFeedbackUpdate(
                AdminFeedbackUpdateRequest(feedbackId = feedbackId, status = status, notes = notes)
            )
            if (response.success == true && response.data != null) {
                Result.success(response.data)
            } else {
                Log.e("AdminRepository", "❌ Failed to update feedback: ${response.error}")
                Result.failure(Exception(ApiErrors.message(response)))
            }
        } catch (e: Exception) {
            Log.e("AdminRepository", "💥 Error updating feedback", e)
            Result.failure(Exception(ApiErrors.message(e), e))
        }
    }

    // ========================================
    // 👤 USER MANAGEMENT
    // ========================================

    /** Search/list/filter users. @param filter "all"|"moderators"|"admins"|"banned"|"trusted" */
    suspend fun listUsers(
        query: String?,
        filter: String?,
        page: Int = 1
    ): Result<AdminPage<AdminUserDto>> = withContext(Dispatchers.IO) {
        try {
            Log.d("AdminRepository", "👤 Listing users (q=$query, filter=$filter, page=$page)")
            val response = api.adminUsersList(
                AdminUsersListRequest(q = query, filter = filter, page = page)
            )
            if (response.success == true && response.data != null) {
                Result.success(AdminPage(response.data, response.pagination))
            } else {
                Log.e("AdminRepository", "❌ Failed to list users: ${response.error}")
                Result.failure(Exception(ApiErrors.message(response)))
            }
        } catch (e: Exception) {
            Log.e("AdminRepository", "💥 Error listing users", e)
            Result.failure(Exception(ApiErrors.message(e), e))
        }
    }

    suspend fun banUser(targetUserId: String, reason: String? = null): Result<AdminUserDto> =
        userAction(AdminUserActionRequest(action = "ban", targetUserId = targetUserId, reason = reason))

    suspend fun unbanUser(targetUserId: String, reason: String? = null): Result<AdminUserDto> =
        userAction(AdminUserActionRequest(action = "unban", targetUserId = targetUserId, reason = reason))

    suspend fun setAutoApprove(targetUserId: String, value: Boolean): Result<AdminUserDto> =
        userAction(AdminUserActionRequest(action = "set_auto_approve", targetUserId = targetUserId, value = value))

    suspend fun resetStrikes(targetUserId: String): Result<AdminUserDto> =
        userAction(AdminUserActionRequest(action = "reset_strikes", targetUserId = targetUserId))

    private suspend fun userAction(request: AdminUserActionRequest): Result<AdminUserDto> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("AdminRepository", "👤 User action '${request.action}' on ${request.targetUserId}")
                val response = api.adminUserAction(request)
                if (response.success == true && response.data != null) {
                    Result.success(response.data)
                } else {
                    Log.e("AdminRepository", "❌ Failed action '${request.action}': ${response.error}")
                    Result.failure(Exception(ApiErrors.message(response)))
                }
            } catch (e: Exception) {
                Log.e("AdminRepository", "💥 Error on action '${request.action}'", e)
                Result.failure(Exception(ApiErrors.message(e), e))
            }
        }

    /**
     * Change a user's role. Returns the new role on success (the server
     * doesn't return the full user row for this endpoint).
     */
    suspend fun updateUserRole(targetUserId: String, newRole: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("AdminRepository", "🛡️ Changing role of $targetUserId -> $newRole")
                val response = api.updateUserRole(
                    UpdateUserRoleRequest(targetUserId = targetUserId, newRole = newRole)
                )
                if (response.success == true) {
                    Result.success(response.newRole ?: newRole)
                } else {
                    Log.e("AdminRepository", "❌ Failed to change role: ${response.error}")
                    Result.failure(Exception(ApiErrors.message(response.code, response.error)))
                }
            } catch (e: Exception) {
                Log.e("AdminRepository", "💥 Error changing role", e)
                Result.failure(Exception(ApiErrors.message(e), e))
            }
        }

    // ========================================
    // 📜 AUDIT LOG
    // ========================================

    /** @param category "all"|"security"|"deals"|"reports"|null (all) */
    suspend fun getAuditLog(
        category: String?,
        page: Int = 1
    ): Result<AdminPage<AdminAuditLogEntryDto>> = withContext(Dispatchers.IO) {
        try {
            Log.d("AdminRepository", "📜 Fetching audit log (category=$category, page=$page)")
            val response = api.adminAuditLog(
                AdminAuditLogRequest(category = category, page = page)
            )
            if (response.success == true && response.data != null) {
                Result.success(AdminPage(response.data, response.pagination))
            } else {
                Log.e("AdminRepository", "❌ Failed to fetch audit log: ${response.error}")
                Result.failure(Exception(ApiErrors.message(response)))
            }
        } catch (e: Exception) {
            Log.e("AdminRepository", "💥 Error fetching audit log", e)
            Result.failure(Exception(ApiErrors.message(e), e))
        }
    }
}
