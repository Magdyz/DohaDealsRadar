package qa.deals.doha.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
}
