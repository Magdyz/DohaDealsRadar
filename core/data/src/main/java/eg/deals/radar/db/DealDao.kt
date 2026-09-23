package eg.deals.radar.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
    companion object {
        /** How many feed tabs (filter combinations) keep a cached list. */
        const val MAX_CACHED_FEEDS = 16
    }

    // ========================================
    // 🗂️ Feed tabs (each filter combination keeps its own cached list)
    // ========================================

    /** Deals of one feed tab, in the backend's order. */
    @Query(
        """SELECT d.* FROM feed_entries f INNER JOIN deals d ON d.id = f.dealId
           WHERE f.feedKey = :feedKey AND d.status = 'approved' AND d.isArchived = 0 AND d.deletedAt IS NULL
           ORDER BY f.position ASC"""
    )
    fun getFeed(feedKey: String): Flow<List<DealEntity>>

    @Query("SELECT * FROM feed_meta WHERE feedKey = :feedKey")
    suspend fun getFeedMeta(feedKey: String): FeedMetaEntity?

    /**
     * Page 1 of a tab: replace that tab's list in one transaction.
     * Removes only deals that no other tab (and no other screen's cache user) lists;
     * keeps at most [MAX_CACHED_FEEDS] tabs.
     */
    @Transaction
    suspend fun replaceFeed(feedKey: String, deals: List<DealEntity>, meta: FeedMetaEntity) {
        deleteDealsOnlyIn(feedKey)
        clearFeedEntries(feedKey)
        insertAll(deals)
        insertFeedEntries(deals.mapIndexed { i, d -> FeedEntryEntity(feedKey, d.id, i) })
        upsertFeedMeta(meta)
        for (old in getFeedKeysOldestFirst().dropLast(MAX_CACHED_FEEDS)) {
            deleteDealsOnlyIn(old)
            clearFeedEntries(old)
            deleteFeedMeta(old)
        }
    }

    /**
     * A prefetched page 1 (category bundle): written only if the tab has no newer data,
     * so it never overwrites what the user just loaded.
     */
    @Transaction
    suspend fun replaceFeedIfOlder(feedKey: String, deals: List<DealEntity>, meta: FeedMetaEntity) {
        val current = getFeedMeta(feedKey)
        if (current == null || current.updatedAt < meta.updatedAt) replaceFeed(feedKey, deals, meta)
    }

    /**
     * "Load more": add a page after the tab's current list. Skipped if the tab was
     * reloaded meanwhile (its cursor changed), so pages never mix. Returns true if written.
     */
    @Transaction
    suspend fun appendFeed(feedKey: String, usedCursor: String?, deals: List<DealEntity>, meta: FeedMetaEntity): Boolean {
        val current = getFeedMeta(feedKey) ?: return false
        if (current.nextCursor != usedCursor) return false
        val start = (getMaxFeedPosition(feedKey) ?: -1) + 1
        insertAll(deals)
        insertFeedEntries(deals.mapIndexed { i, d -> FeedEntryEntity(feedKey, d.id, start + i) })
        upsertFeedMeta(meta.copy(pagesLoaded = current.pagesLoaded + 1))
        return true
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedEntries(entries: List<FeedEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeedMeta(meta: FeedMetaEntity)

    @Query("DELETE FROM feed_entries WHERE feedKey = :feedKey")
    suspend fun clearFeedEntries(feedKey: String)

    @Query("DELETE FROM feed_meta WHERE feedKey = :feedKey")
    suspend fun deleteFeedMeta(feedKey: String)

    @Query("SELECT feedKey FROM feed_meta ORDER BY updatedAt ASC")
    suspend fun getFeedKeysOldestFirst(): List<String>

    @Query("SELECT MAX(position) FROM feed_entries WHERE feedKey = :feedKey")
    suspend fun getMaxFeedPosition(feedKey: String): Int?

    /** Deals listed by this tab and by no other tab (deals cached by other screens are never listed). */
    @Query(
        """DELETE FROM deals WHERE id IN (SELECT dealId FROM feed_entries WHERE feedKey = :feedKey)
           AND id NOT IN (SELECT dealId FROM feed_entries WHERE feedKey != :feedKey)"""
    )
    suspend fun deleteDealsOnlyIn(feedKey: String)

    @Query("UPDATE deals SET isArchived = 1 WHERE id = :dealId")
    suspend fun markArchived(dealId: String)


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

    // ========================================
    // ✅ NEW: Update a specific deal with fresh data (Big App voting fix)
    // OnConflictStrategy.REPLACE ensures we overwrite old counts with new ones
    // ========================================
    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateDeal(deal: DealEntity)

    // ========================================
    // ✅ NEW: Read a single deal by id (used to skip no-op writes/invalidations)
    // ========================================
    @Query("SELECT * FROM deals WHERE id = :id")
    suspend fun getDealById(id: String): DealEntity?

    // ========================================
    // ✅ NEW: Instant local vote count update (Zero-Lag UI)
    // Updates only the vote counts without touching the network
    // Used for immediate UI feedback before network sync
    // ========================================
    @Query("UPDATE deals SET hotCount = :hot, coldCount = :cold WHERE id = :dealId")
    suspend fun updateCounts(dealId: String, hot: Int, cold: Int)

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

    // Permanently delete a deal from database (admin only)
    @Query("DELETE FROM deals WHERE id = :dealId")
    suspend fun deleteDealById(dealId: String)


}
