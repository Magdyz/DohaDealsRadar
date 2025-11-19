package qa.deals.doha.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * ========================================
 * ✅ UPDATED: DealDao with pagination support
 * ========================================
 *
 * Updated: 2025-10-23
 * - Added getDealsCount() for pagination tracking
 * - All existing functions preserved
 *
 * Updated: 2025-11-05 (Sprint 2)
 * - Added Sprint 2 methods (temporarily commented out)
 */
@Dao
interface DealDao {
    @Transaction
    suspend fun replaceAllDeals(deals: List<DealEntity>) {
        clearAll()
        insertAll(deals)
    }

    @Transaction
    suspend fun replaceArchivedDeals(deals: List<DealEntity>) {
        clearArchived()
        insertAll(deals)
    }

    @Query("SELECT * FROM deals ORDER BY createdAt DESC")
    fun getAllDeals(): Flow<List<DealEntity>>

    // ========================================
    // ✅ SPRINT 1: Get only ACTIVE deals (not archived)
    // Use this for the main feed to hide archived deals
    // ========================================
    @Query("SELECT * FROM deals WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getActiveDeals(): Flow<List<DealEntity>>

    // ========================================
    // ✅ SPRINT 1: Get only ARCHIVED deals
    // Use this for the archive screen
    // ========================================
    @Query("SELECT * FROM deals WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getArchivedDeals(): Flow<List<DealEntity>>

    // ========================================
    // ✅ NEW: Clear only ARCHIVED deals
    // Used for pull-to-refresh on archive screen
    // ========================================
    @Query("DELETE FROM deals WHERE isArchived = 1")
    suspend fun clearArchived()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deals: List<DealEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeal(deal: DealEntity)

    @Query("DELETE FROM deals")
    suspend fun clearAll()

    // ========================================
    // ✅ NEW: Get count of cached deals
    // Used for pagination tracking
    // ========================================
    @Query("SELECT COUNT(*) FROM deals")
    suspend fun getDealsCount(): Int

    @Query("SELECT COUNT(*) FROM deals WHERE isArchived = 0")
    suspend fun getActiveDealsCount(): Int

    @Query("SELECT COUNT(*) FROM deals WHERE isArchived = 1")
    suspend fun getArchivedDealsCount(): Int

    // ========================================
    // 🚧 SPRINT 2: NEW METHODS - TEMPORARILY COMMENTED OUT
    // ========================================
    // These methods reference new columns that don't exist yet in the old schema.
    // Room validates queries at compile-time, so we need to:
    // 1. Build with these commented out
    // 2. Run the app (migration 9→10 will create the columns)
    // 3. Uncomment these methods
    // 4. Rebuild successfully
    //
    // IMPORTANT: Column names use camelCase (Kotlin property names),
    // not snake_case (SQL column names)
    // ========================================


    // Get deals submitted by a specific user
    @Query("SELECT * FROM deals WHERE submittedByUserId = :userId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getDealsByUser(userId: String): Flow<List<DealEntity>>

    // Get pending deals (awaiting approval)
    @Query("SELECT * FROM deals WHERE status = 'pending' AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getPendingDeals(): Flow<List<DealEntity>>

    // Soft delete a deal (marks as deleted instead of removing from DB)
    @Query("UPDATE deals SET deletedAt = :deletedAt, deletedBy = :deletedBy, deletionReason = :reason WHERE id = :dealId")
    suspend fun softDeleteDeal(dealId: String, deletedAt: String, deletedBy: String?, reason: String?)

    // Approve a deal and record who approved it
    @Query("UPDATE deals SET status = 'approved', approvedBy = :approvedBy, approvedAt = :approvedAt WHERE id = :dealId")
    suspend fun approveDeal(dealId: String, approvedBy: String?, approvedAt: String)

    // Get approved, active, non-deleted deals (most restrictive filter)
    @Query("SELECT * FROM deals WHERE status = 'approved' AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getApprovedActiveDeals(): Flow<List<DealEntity>>

    // Permanently delete a deal from database (admin only)
    @Query("DELETE FROM deals WHERE id = :dealId")
    suspend fun deleteDealById(dealId: String)


}
