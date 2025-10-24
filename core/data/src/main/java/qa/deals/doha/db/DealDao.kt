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
}