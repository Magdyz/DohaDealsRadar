package qa.deals.doha.db

import androidx.room.ColumnInfo

import androidx.room.Entity

import androidx.room.PrimaryKey



@Entity(tableName = "users")

data class UserEntity(

    @PrimaryKey

    @ColumnInfo(name = "id")

    val id: String,



    @ColumnInfo(name = "email")

    val email: String,



    @ColumnInfo(name = "username")

    val username: String,



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

)

