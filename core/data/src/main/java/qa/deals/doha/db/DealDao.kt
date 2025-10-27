package qa.deals.doha.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * ========================================
 * ✅ UPDATED: DealDao with pagination support
 * ========================================
 *
 * Updated: 2025-10-23
 * - Added getDealsCount() for pagination tracking
 * - All existing functions preserved
 */
@Dao
interface DealDao {

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

    // ========================================
    // ✅ SPRINT 1: Get count of active deals (not archived)
    // Used for pagination tracking in main feed
    // ========================================
    @Query("SELECT COUNT(*) FROM deals WHERE isArchived = 0")
    suspend fun getActiveDealsCount(): Int

    // ========================================
    // ✅ SPRINT 1: Get count of archived deals
    // Used for pagination tracking in archive screen
    // ========================================
    @Query("SELECT COUNT(*) FROM deals WHERE isArchived = 1")
    suspend fun getArchivedDealsCount(): Int

}