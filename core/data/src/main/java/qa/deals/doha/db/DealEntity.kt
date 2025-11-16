package qa.deals.doha.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ========================================
 * ✨ DEAL ENTITY (Room Database)
 * Local cache of deals from Supabase
 * ========================================
 *
 * UPDATED: 2025-10-18
 * - Added category field for deal categorization
 * - Added postedBy field for username attribution
 *
 * ✅ UPDATED: 2025-10-23
 * - Added autoApproved field to match API response
 *
 * ✅ UPDATED: 2025-11-16
 * - Added originalPrice and discountedPrice fields for price display
 *
 * Indices for performance:
 * - title: Fast text search
 * - createdAt: Fast date sorting
 * - status: Fast filtering by status
 * - category: Fast category filtering
 */
@Entity(
    tableName = "deals",
    indices = [
        Index(value = ["title"]),
        Index(value = ["createdAt"]),
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["isArchived"]),  // ✅ SPRINT 1: Index for archive filtering
        Index(value = ["submittedByUserId"]),  // NEW: For user's deals lookup
        Index(value = ["approvedBy"]),          // NEW: For approval tracking
        Index(value = ["deletedAt"]),            // NEW: For filtering deleted deals
        Index(value = ["rejectedAt"])            // NEW: For filtering rejected deals
    ]
)

data class DealEntity(
    @PrimaryKey val id: String,
    val title: String,
    val link: String,
    val imageUrl: String?,
    val status: String?,
    val createdAt: String?,
    val expiresAt: String? = null,
    val hotCount: Int?,
    val coldCount: Int?,
    val description: String? = null,
    val location: String? = null,
    val category: String = "other",
    val postedBy: String = "Anonymous",  // ✨ NEW: Username attribution (default for old deals)
    val autoApproved: Boolean = false,
    val promoCode: String? = null,
    val originalPrice: Double? = null,        // ✨ NEW: Original price before discount (e.g., 2745.00)
    val discountedPrice: Double? = null,      // ✨ NEW: Discounted price (e.g., 1995.00)
    val isArchived: Boolean = false,
    val submittedByUserId: String? = null,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val reportCount: Int = 0,
    val deletedAt: String? = null,
    val deletedBy: String? = null,
    val deletionReason: String? = null,
    // ✅ NEW: Rejection fields (separate from deletion)
    val rejectionReason: String? = null,
    val rejectedAt: String? = null,
    val rejectedBy: String? = null

)