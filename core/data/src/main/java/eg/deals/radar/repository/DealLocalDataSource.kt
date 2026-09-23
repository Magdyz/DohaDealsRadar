package eg.deals.radar.repository

import eg.deals.radar.db.DealDao
import eg.deals.radar.db.DealEntity
import kotlinx.coroutines.flow.Flow

class DealLocalDataSource(private val dao: DealDao) {
    fun getDeals(): Flow<List<DealEntity>> = dao.getAllDeals()
    suspend fun saveDeals(deals: List<DealEntity>) = dao.insertAll(deals)
    suspend fun clearDeals() = dao.clearAll()
}
