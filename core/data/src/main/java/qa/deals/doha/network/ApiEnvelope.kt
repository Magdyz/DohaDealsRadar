package qa.deals.doha.network

/**
 * ========================================
 * ✅ UPDATED: Generic API envelope with pagination support
 * ========================================
 *
 * Updated: 2025-10-24
 * - Added pagination metadata for lazy loading
 * - All existing functionality preserved
 */
data class ApiEnvelope<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val data: T? = null,
    // ✅ NEW: Pagination metadata (optional, only for paginated endpoints)
    val pagination: PaginationMeta? = null
)

/**
 * ========================================
 * ✅ NEW: Pagination metadata
 * Returned by backend for paginated endpoints like get_deals
 * ========================================
 */
data class PaginationMeta(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int,
    val hasMore: Boolean
)