package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * ========================================
 * ✨ USERNAME REQUEST DTO
 * Used for username operations with backend
 * ========================================
 *
 * Actions:
 * - "get_username": Check if device has username
 * - "check_availability": Check if username is available
 * - "register_username": Register new username for device
 *
 * @param action The operation to perform
 * @param deviceId Device identifier (from Android)
 * @param username Username to check/register (optional depending on action)
 */
data class UsernameRequest(
    val action: String,
    @SerializedName("device_id") val deviceId: String? = null,
    val username: String? = null
)

/**
 * ========================================
 * ✨ USERNAME RESPONSE DTO
 * Response from username operations
 * ========================================
 *
 * @param success Whether operation succeeded
 * @param exists Whether device has a username (for get_username)
 * @param available Whether username is available (for check_availability)
 * @param username The username associated with device
 * @param error Error message if operation failed
 * @param data Additional user data (created_at, deal_count, etc.)
 */
data class UsernameResponse(
    val success: Boolean,
    val exists: Boolean? = null,
    val available: Boolean? = null,
    val username: String? = null,
    val error: String? = null,
    val data: UsernameData? = null
)

/**
 * ========================================
 * ✨ USERNAME DATA
 * Additional user identity information
 * ========================================
 *
 * @param username User's chosen username
 * @param createdAt When username was registered
 * @param dealCount Number of deals posted by user
 */
data class UsernameData(
    val username: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("deal_count") val dealCount: Int? = 0
)