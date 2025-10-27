package qa.deals.doha.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Stable

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
 * Indices for performance:
 * - title: Fast text search
 * - createdAt: Fast date sorting
 * - status: Fast filtering by status
 * - category: Fast category filtering
 */
@Stable
@Entity(
    tableName = "deals",
    indices = [
        Index(value = ["title"]),
        Index(value = ["createdAt"]),
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["isArchived"])  // ✅ SPRINT 1: Index for archive filtering

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
    val location: String? = null,
    val category: String = "other",
    val postedBy: String = "Anonymous",  // ✨ NEW: Username attribution (default for old deals)

    // ========================================
    // ✅ NEW: Added field for auto-approval
    // This field requires a Room Migration.
    // ========================================
    val autoApproved: Boolean = false,
    val promoCode: String? = null,
    // ========================================
    // SPRINT 1: Archive Feature
    // Field to track if deal is archived (auto-archived after 10 days)
    // Default: false (all existing deals remain active)
    // Migration: 8-9 adds this column with default false
    // ========================================
    val isArchived: Boolean = false

)