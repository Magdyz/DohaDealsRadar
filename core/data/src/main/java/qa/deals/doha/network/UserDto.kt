package qa.deals.doha.network

import com.google.gson.annotations.SerializedName
import qa.deals.doha.db.UserEntity

/**
 * User data transfer object for network responses
 * Maps to Supabase users table
 */
data class UserDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("device_id")
    val deviceId: String? = null,

    @SerializedName("email_verified")
    val emailVerified: Boolean = false,

    @SerializedName("role")
    val role: String = "user",

    @SerializedName("auto_approve")
    val autoApprove: Boolean = false,

    @SerializedName("approved_deals_count")
    val approvedDealsCount: Int = 0,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("last_login_at")
    val lastLoginAt: String? = null
) {
    /**
     * Convert UserDto to UserEntity for Room database
     */
    fun toEntity(): UserEntity {
        return UserEntity(
            id = id,
            email = email,
            username = username,
            deviceId = deviceId,
            emailVerified = emailVerified,
            role = role,
            autoApprove = autoApprove,
            approvedDealsCount = approvedDealsCount,
            createdAt = createdAt,
            lastLoginAt = lastLoginAt
        )
    }
}
