package qa.deals.doha.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import qa.deals.doha.network.UserDto

@Entity(tableName = "users")

data class UserEntity(



    @PrimaryKey

    @ColumnInfo(name = "id")

    val id: String,



    @ColumnInfo(name = "email")

    val email: String? = null,



    @ColumnInfo(name = "username")

    val username: String? = null,







    @ColumnInfo(name = "device_id")

    val deviceId: String? = null,



    @ColumnInfo(name = "email_verified")

    val emailVerified: Boolean = false,



    @ColumnInfo(name = "role")

    val role: String = "user", // user, moderator, admin



    @ColumnInfo(name = "auto_approve")

    val autoApprove: Boolean = false,



    @ColumnInfo(name = "approved_deals_count")

    val approvedDealsCount: Int = 0,



    @ColumnInfo(name = "created_at")

    val createdAt: String? = null,



    @ColumnInfo(name = "last_login_at")

    val lastLoginAt: String? = null

) {

    /**

     * Convert UserEntity to UserDto for UI layer

     */

    fun toDto(): UserDto {

        return UserDto(

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

