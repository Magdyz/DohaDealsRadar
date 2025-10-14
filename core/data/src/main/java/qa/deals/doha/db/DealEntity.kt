package qa.deals.doha.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deals",
    indices = [
        Index(value = ["title"]),      // ✅ Speed up title search
        Index(value = ["createdAt"]),  // ✅ Speed up sorting by date
        Index(value = ["status"])      // ✅ Speed up filtering by status
    ]
)
data class DealEntity(
    @PrimaryKey val id: String,
    val title: String,
    val link: String,
    val imageUrl: String?,
    val status: String?,
    val createdAt: String?,
    val hotCount: Int?,
    val coldCount: Int?,
    val description: String? = null,
    val location: String? = null
)